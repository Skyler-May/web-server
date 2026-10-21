package com.niuma.vendor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 厂商接入配置（标准厂商 API 模式）
 *
 * <p>设计原则：<b>默认关闭，只增不改</b>。
 * 关闭时 NiuMa 与改造前完全一致，可独立运行/独立售卖；
 * 开启后才额外暴露 {@code /vendor/**} 标准厂商接口。
 *
 * <p>对应配置示例：
 * <pre>
 * niuma:
 *   vendor:
 *     enabled: true
 *     api-prefix: /vendor
 *     nonce-ttl-seconds: 300
 *     apps:
 *       - app-id: gaming
 *         app-secret: change-me
 *         name: Gaming 聚合平台
 *         settle-callback-url: http://host:8080/api/v1/vendor/niuma/settle
 * </pre>
 *
 * @author vendor-integration
 */
@Component
@ConfigurationProperties(prefix = "niuma.vendor")
public class VendorProperties {

    /** 是否启用厂商接入模式。false 时厂商接口一律 404（不影响原有功能） */
    private boolean enabled = false;

    /** 厂商 API 路径前缀 */
    private String apiPrefix = "/vendor";

    /** 签名有效期（秒）；超过此时间的请求视为过期，防重放 */
    private long signTtlSeconds = 300L;

    /** 允许多次结算回调重试的窗口（秒） */
    private long settleWindowSeconds = 3600L;

    /** 结算扫描任务：开关 */
    private boolean settleJobEnabled = false;

    /** 结算扫描任务：间隔（毫秒），默认 30 秒 */
    private long settleJobIntervalMs = 30000L;

    /** 已授权的接入方（聚合平台）列表 */
    private List<VendorApp> apps = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiPrefix() {
        return apiPrefix;
    }

    public void setApiPrefix(String apiPrefix) {
        this.apiPrefix = apiPrefix;
    }

    public long getSignTtlSeconds() {
        return signTtlSeconds;
    }

    public void setSignTtlSeconds(long signTtlSeconds) {
        this.signTtlSeconds = signTtlSeconds;
    }

    public long getSettleWindowSeconds() {
        return settleWindowSeconds;
    }

    public void setSettleWindowSeconds(long settleWindowSeconds) {
        this.settleWindowSeconds = settleWindowSeconds;
    }

    public boolean isSettleJobEnabled() {
        return settleJobEnabled;
    }

    public void setSettleJobEnabled(boolean settleJobEnabled) {
        this.settleJobEnabled = settleJobEnabled;
    }

    public long getSettleJobIntervalMs() {
        return settleJobIntervalMs;
    }

    public void setSettleJobIntervalMs(long settleJobIntervalMs) {
        this.settleJobIntervalMs = settleJobIntervalMs;
    }

    public List<VendorApp> getApps() {
        return apps;
    }

    public void setApps(List<VendorApp> apps) {
        this.apps = apps;
    }

    /**
     * 按 appId 查找接入方
     *
     * @param appId 接入方标识
     * @return 匹配的接入方；未授权返回 null
     */
    public VendorApp findApp(String appId) {
        if (appId == null || this.apps == null) {
            return null;
        }
        for (VendorApp app : this.apps) {
            if (appId.equals(app.getAppId())) {
                return app;
            }
        }
        return null;
    }

    /**
     * 单个接入方（聚合平台）的配置
     */
    public static class VendorApp {

        /** 接入方标识（对应请求头 X-Vendor-AppId） */
        private String appId;

        /** 接入方密钥（HMAC-SHA256 签名用，不得入库/入日志） */
        private String appSecret;

        /** 接入方名称（仅用于日志排查） */
        private String name;

        /** 结算回调地址：NiuMa 主动把逐局结果推给该地址 */
        private String settleCallbackUrl;

        /** 是否启用 */
        private boolean enabled = true;

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSettleCallbackUrl() {
            return settleCallbackUrl;
        }

        public void setSettleCallbackUrl(String settleCallbackUrl) {
            this.settleCallbackUrl = settleCallbackUrl;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
