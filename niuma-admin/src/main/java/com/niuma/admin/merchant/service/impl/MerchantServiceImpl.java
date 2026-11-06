package com.niuma.admin.merchant.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.niuma.admin.entity.Capital;
import com.niuma.admin.entity.Player;
import com.niuma.admin.mapper.CapitalMapper;
import com.niuma.admin.mapper.PlayerMapper;
import com.niuma.admin.merchant.MerchantException;
import com.niuma.admin.merchant.entity.Merchant;
import com.niuma.admin.merchant.entity.MerchantPlayer;
import com.niuma.admin.merchant.entity.MerchantTxn;
import com.niuma.admin.merchant.mapper.MerchantAccountMapper;
import com.niuma.admin.merchant.mapper.MerchantCapitalMapper;
import com.niuma.admin.merchant.mapper.MerchantMapper;
import com.niuma.admin.merchant.mapper.MerchantPlayerMapper;
import com.niuma.admin.merchant.mapper.MerchantRoundMapper;
import com.niuma.admin.merchant.mapper.MerchantTxnMapper;
import com.niuma.admin.merchant.service.IMerchantService;
import com.niuma.admin.merchant.util.MerchantSignUtils;
import com.niuma.admin.constant.NiuMaRedisKeys;
import com.niuma.common.constant.Constants;
import com.niuma.common.core.domain.model.LoginPlayer;
import com.niuma.common.core.redis.RedisCache;
import com.niuma.common.utils.AesUtil;
import com.niuma.common.utils.CommonUtils;
import com.niuma.common.utils.StringUtils;
import com.niuma.common.utils.uuid.IdUtils;
import com.niuma.framework.web.service.PlayerTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聚合商户接入实现（转账钱包）
 *
 * 设计要点：
 * 1. 只新增表，自营玩家的 player/capital 逻辑完全复用，不改变原有语义；
 * 2. 转账用单条原子 SQL，避免读改写竞态；
 * 3. 所有写操作以 (merchantId, txnId) 幂等；
 * 4. 商户玩家初始金币为 0（额度只能由平台转入），与自营注册发放 1000000 金币区分开。
 */
@Service
public class MerchantServiceImpl implements IMerchantService {

    private static final Logger log = LoggerFactory.getLogger(MerchantServiceImpl.class);

    /** 时间窗 ±5 分钟 */
    private static final long TS_WINDOW_SECONDS = 300L;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${niuma.merchant.enabled:false}")
    private boolean enabled;

    @Value("${niuma.merchant.launch-base-url:}")
    private String defaultLaunchBaseUrl;

    @Value("${niuma.merchant.ip-whitelist-check:true}")
    private boolean ipWhitelistCheck;

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private MerchantPlayerMapper merchantPlayerMapper;

    @Autowired
    private MerchantTxnMapper merchantTxnMapper;

    @Autowired
    private MerchantCapitalMapper merchantCapitalMapper;

    @Autowired
    private MerchantRoundMapper merchantRoundMapper;

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private MerchantAccountMapper merchantAccountMapper;

    @Autowired
    private CapitalMapper capitalMapper;

    @Autowired
    private PlayerTokenService playerTokenService;

    @Autowired
    private RedisCache redisCache;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ==========================================================
    // 身份校验
    // ==========================================================

    @Override
    public Merchant verify(String merchantId, String timestamp, String nonce, String rawBody, String clientIp) {
        if (!enabled) {
            throw MerchantException.merchantInvalid();
        }
        if (StringUtils.isEmpty(merchantId) || StringUtils.isEmpty(timestamp) || StringUtils.isEmpty(nonce)) {
            throw MerchantException.badSign();
        }
        // 时间窗
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw MerchantException.badSign();
        }
        long now = System.currentTimeMillis() / 1000L;
        if (Math.abs(now - ts) > TS_WINDOW_SECONDS) {
            throw MerchantException.badSign();
        }

        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null || merchant.getEnabled() == null || merchant.getEnabled() != 1) {
            throw MerchantException.merchantInvalid();
        }
        // IP 白名单
        if (ipWhitelistCheck && StringUtils.isNotEmpty(merchant.getIpWhitelist())) {
            boolean hit = false;
            for (String ip : merchant.getIpWhitelist().split(",")) {
                if (ip.trim().equals(clientIp)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                log.warn("merchant {} ip {} not in whitelist", merchantId, clientIp);
                throw MerchantException.badSign();
            }
        }
        return merchant;
    }

    /**
     * 校验签名（控制器在解析 body 前调用）
     */
    public boolean checkSign(Merchant merchant, String timestamp, String nonce, String rawBody, String sign) {
        return signMatches(merchant.getSecretKey(), merchant, timestamp, nonce, rawBody, sign)
                || signMatches(merchant.getSecretKeyPrev(), merchant, timestamp, nonce, rawBody, sign);
    }

    private boolean signMatches(String secret, Merchant merchant, String timestamp, String nonce, String rawBody, String sign) {
        if (StringUtils.isEmpty(secret) || StringUtils.isEmpty(sign)) {
            return false;
        }
        String expect = MerchantSignUtils.sign(merchant.getId(), timestamp, nonce, rawBody, secret);
        return MerchantSignUtils.safeEquals(expect, sign);
    }

    // ==========================================================
    // 登录
    // ==========================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> login(Merchant merchant, JSONObject req) {
        String platformPlayerId = required(req, "playerId");
        String nickname = req.getString("nickname");

        MerchantPlayer mp = getMerchantPlayer(merchant.getId(), platformPlayerId);
        String rawPassword = null;
        if (mp == null) {
            // 首次：创建 NiuMa 玩家（金币 0，额度只由平台转入）
            rawPassword = CommonUtils.generatePassword(8);
            Player player = new Player();
            player.setId(generatePlayerId());
            player.setName(buildUserName(merchant.getId(), platformPlayerId));
            player.setPassword(passwordEncoder.encode(rawPassword));
            player.setNickname(StringUtils.isNotEmpty(nickname) ? nickname : player.getName());
            player.setSex(0);
            Integer count = playerMapper.countHeadImageUrls();
            if (count != null && count > 0) {
                player.setAvatar(playerMapper.getHeadImageUrl(1 + (int) (Math.random() * count)));
            }
            playerMapper.addPlayer(player);

            Capital capital = new Capital();
            capital.setPlayerId(player.getId());
            capital.setGold(0L);
            capital.setDeposit(0L);
            capital.setDiamond(0L);
            capital.setVersion(1L);
            capitalMapper.insert(capital);

            mp = new MerchantPlayer();
            mp.setMerchantId(merchant.getId());
            mp.setPlayerId(platformPlayerId);
            mp.setVendorPlayerId(player.getId());
            mp.setNickname(player.getNickname());
            try {
                merchantPlayerMapper.insert(mp);
            } catch (DuplicateKeyException e) {
                // 并发登录：读回已存在的映射
                mp = getMerchantPlayer(merchant.getId(), platformPlayerId);
                if (mp == null) {
                    throw e;
                }
            }
        } else {
            rawPassword = CommonUtils.generatePassword(8);
            // 重置密码，避免历史密码泄露；只影响商户玩家。
            // 用参数化 UPDATE，避免实体更新触发后台用户的自动填充（商户接口是匿名调用）
            merchantAccountMapper.updatePassword(mp.getVendorPlayerId(), passwordEncoder.encode(rawPassword));
        }

        Player vendorPlayer = playerMapper.selectById(mp.getVendorPlayerId());
        if (vendorPlayer == null) {
            throw MerchantException.playerNotFound();
        }

        // 生成登录态（与自营同一套 token 机制）
        LoginPlayer loginPlayer = new LoginPlayer();
        loginPlayer.setId(vendorPlayer.getId());
        loginPlayer.setName(vendorPlayer.getName());
        loginPlayer.setNickName(vendorPlayer.getNickname());
        loginPlayer.setUuid(IdUtils.fastSimpleUUID());
        playerTokenService.setLoginPlayer(loginPlayer);
        String token = Constants.TOKEN_PREFIX + playerTokenService.createToken(loginPlayer);

        Long balance = merchantCapitalMapper.getGold(vendorPlayer.getId());

        // userData：AES(JSON{username,password,token})，与客户端既有免登录机制一致
        JSONObject ud = new JSONObject();
        ud.put("username", vendorPlayer.getName());
        ud.put("password", rawPassword);
        ud.put("token", token);
        String userData = AesUtil.encrypt(ud.toJSONString());

        String baseUrl = StringUtils.isNotEmpty(merchant.getLaunchBaseUrl())
                ? merchant.getLaunchBaseUrl() : defaultLaunchBaseUrl;
        String launchUrl = buildLaunchUrl(baseUrl, merchant.getLaunchParam(), userData, token, req.getString("gameCode"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("vendorPlayerId", vendorPlayer.getId());
        data.put("balance", balance == null ? 0L : balance);
        data.put("token", token);
        data.put("userData", userData);
        data.put("launchUrl", launchUrl);
        data.put("expireAt", System.currentTimeMillis() / 1000L + 600L);
        return data;
    }

    // ==========================================================
    // 转账
    // ==========================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> transfer(Merchant merchant, JSONObject req) {
        String txnId = required(req, "txnId");
        String platformPlayerId = required(req, "playerId");
        String type = required(req, "type").toUpperCase();
        Long amount = req.getLong("amount");
        String remark = req.getString("reason");

        if (amount == null || amount <= 0L) {
            throw MerchantException.badRequest("amount must be > 0");
        }
        if (!"IN".equals(type) && !"OUT".equals(type)) {
            throw MerchantException.badRequest("type must be IN or OUT");
        }

        String requestHash = MerchantSignUtils.sha256Hex(type + "|" + platformPlayerId + "|" + amount + "|" + (remark == null ? "" : remark));

        // 幂等：已处理过
        MerchantTxn exists = getTxn(merchant.getId(), txnId);
        if (exists != null) {
            if (!requestHash.equals(exists.getRequestHash())) {
                throw MerchantException.txnConflict();
            }
            Map<String, Object> replay = new LinkedHashMap<>();
            replay.put("txnId", txnId);
            replay.put("balance", exists.getBalanceAfter());
            replay.put("replayed", true);
            return replay;
        }

        MerchantPlayer mp = getMerchantPlayer(merchant.getId(), platformPlayerId);
        if (mp == null) {
            throw MerchantException.playerNotFound();
        }

        // 转出前：玩家不能在场馆内（否则会边玩边扣）
        if ("OUT".equals(type)) {
            Object venue = redisCache.getCacheObject(NiuMaRedisKeys.PLAYER_CURRENT_VENUE + mp.getVendorPlayerId());
            if (venue != null && StringUtils.isNotEmpty(String.valueOf(venue))) {
                throw MerchantException.inGame();
            }
        }

        long delta = "IN".equals(type) ? amount : -amount;
        int rows = merchantCapitalMapper.changeGold(mp.getVendorPlayerId(), delta);
        if (rows <= 0) {
            throw MerchantException.insufficient();
        }
        Long balance = merchantCapitalMapper.getGold(mp.getVendorPlayerId());

        MerchantTxn txn = new MerchantTxn();
        txn.setMerchantId(merchant.getId());
        txn.setTxnId(txnId);
        txn.setPlayerId(platformPlayerId);
        txn.setType(type);
        txn.setAmount(amount);
        txn.setBalanceAfter(balance == null ? 0L : balance);
        txn.setStatus("SUCCESS");
        txn.setRequestHash(requestHash);
        txn.setRemark(remark);
        txn.setCreateTime(LocalDateTime.now());
        try {
            merchantTxnMapper.insert(txn);
        } catch (DuplicateKeyException e) {
            // 并发重复：以库里已有的为准
            MerchantTxn again = getTxn(merchant.getId(), txnId);
            if (again != null && requestHash.equals(again.getRequestHash())) {
                Map<String, Object> replay = new LinkedHashMap<>();
                replay.put("txnId", txnId);
                replay.put("balance", again.getBalanceAfter());
                replay.put("replayed", true);
                return replay;
            }
            throw MerchantException.txnConflict();
        }

        log.info("merchant transfer, merchant={}, txn={}, type={}, amount={}, player={}, balance={}",
                merchant.getId(), txnId, type, amount, platformPlayerId, balance);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("txnId", txnId);
        data.put("balance", txn.getBalanceAfter());
        data.put("replayed", false);
        return data;
    }

    // ==========================================================
    // 余额 / 下线 / 注单
    // ==========================================================

    @Override
    public Map<String, Object> balance(Merchant merchant, JSONObject req) {
        String platformPlayerId = required(req, "playerId");
        MerchantPlayer mp = getMerchantPlayer(merchant.getId(), platformPlayerId);
        if (mp == null) {
            throw MerchantException.playerNotFound();
        }
        Long gold = merchantCapitalMapper.getGold(mp.getVendorPlayerId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("balance", gold == null ? 0L : gold);
        data.put("vendorPlayerId", mp.getVendorPlayerId());
        return data;
    }

    @Override
    public Map<String, Object> kick(Merchant merchant, JSONObject req) {
        String platformPlayerId = required(req, "playerId");
        MerchantPlayer mp = getMerchantPlayer(merchant.getId(), platformPlayerId);
        if (mp == null) {
            throw MerchantException.playerNotFound();
        }
        // v1：撤销登录态（强制重新登录）；真正把玩家踢出牌桌需要游戏服配合，放到 v1.1
        playerTokenService.delLoginPlayer(mp.getVendorPlayerId());
        log.info("merchant kick, merchant={}, player={}, vendorPlayer={}", merchant.getId(), platformPlayerId, mp.getVendorPlayerId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kicked", true);
        return data;
    }

    @Override
    public Map<String, Object> rounds(Merchant merchant, JSONObject req) {
        String platformPlayerId = required(req, "playerId");
        MerchantPlayer mp = getMerchantPlayer(merchant.getId(), platformPlayerId);
        if (mp == null) {
            throw MerchantException.playerNotFound();
        }
        int page = req.getIntValue("page", 1);
        int size = req.getIntValue("size", 50);
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 200) {
            size = 50;
        }
        String from = normalizeTime(req.getString("from"));
        String to = normalizeTime(req.getString("to"));

        int total = merchantRoundMapper.countMahjongRounds(mp.getVendorPlayerId(), from, to);
        List<Map<String, Object>> list = merchantRoundMapper.pageMahjongRounds(
                mp.getVendorPlayerId(), from, to, (page - 1) * size, size);

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> row : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("roundId", row.get("roundId"));
            item.put("venueId", row.get("venueId"));
            item.put("roundNo", row.get("roundNo"));
            item.put("seat", row.get("seat"));
            item.put("winGold", row.get("winGold"));
            item.put("gameCode", "mahjong");
            item.put("time", row.get("time") == null ? null : String.valueOf(row.get("time")));
            normalized.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("list", normalized);
        return data;
    }

    // ==========================================================
    // 内部工具
    // ==========================================================

    private MerchantPlayer getMerchantPlayer(String merchantId, String platformPlayerId) {
        return merchantPlayerMapper.selectOne(Wrappers.<MerchantPlayer>lambdaQuery()
                .eq(MerchantPlayer::getMerchantId, merchantId)
                .eq(MerchantPlayer::getPlayerId, platformPlayerId)
                .last("limit 1"));
    }

    private MerchantTxn getTxn(String merchantId, String txnId) {
        return merchantTxnMapper.selectOne(Wrappers.<MerchantTxn>lambdaQuery()
                .eq(MerchantTxn::getMerchantId, merchantId)
                .eq(MerchantTxn::getTxnId, txnId)
                .last("limit 1"));
    }

    private String buildUserName(String merchantId, String platformPlayerId) {
        String name = "m" + merchantId + "_" + platformPlayerId;
        return name.length() > 45 ? name.substring(0, 45) : name;
    }

    private String generatePlayerId() {
        for (int i = 0; i < 5; i++) {
            String id = IdUtils.fastSimpleUUID().substring(0, 10);
            if (playerMapper.selectById(id) == null) {
                return id;
            }
        }
        throw new IllegalStateException("generate player id failed");
    }

    private String buildLaunchUrl(String baseUrl, String param, String userData, String token, String gameCode) {
        if (StringUtils.isEmpty(baseUrl)) {
            return "";
        }
        StringBuilder sb = new StringBuilder(baseUrl);
        sb.append(baseUrl.contains("?") ? "&" : "?");
        sb.append("mode=merchant");
        if ("token".equalsIgnoreCase(param)) {
            sb.append("&token=").append(urlEncode(token));
        } else {
            sb.append("&userData=").append(urlEncode(userData));
        }
        if (StringUtils.isNotEmpty(gameCode)) {
            sb.append("&gameCode=").append(urlEncode(gameCode));
        }
        return sb.toString();
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private String required(JSONObject req, String key) {
        String v = req.getString(key);
        if (StringUtils.isEmpty(v)) {
            throw MerchantException.badRequest("missing " + key);
        }
        return v;
    }

    private String normalizeTime(String s) {
        if (StringUtils.isEmpty(s)) {
            return null;
        }
        if (s.length() == 10) {
            return s + " 00:00:00";
        }
        // 兼容 ISO 格式
        return s.replace("T", " ").replace("Z", "");
    }

    /** 供控制器回包使用 */
    public static Map<String, Object> ok(Object data) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 0);
        resp.put("data", data);
        return resp;
    }

    public static Map<String, Object> fail(int code, String msg) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", code);
        resp.put("msg", msg);
        return resp;
    }

    /** 便于测试：把 JSON 串转对象 */
    public static JSONObject parse(String raw) {
        return JSON.parseObject(raw);
    }
}
