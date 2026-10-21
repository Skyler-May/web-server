package com.niuma.vendor.controller;

import com.niuma.admin.entity.Capital;
import com.niuma.admin.service.ICapitalService;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.vendor.config.VendorProperties;
import com.niuma.vendor.dto.VendorTransferRequest;
import com.niuma.vendor.util.VendorSignUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 标准厂商 API —— 资金接口（中心钱包 ↔ 厂商子钱包）
 *
 * 对应聚合平台的钱包划转流程：
 *  · 转入（deposit）：玩家从中心钱包转钱进 NiuMa，才能下注
 *  · 转出（withdraw）：玩家退出游戏后「回收」剩余金币回中心钱包
 *  · 余额（balance）：回收时必须按 NiuMa 实际余额取回，不能用平台记账金额
 *
 * 与原有 /player/capital/debit|deposit 的区别：
 *  · 原有接口依赖「当前登录玩家」上下文，只能玩家客户端自己调
 *  · 本接口供聚合平台服务端调用，playerId 显式指定 + HMAC 签名认证
 *
 * 金额：NiuMa 金币为整数，与平台货币 1:1。
 */
@RestController
@RequestMapping("${niuma.vendor.api-prefix:/vendor}")
public class VendorCapitalController {

    private static final Logger log = LoggerFactory.getLogger(VendorCapitalController.class);

    private static final String HEADER_APP_ID = "X-Vendor-AppId";
    private static final String HEADER_TIMESTAMP = "X-Vendor-Timestamp";
    private static final String HEADER_NONCE = "X-Vendor-Nonce";
    private static final String HEADER_SIGN = "X-Vendor-Sign";

    @Autowired
    private VendorProperties vendorProperties;

    @Autowired
    private VendorSignUtil vendorSignUtil;

    @Autowired
    private ICapitalService capitalService;

    /**
     * 查询玩家在 NiuMa 的金币余额
     *
     * 回收流程：先查余额 → 再按该余额转出，避免金额不一致。
     */
    @GetMapping("/capital/balance")
    public AjaxResult balance(@RequestParam("playerId") String playerId,
                              @RequestHeader(value = HEADER_APP_ID, required = false) String appId,
                              @RequestHeader(value = HEADER_TIMESTAMP, required = false) String timestamp,
                              @RequestHeader(value = HEADER_NONCE, required = false) String nonce,
                              @RequestHeader(value = HEADER_SIGN, required = false) String sign) {
        AjaxResult denied = authenticate(appId, timestamp, nonce, playerId, sign);
        if (denied != null) {
            return denied;
        }
        String innerId = this.capitalService.resolvePlayerId(playerId);
        if (innerId == null) {
            return AjaxResult.error("玩家不存在: " + playerId);
        }
        Capital capital = this.capitalService.getCapital(innerId);
        AjaxResult result = AjaxResult.success();
        result.put("playerId", innerId);
        result.put("externalPlayerId", playerId);
        result.put("gold", capital == null || capital.getGold() == null ? 0L : capital.getGold());
        result.put("diamond", capital == null || capital.getDiamond() == null ? 0L : capital.getDiamond());
        return result;
    }

    /**
     * 转入：平台中心钱包 → NiuMa 金币（进游戏前调用）
     */
    @PostMapping("/capital/deposit")
    public AjaxResult deposit(@RequestBody VendorTransferRequest request,
                              @RequestHeader(value = HEADER_APP_ID, required = false) String appId,
                              @RequestHeader(value = HEADER_TIMESTAMP, required = false) String timestamp,
                              @RequestHeader(value = HEADER_NONCE, required = false) String nonce,
                              @RequestHeader(value = HEADER_SIGN, required = false) String sign) {
        return doTransfer(request, true, appId, timestamp, nonce, sign);
    }

    /**
     * 转出：NiuMa 金币 → 平台中心钱包（退出/回收时调用）
     */
    @PostMapping("/capital/withdraw")
    public AjaxResult withdraw(@RequestBody VendorTransferRequest request,
                               @RequestHeader(value = HEADER_APP_ID, required = false) String appId,
                               @RequestHeader(value = HEADER_TIMESTAMP, required = false) String timestamp,
                               @RequestHeader(value = HEADER_NONCE, required = false) String nonce,
                               @RequestHeader(value = HEADER_SIGN, required = false) String sign) {
        return doTransfer(request, false, appId, timestamp, nonce, sign);
    }

    // ---------------- 内部实现 ----------------

    private AjaxResult doTransfer(VendorTransferRequest request, boolean isIn,
                                  String appId, String timestamp, String nonce, String sign) {
        if (request == null || request.getPlayerId() == null || request.getPlayerId().isEmpty()) {
            return AjaxResult.error("playerId 不能为空");
        }
        if (request.getAmount() == null || request.getAmount() <= 0) {
            return AjaxResult.error("amount 必须大于 0");
        }
        // 签名串与调用方约定：playerId|amount
        String payload = request.getPlayerId() + "|" + request.getAmount();
        AjaxResult denied = authenticate(appId, timestamp, nonce, payload, sign);
        if (denied != null) {
            return denied;
        }

        try {
            boolean ok = this.capitalService.vendorTransfer(
                    request.getPlayerId(), request.getAmount(), isIn);
            if (!ok) {
                // 乐观锁冲突：调用方重试即可，属于可恢复错误
                return AjaxResult.error("划转冲突，请重试");
            }
            String innerId = this.capitalService.resolvePlayerId(request.getPlayerId());
            Capital after = innerId == null ? null : this.capitalService.getCapital(innerId);
            AjaxResult result = AjaxResult.success();
            result.put("playerId", innerId);
            result.put("externalPlayerId", request.getPlayerId());
            result.put("gold", after == null || after.getGold() == null ? 0L : after.getGold());
            result.put("amount", request.getAmount());
            result.put("direction", isIn ? "IN" : "OUT");
            log.info("vendor capital transfer: appId={} playerId={} amount={} direction={} goldAfter={}",
                    appId, request.getPlayerId(), request.getAmount(), isIn ? "IN" : "OUT",
                    after == null ? null : after.getGold());
            return result;
        } catch (IllegalStateException e) {
            // 余额不足/玩家不存在等业务错误：消息可直接展示给调用方
            log.warn("vendor capital transfer rejected: playerId={} reason={}",
                    request.getPlayerId(), e.getMessage());
            return AjaxResult.error(e.getMessage());
        }
    }

    private AjaxResult authenticate(String appId, String timestamp, String nonce, String payload, String sign) {
        if (!vendorProperties.isEnabled()) {
            return AjaxResult.error("404", "not found");
        }
        VendorProperties.VendorApp app = vendorProperties.findApp(appId);
        if (app == null || !app.isEnabled()) {
            return AjaxResult.error("401", "unauthorized");
        }
        if (!vendorSignUtil.verify(app, timestamp, nonce, payload, sign)) {
            return AjaxResult.error("401", "signature invalid");
        }
        return null;
    }
}
