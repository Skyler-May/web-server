package com.niuma.vendor.util;

import com.niuma.vendor.config.VendorProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

/**
 * 厂商接入签名工具（HMAC-SHA256 + 时间戳 + nonce 防重放）
 *
 * <p>签名串构造（固定顺序，参与签名的值一律 urlencode 后拼接）：
 * <pre>
 * appId + "\n" + timestamp + "\n" + nonce + "\n" + body
 * </pre>
 * body 为原始请求体的 JSON 字符串（GET 请求传空串）。
 *
 * <p>防重放：nonce 写入 Redis（key = vendor:nonce:{appId}:{nonce}），
 * TTL = signTtlSeconds。重复 nonce 直接拒绝。
 *
 * <p>安全性说明：
 * <ul>
 *   <li>使用 {@link MessageDigest#isEqual} 做恒定时间比较，防时序攻击</li>
 *   <li>时间戳超出 TTL 视为过期</li>
 *   <li>密钥只从配置读取，绝不写入日志</li>
 * </ul>
 */
@Component
public class VendorSignUtil {

    /** Redis 中 nonce 的键前缀 */
    private static final String NONCE_KEY_PREFIX = "vendor:nonce:";

    private static final String HMAC_SHA256 = "HmacSHA256";

    @Autowired
    private VendorProperties vendorProperties;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 计算签名
     *
     * @param appSecret 接入方密钥
     * @param appId     接入方标识
     * @param timestamp 时间戳（秒）
     * @param nonce     随机串
     * @param body      原始请求体（GET 传空串）
     * @return 小写十六进制签名
     */
    public String sign(String appSecret, String appId, String timestamp, String nonce, String body) {
        String payload = appId + "\n" + timestamp + "\n" + nonce + "\n" + (body == null ? "" : body);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return toHex(raw);
        } catch (Exception e) {
            // 签名失败属于服务端配置问题，不应静默放行
            throw new IllegalStateException("vendor sign failed", e);
        }
    }

    /**
     * 校验签名（含时间戳校验 + nonce 防重放）
     *
     * @param app          接入方配置
     * @param timestamp    请求时间戳（秒）
     * @param nonce        随机串
     * @param body         原始请求体
     * @param providedSign 请求携带的签名
     * @return 校验通过返回 true
     */
    public boolean verify(VendorProperties.VendorApp app, String timestamp, String nonce,
                          String body, String providedSign) {
        if (app == null || providedSign == null || timestamp == null || nonce == null) {
            return false;
        }
        // 1) 时间戳有效期
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000L;
        if (Math.abs(now - ts) > vendorProperties.getSignTtlSeconds()) {
            return false;
        }
        // 2) 签名恒定时间比较
        String expected = sign(app.getAppSecret(), app.getAppId(), timestamp, nonce, body);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                providedSign.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        // 3) nonce 防重放：成功写入才放行
        return reserveNonce(app.getAppId(), nonce);
    }

    /**
     * 占用 nonce；已存在说明重放，返回 false
     */
    private boolean reserveNonce(String appId, String nonce) {
        String key = NONCE_KEY_PREFIX + appId + ":" + nonce;
        Boolean ok = this.stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", vendorProperties.getSignTtlSeconds(), TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String h = Integer.toHexString(b & 0xFF);
            if (h.length() == 1) {
                sb.append('0');
            }
            sb.append(h);
        }
        return sb.toString();
    }
}
