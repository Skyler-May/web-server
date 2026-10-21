package com.niuma.vendor.controller;

import com.niuma.common.core.domain.AjaxResult;
import com.niuma.vendor.config.VendorProperties;
import com.niuma.vendor.dto.VendorLaunchRequest;
import com.niuma.vendor.service.VendorCatalogService;
import com.niuma.vendor.service.VendorLaunchService;
import com.niuma.vendor.util.VendorSignUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 标准厂商 API（对聚合平台开放）
 *
 * <p>仅在 {@code niuma.vendor.enabled=true} 时可用；关闭时统一返回 404，
 * 完全不影响 NiuMa 原有的独立运行能力。
 *
 * <h3>认证方式</h3>
 * 所有接口需携带三个请求头：
 * <ul>
 *   <li>{@code X-Vendor-AppId} —— 接入方标识</li>
 *   <li>{@code X-Vendor-Timestamp} —— Unix 时间戳（秒）</li>
 *   <li>{@code X-Vendor-Sign} —— HMAC-SHA256 签名</li>
 * </ul>
 * 签名串：{@code appId \n timestamp \n nonce \n body}，其中 nonce 复用
 * {@code X-Vendor-Nonce} 头。任何一项不通过即拒绝，<b>禁止静默放行</b>。
 *
 * @author vendor-integration
 */
@RestController
@RequestMapping("${niuma.vendor.api-prefix:/vendor}")
public class VendorApiController {

    private static final Logger log = LoggerFactory.getLogger(VendorApiController.class);

    private static final String HEADER_APP_ID = "X-Vendor-AppId";
    private static final String HEADER_TIMESTAMP = "X-Vendor-Timestamp";
    private static final String HEADER_NONCE = "X-Vendor-Nonce";
    private static final String HEADER_SIGN = "X-Vendor-Sign";

    @Autowired
    private VendorProperties vendorProperties;

    @Autowired
    private VendorSignUtil vendorSignUtil;

    @Autowired
    private VendorCatalogService vendorCatalogService;

    @Autowired
    private VendorLaunchService vendorLaunchService;

    /**
     * 游戏目录（平铺）
     *
     * <p>聚合平台据此在自家大厅平铺显示全部棋牌游戏；点某一款后调用 /launch 进入。
     */
    @GetMapping("/catalog")
    public AjaxResult catalog(@RequestHeader(value = HEADER_APP_ID, required = false) String appId,
                              @RequestHeader(value = HEADER_TIMESTAMP, required = false) String timestamp,
                              @RequestHeader(value = HEADER_NONCE, required = false) String nonce,
                              @RequestHeader(value = HEADER_SIGN, required = false) String sign) {
        AjaxResult denied = authenticate(appId, timestamp, nonce, "", sign);
        if (denied != null) {
            return denied;
        }
        List<Map<String, Object>> games = new ArrayList<>();
        for (VendorCatalogService.GameItem item : vendorCatalogService.listGames()) {
            Map<String, Object> m = new HashMap<>(8);
            m.put("gameCode", item.getGameCode());
            m.put("gameType", item.getGameType());
            m.put("name", item.getName());
            m.put("resourceKey", item.getResourceKey());
            m.put("sort", item.getSort());
            games.add(m);
        }
        AjaxResult result = AjaxResult.success();
        result.put("games", games);
        result.put("total", games.size());
        return result;
    }

    /**
     * 启动游戏（核心接口）
     *
     * <p>返回 token + gameType，客户端凭此直达该游戏的选场次页，<b>不经过 NiuMa 大厅</b>。
     */
    @PostMapping("/launch")
    public AjaxResult launch(@RequestBody VendorLaunchRequest request,
                             @RequestHeader(value = HEADER_APP_ID, required = false) String appId,
                             @RequestHeader(value = HEADER_TIMESTAMP, required = false) String timestamp,
                             @RequestHeader(value = HEADER_NONCE, required = false) String nonce,
                             @RequestHeader(value = HEADER_SIGN, required = false) String sign) {
        AjaxResult denied = authenticate(appId, timestamp, nonce, signPayload(request), sign);
        if (denied != null) {
            return denied;
        }
        if (request == null || request.getPlayerId() == null || request.getPlayerId().isEmpty()) {
            return AjaxResult.error("playerId 不能为空");
        }
        if (request.getGameCode() == null || request.getGameCode().isEmpty()) {
            return AjaxResult.error("gameCode 不能为空");
        }
        try {
            return AjaxResult.success(vendorLaunchService.launch(appId, request));
        } catch (IllegalArgumentException e) {
            log.warn("vendor launch rejected: appId={}, reason={}", appId, e.getMessage());
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 健康检查（无需签名，供负载均衡探活）
     */
    @GetMapping("/health")
    public AjaxResult health() {
        if (!vendorProperties.isEnabled()) {
            return AjaxResult.error("vendor api disabled");
        }
        AjaxResult result = AjaxResult.success();
        result.put("ok", true);
        return result;
    }

    /**
     * 统一认证
     *
     * @return null 表示通过；非 null 为拒绝响应
     */
    private AjaxResult authenticate(String appId, String timestamp, String nonce, String body, String sign) {
        if (!vendorProperties.isEnabled()) {
            // 厂商模式未开启：与"不存在该接口"表现一致，避免暴露能力
            return AjaxResult.error("404", "not found");
        }
        VendorProperties.VendorApp app = vendorProperties.findApp(appId);
        if (app == null || !app.isEnabled()) {
            log.warn("vendor auth failed: unknown or disabled appId={}", appId);
            return AjaxResult.error("401", "unauthorized");
        }
        if (!vendorSignUtil.verify(app, timestamp, nonce, body, sign)) {
            log.warn("vendor auth failed: bad signature, appId={}", appId);
            return AjaxResult.error("401", "signature invalid");
        }
        return null;
    }

    /**
     * 构造参与签名的 body 串
     *
     * <p>为使签名可复现，采用字段拼接而非 JSON 序列化（避免键序/空格差异）。
     */
    private String signPayload(VendorLaunchRequest request) {
        if (request == null) {
            return "";
        }
        return nullToEmpty(request.getPlayerId()) + "|"
                + nullToEmpty(request.getNickname()) + "|"
                + nullToEmpty(request.getGameCode()) + "|"
                + nullToEmpty(request.getWsBaseUrl());
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
