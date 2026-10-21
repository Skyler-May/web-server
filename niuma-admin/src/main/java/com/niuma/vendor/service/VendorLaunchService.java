package com.niuma.vendor.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.niuma.admin.entity.Capital;
import com.niuma.admin.entity.Player;
import com.niuma.admin.mapper.PlayerMapper;
import com.niuma.admin.service.ICapitalService;
import com.niuma.common.constant.Constants;
import com.niuma.common.core.domain.model.LoginPlayer;
import com.niuma.common.exception.http.InternalServerException;
import com.niuma.common.utils.CommonUtils;
import com.niuma.common.utils.StringUtils;
import com.niuma.common.utils.uuid.IdUtils;
import com.niuma.common.constant.ResultCodeEnum;
import com.niuma.framework.web.service.PlayerTokenService;
import com.niuma.vendor.dto.VendorLaunchRequest;
import com.niuma.vendor.dto.VendorLaunchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

/**
 * 厂商启动游戏服务（标准厂商 API 的 launch 实现）
 *
 * <p>流程：校验游戏 → 确保玩家存在（可自动创建）→ 签发 NiuMa 令牌 → 返回直达参数。
 *
 * <p>关键设计：
 * <ul>
 *   <li><b>不经过 NiuMa 大厅</b>：返回的 token 可直接用于该游戏的进入流程，
 *       聚合平台客户端拿 token + gameType 进入该游戏的选场次页</li>
 *   <li><b>复用既有令牌体系</b>：走 {@link PlayerTokenService}，与原有登录签发的 token
 *       完全等价，因此后续所有玩家接口（进房、查记录、筹码）无需任何改动</li>
 *   <li><b>复用既有玩家创建流程</b>：ID 生成、默认头像、资产初始化均与
 *       {@code PlayerServiceImpl#register} 保持一致，避免出现"有账号无资产"的脏数据</li>
 *   <li><b>不做 AES 解密、不校验验证码</b>：厂商模式下由聚合平台负责用户认证</li>
 * </ul>
 */
@Service
public class VendorLaunchService {

    private static final Logger log = LoggerFactory.getLogger(VendorLaunchService.class);

    /**
     * 新建玩家的初始金币
     *
     * ⚠️ 生产必须为 0：厂商模式下玩家资金一律由聚合平台从中心钱包转入，
     * 若此处赠送金币，等于平台白送钱给玩家，属重大资金隐患。
     * 仅在本地联调时可临时调高以便测试。
     */
    private static final long INITIAL_GOLD = 0L;

    /** 新建玩家的初始钻石（同上，生产必须为 0） */
    private static final long INITIAL_DIAMOND = 0L;

    @Autowired
    private VendorCatalogService vendorCatalogService;

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private PlayerTokenService playerTokenService;

    @Autowired
    private ICapitalService capitalService;

    /**
     * 启动游戏
     *
     * @param appId   接入方标识（已通过签名校验）
     * @param request 启动请求
     * @return 启动结果（含 token、gameType、WS 地址）
     */
    @Transactional(rollbackFor = Exception.class)
    public VendorLaunchResponse launch(String appId, VendorLaunchRequest request) {
        // 1) 校验游戏
        VendorCatalogService.GameItem game = vendorCatalogService.findByCode(request.getGameCode());
        if (game == null) {
            throw new IllegalArgumentException("unknown gameCode: " + request.getGameCode());
        }

        // 2) 解析/创建玩家
        String playerId = resolveOrCreatePlayer(appId, request.getPlayerId(), request.getNickname());

        // 3) 签发令牌（与原有登录流程同源，token 可直接用于既有全部玩家接口）
        LoginPlayer loginPlayer = new LoginPlayer();
        loginPlayer.setId(playerId);
        loginPlayer.setName(request.getPlayerId());
        loginPlayer.setNickName(request.getNickname());
        loginPlayer.setUuid(IdUtils.fastSimpleUUID());
        this.playerTokenService.setLoginPlayer(loginPlayer);
        String token = Constants.TOKEN_PREFIX + this.playerTokenService.createToken(loginPlayer);

        // 4) 组装响应
        VendorLaunchResponse response = new VendorLaunchResponse();
        response.setToken(token);
        response.setExternalPlayerId(request.getPlayerId());
        response.setPlayerId(playerId);
        response.setGameCode(game.getGameCode());
        response.setGameType(game.getGameType());
        response.setResourceKey(game.getResourceKey());
        response.setWsUrl(request.getWsBaseUrl());

        log.info("vendor launch: appId={}, externalPlayerId={}, playerId={}, game={}({})",
                appId, request.getPlayerId(), playerId, game.getGameCode(), game.getGameType());
        return response;
    }

    /**
     * 确保玩家存在；不存在则按外部平台 ID 创建
     *
     * <p>玩家 ID 规则：外部 playerId 直接作为 NiuMa 账号名（name），
     * 避免引入映射表——聚合平台侧保证 playerId 全局唯一即可。
     *
     * @return NiuMa 内部玩家 ID
     */
    private String resolveOrCreatePlayer(String appId, String externalPlayerId, String nickname) {
        String existing = this.playerMapper.getIdByName(externalPlayerId);
        if (StringUtils.isNotEmpty(existing)) {
            return existing;
        }

        // ---- 自动创建：与 PlayerServiceImpl#register 流程保持一致 ----
        Player player = new Player();
        player.setId(generatePlayerId());
        player.setName(externalPlayerId);
        // 厂商模式不设密码：聚合平台负责认证，NiuMa 密码登录入口对该账号不可用
        player.setNickname(StringUtils.isEmpty(nickname) ? externalPlayerId : nickname);
        player.setSex(0);
        player.setDelFlag(0);
        player.setBanned(0);
        player.setAvatar(randomAvatar());
        this.playerMapper.addPlayer(player);

        // 初始化资产（缺此步会导致进入游戏后无法下注）
        Capital capital = new Capital();
        capital.setPlayerId(player.getId());
        capital.setGold(INITIAL_GOLD);
        capital.setDiamond(INITIAL_DIAMOND);
        capital.setVersion(1L);
        this.capitalService.save(capital);

        log.info("vendor auto-created player: appId={}, name={}, id={}", appId, externalPlayerId, player.getId());
        return player.getId();
    }

    /**
     * 生成不重复的玩家 ID（与 NiuMa 原流程同一算法，10 位随机码）
     */
    private String generatePlayerId() {
        String playerId = CommonUtils.generateRandomCode(10, CommonUtils.CODE_ALL,
                new DuplicateTesterImpl(this.playerMapper));
        if (StringUtils.isEmpty(playerId)) {
            throw new InternalServerException(ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "Generate player id failed");
        }
        return playerId;
    }

    /**
     * 随机分配头像（与 NiuMa 原流程一致）
     */
    private String randomAvatar() {
        try {
            Integer count = this.playerMapper.countHeadImageUrls();
            if (count == null || count <= 0) {
                return null;
            }
            int imgId = new Random().nextInt(count) + 1;
            return this.playerMapper.getHeadImageUrl(imgId);
        } catch (Exception e) {
            // 头像缺失不应阻断建号
            log.warn("vendor randomAvatar failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 玩家 ID 查重（供 CommonUtils.generateRandomCode 使用）
     */
    private static class DuplicateTesterImpl implements CommonUtils.DuplicateTester {

        private final PlayerMapper mapper;

        DuplicateTesterImpl(PlayerMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public boolean testDuplicate(String code) {
            Integer count = this.mapper.selectCount(
                    Wrappers.<Player>lambdaQuery().eq(Player::getId, code));
            return CommonUtils.predicate(count);
        }
    }
}
