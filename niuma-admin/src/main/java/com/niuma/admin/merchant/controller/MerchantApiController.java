package com.niuma.admin.merchant.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.niuma.admin.merchant.MerchantException;
import com.niuma.admin.merchant.entity.Merchant;
import com.niuma.admin.merchant.service.IMerchantService;
import com.niuma.admin.merchant.service.impl.MerchantServiceImpl;
import com.niuma.common.annotation.Anonymous;
import com.niuma.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.Map;

/**
 * 聚合商户接入 API（转账钱包）
 *
 * 鉴权：商户级 HMAC-SHA256 签名（X-Merchant-Id / X-Timestamp / X-Nonce / X-Sign），
 * 不依赖平台账号登录态，因此统一 {@link Anonymous} 放行，由本控制器自行校验。
 * 签名内容：merchantId + timestamp + nonce + body
 *   - POST：body 为原始请求体
 *   - GET ：body 为空串，业务参数走 query
 *
 * 自营玩家不经过这里，行为不受影响。
 */
@Anonymous
@RestController
@RequestMapping("/api/merchant")
public class MerchantApiController {

    private static final Logger log = LoggerFactory.getLogger(MerchantApiController.class);

    @Autowired
    private IMerchantService merchantService;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody(required = false) String rawBody, HttpServletRequest request) {
        return handlePost("login", rawBody, request, (m, body) -> merchantService.login(m, body));
    }

    @PostMapping("/transfer")
    public Map<String, Object> transfer(@RequestBody(required = false) String rawBody, HttpServletRequest request) {
        return handlePost("transfer", rawBody, request, (m, body) -> merchantService.transfer(m, body));
    }

    @PostMapping("/balance")
    public Map<String, Object> balancePost(@RequestBody(required = false) String rawBody, HttpServletRequest request) {
        return handlePost("balance", rawBody, request, (m, body) -> merchantService.balance(m, body));
    }

    @GetMapping("/balance")
    public Map<String, Object> balanceGet(HttpServletRequest request) {
        return handleGet("balance", request, (m, body) -> merchantService.balance(m, body));
    }

    @PostMapping("/kick")
    public Map<String, Object> kick(@RequestBody(required = false) String rawBody, HttpServletRequest request) {
        return handlePost("kick", rawBody, request, (m, body) -> merchantService.kick(m, body));
    }

    @PostMapping("/rounds")
    public Map<String, Object> roundsPost(@RequestBody(required = false) String rawBody, HttpServletRequest request) {
        return handlePost("rounds", rawBody, request, (m, body) -> merchantService.rounds(m, body));
    }

    @GetMapping("/rounds")
    public Map<String, Object> roundsGet(HttpServletRequest request) {
        return handleGet("rounds", request, (m, body) -> merchantService.rounds(m, body));
    }

    // ==========================================================
    // 统一签名校验 + 异常包装
    // ==========================================================

    private interface MerchantHandler {
        Map<String, Object> invoke(Merchant merchant, JSONObject body);
    }

    private Map<String, Object> handlePost(String api, String rawBody, HttpServletRequest request, MerchantHandler handler) {
        String body = rawBody == null ? "" : rawBody;
        try {
            Merchant merchant = verify(api, body, request);
            JSONObject json = StringUtils.isEmpty(body) ? new JSONObject() : JSON.parseObject(body);
            return MerchantServiceImpl.ok(handler.invoke(merchant, json));
        } catch (MerchantException e) {
            log.warn("merchant api {} rejected, merchant={}, code={}, msg={}", api, request.getHeader("X-Merchant-Id"), e.getCode(), e.getMessage());
            return MerchantServiceImpl.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("merchant api {} error, merchant={}", api, request.getHeader("X-Merchant-Id"), e);
            return MerchantServiceImpl.fail(9999, "internal error");
        }
    }

    private Map<String, Object> handleGet(String api, HttpServletRequest request, MerchantHandler handler) {
        try {
            Merchant merchant = verify(api, "", request);
            JSONObject json = new JSONObject();
            Enumeration<String> names = request.getParameterNames();
            while (names.hasMoreElements()) {
                String key = names.nextElement();
                json.put(key, request.getParameter(key));
            }
            return MerchantServiceImpl.ok(handler.invoke(merchant, json));
        } catch (MerchantException e) {
            log.warn("merchant api {} rejected, merchant={}, code={}, msg={}", api, request.getHeader("X-Merchant-Id"), e.getCode(), e.getMessage());
            return MerchantServiceImpl.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("merchant api {} error, merchant={}", api, request.getHeader("X-Merchant-Id"), e);
            return MerchantServiceImpl.fail(9999, "internal error");
        }
    }

    private Merchant verify(String api, String signedBody, HttpServletRequest request) {
        String merchantId = request.getHeader("X-Merchant-Id");
        String timestamp = request.getHeader("X-Timestamp");
        String nonce = request.getHeader("X-Nonce");
        String sign = request.getHeader("X-Sign");
        Merchant merchant = merchantService.verify(merchantId, timestamp, nonce, signedBody, getClientIp(request));
        if (!merchantService.checkSign(merchant, timestamp, nonce, signedBody, sign)) {
            throw MerchantException.badSign();
        }
        return merchant;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotEmpty(ip) && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.isNotEmpty(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }
}
