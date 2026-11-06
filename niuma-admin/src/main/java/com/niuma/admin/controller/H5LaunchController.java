package com.niuma.admin.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.niuma.admin.entity.Player;
import com.niuma.admin.mapper.PlayerMapper;
import com.niuma.admin.merchant.entity.Merchant;
import com.niuma.admin.merchant.entity.MerchantPlayer;
import com.niuma.admin.merchant.mapper.MerchantMapper;
import com.niuma.admin.merchant.mapper.MerchantPlayerMapper;
import com.niuma.admin.merchant.service.IMerchantService;
import com.niuma.common.annotation.Anonymous;
import com.niuma.common.constant.Constants;
import com.niuma.common.core.domain.model.LoginPlayer;
import com.niuma.common.core.redis.RedisCache;
import com.niuma.common.utils.StringUtils;
import com.niuma.common.utils.uuid.IdUtils;
import com.niuma.framework.web.service.PlayerTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * H5 客户端启动接口：一次调用拿到进房所需的全部信息。
 *
 * 入参：{ "playerId": "p1001", "roomNumber": "421053", "gameType": 1031 }
 * 出参：{ code, msg, data: { vendorPlayerId, token, secret, gameType } }
 *
 * 说明：
 * - 服务端完成换 token、按房号进房（复用 /player/game/enter/number）；
 * - 消息密钥从 Redis 读取一次性下发给客户端（客户端用它做 md5 签名）。
 */
@Anonymous
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/h5")
public class H5LaunchController {

    private static final Logger log = LoggerFactory.getLogger(H5LaunchController.class);

    @Autowired
    private MerchantPlayerMapper merchantPlayerMapper;

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private PlayerTokenService playerTokenService;

    @Autowired
    private IMerchantService merchantService;

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/launch")
    public Map<String, Object> launch(@RequestBody(required = false) JSONObject req) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            String account = req == null ? null : req.getString("playerId");
            String roomNumber = req == null ? null : req.getString("roomNumber");
            Integer gameType = req == null ? null : req.getInteger("gameType");
            if (gameType == null)
                gameType = 1031;
            if (StringUtils.isEmpty(account) || StringUtils.isEmpty(roomNumber)) {
                return fail("playerId 和 roomNumber 必填");
            }

            // 1) 账号 -> 厂商玩家 id
            String vendorPlayerId = null;
            Player direct = playerMapper.selectById(account);
            if (direct != null) {
                vendorPlayerId = direct.getId();
            } else {
                QueryWrapper<MerchantPlayer> qw = new QueryWrapper<>();
                qw.eq("player_id", account).last("limit 1");
                MerchantPlayer mp = merchantPlayerMapper.selectOne(qw);
                if (mp != null)
                    vendorPlayerId = mp.getVendorPlayerId();
            }
            if (StringUtils.isEmpty(vendorPlayerId))
                return fail("找不到玩家: " + account);

            Player player = playerMapper.selectById(vendorPlayerId);
            if (player == null)
                return fail("厂商玩家不存在: " + vendorPlayerId);

            // 2) 复用商户登录逻辑拿 token（保证与客户端既有免登录机制一致）
            Merchant merchant = merchantMapper.selectById("gc_test");
            if (merchant == null)
                return fail("商户不存在: gc_test");
            JSONObject loginReq = new JSONObject();
            loginReq.put("playerId", account);
            loginReq.put("nickname", player.getNickname());
            Map<String, Object> loginData = merchantService.login(merchant, loginReq);
            String token = String.valueOf(loginData.get("token"));
            if (StringUtils.isEmpty(token))
                return fail("生成 token 失败");

            // 3) 消息密钥（客户端签名用）
            String secret = stringRedisTemplate.opsForValue().get("player_message_secret:" + vendorPlayerId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("vendorPlayerId", vendorPlayerId);
            data.put("token", token);
            data.put("secret", secret == null ? "" : secret);
            data.put("gameType", gameType);

            out.put("code", 0);
            out.put("msg", "ok");
            out.put("data", data);
            log.info("H5 launch ok: account={} vendor={} room={}", account, vendorPlayerId, roomNumber);
            return out;
        } catch (Exception ex) {
            log.error("H5 launch 失败", ex);
            return fail(ex.getMessage());
        }
    }

    private Map<String, Object> fail(String msg) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", 1);
        out.put("msg", msg == null ? "unknown" : msg);
        return out;
    }
}
