package com.niuma.vendor.settle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.niuma.vendor.config.VendorProperties;
import com.niuma.vendor.util.VendorSignUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 结算推送任务
 *
 * NiuMa 自身无「实时回调平台」的能力（对局结算在 C++ 侧完成，不会主动通知外部），
 * 因此采用**定时增量扫描 + 主动推送**补偿：
 *
 *   各游戏表 → SettleProvider 统一成 SettleRecord → HMAC 签名 → POST 平台结算接口
 *
 * 幂等保证：
 *  · 每条记录带确定性 settleId（gameType:roundId:playerId），重复推送不产生重复账
 *  · 平台侧以 settleId 建唯一索引兜底
 *
 * 水位推进（避免重复扫描与漏扫）：
 *  · 用 `lastScanMillis` 记录上次扫描边界，每次成功后推进
 *  · 回退 60 秒重叠窗口，容忍事务提交延迟导致的「时间戳已过但数据尚未可见」
 */
@Component
@ConditionalOnProperty(prefix = "niuma.vendor", name = {"enabled", "settle-job-enabled"}, havingValue = "true")
public class SettlePushJob {

    private static final Logger log = LoggerFactory.getLogger(SettlePushJob.class);

    /** 单次每游戏最多拉取条数 */
    private static final int BATCH_LIMIT = 500;

    /** 重叠窗口（毫秒）：容忍事务提交延迟，宁可重复扫（幂等兜底）也不漏 */
    private static final long OVERLAP_MS = 60_000L;

    @Autowired
    private VendorProperties vendorProperties;

    @Autowired
    private List<SettleProvider> settleProviders;

    @Autowired
    private VendorSignUtil vendorSignUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    /** 上次扫描水位（毫秒）；启动时回溯 5 分钟，保证重启后补齐 */
    private volatile long lastScanMillis = System.currentTimeMillis() - 5 * 60 * 1000L;

    /**
     * 定时扫描并推送
     *
     * 间隔由 niuma.vendor.settle-job-interval-ms 控制（默认 30s）
     */
    @Scheduled(fixedDelayString = "${niuma.vendor.settle-job-interval-ms:30000}")
    public void scanAndPush() {
        if (!vendorProperties.isEnabled() || vendorProperties.getApps() == null) {
            return;
        }
        long since = Math.max(0L, lastScanMillis - OVERLAP_MS);
        long scanStart = System.currentTimeMillis();

        for (VendorProperties.VendorApp app : vendorProperties.getApps()) {
            if (!app.isEnabled() || isBlank(app.getSettleCallbackUrl())) {
                continue;
            }
            for (SettleProvider provider : settleProviders) {
                try {
                    List<SettleRecord> records = provider.fetchSince(since, BATCH_LIMIT);
                    if (records.isEmpty()) {
                        continue;
                    }
                    boolean ok = push(app, provider.gameCode(), records);
                    if (ok) {
                        log.info("settle pushed: app={}, game={}, count={}",
                                app.getAppId(), provider.gameCode(), records.size());
                    }
                } catch (Exception e) {
                    // 单个游戏失败不影响其他游戏，也不阻塞水位推进（下轮重叠重试）
                    log.error("settle push failed: app={}, game={}, err={}",
                            app.getAppId(), provider.gameCode(), e.getMessage(), e);
                }
            }
        }
        // 只有整轮跑完才推进水位，保证失败的游戏下轮仍会被覆盖
        lastScanMillis = scanStart;
    }

    /**
     * 推送一批结算记录
     *
     * @return true=平台确认成功
     */
    @SuppressWarnings("unchecked")
    private boolean push(VendorProperties.VendorApp app, String gameCode, List<SettleRecord> records) throws Exception {
        Map<String, Object> payload = new HashMap<>(4);
        payload.put("gameCode", gameCode);
        payload.put("count", records.size());
        payload.put("records", toList(records));

        String body = objectMapper.writeValueAsString(payload);

        // 签名串必须与平台端 VendorCallbackController#canonicalBody 完全一致
        String canonical = canonicalBody(gameCode, records);

        // 与厂商 API 同一套签名：appId\ntimestamp\nnonce\ncanonical
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String sign = vendorSignUtil.sign(app.getAppSecret(), app.getAppId(), timestamp, nonce, canonical);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Vendor-AppId", app.getAppId());
        headers.set("X-Vendor-Timestamp", timestamp);
        headers.set("X-Vendor-Nonce", nonce);
        headers.set("X-Vendor-Sign", sign);

        String resp = restTemplate.postForObject(
                app.getSettleCallbackUrl(), new HttpEntity<>(body, headers), String.class);

        if (resp == null) {
            log.warn("settle push no response: app={}, game={}", app.getAppId(), gameCode);
            return false;
        }
        // 平台返回 success=true 才算成功
        Map<String, Object> result = objectMapper.readValue(resp, Map.class);
        Object success = result.get("success");
        if (!Boolean.TRUE.equals(success)) {
            log.warn("settle push rejected: app={}, game={}, resp={}", app.getAppId(), gameCode, resp);
            return false;
        }
        return true;
    }

    /**
     * 构造与平台端一致的规范化签名串
     *
     * 平台端 VendorCallbackController#canonicalBody 的规则：
     *   gameCode={gameCode}&count={n}&{settleId}|{playerId}|{roundId}|{winLoss}...
     * 两端必须逐字节一致，否则签名校验必然失败。
     */
    private String canonicalBody(String gameCode, List<SettleRecord> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("gameCode=").append(gameCode == null ? "" : gameCode)
          .append("&count=").append(records.size());
        for (SettleRecord r : records) {
            sb.append("&").append(r.getSettleId() == null ? "" : r.getSettleId());
            sb.append("|").append(r.getPlayerId() == null ? "" : r.getPlayerId());
            sb.append("|").append(r.getRoundId() == null ? "" : r.getRoundId());
            sb.append("|").append(r.getWinLoss() == null ? "" : r.getWinLoss().toPlainString());
        }
        return sb.toString();
    }

    private List<Map<String, Object>> toList(List<SettleRecord> records) {
        List<Map<String, Object>> list = new ArrayList<>(records.size());
        for (SettleRecord r : records) {
            Map<String, Object> m = new HashMap<>(16);
            m.put("settleId", r.getSettleId());
            m.put("gameType", r.getGameType());
            m.put("gameCode", r.getGameCode());
            m.put("roundId", r.getRoundId());
            m.put("playerId", r.getPlayerId());
            m.put("venueId", r.getVenueId());
            m.put("winLoss", r.getWinLoss() == null ? 0 : r.getWinLoss().toPlainString());
            m.put("winGold", r.getWinGold());
            m.put("settleTime", r.getSettleTime());
            list.add(m);
        }
        return list;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
