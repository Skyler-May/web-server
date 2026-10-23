package com.niuma.vendor.settle.impl;

import com.niuma.admin.constant.NiuMaConstants;
import com.niuma.vendor.settle.SettleProvider;
import com.niuma.vendor.settle.SettleRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 比鸡结算数据源
 *
 * 数据表：game_bi_ji_round（由 C++ 侧在每局结算时写入）
 *
 * 背景：比鸡原先只在内存算金币，最终只把「押金余额」写入 cash_pledge，
 * 没有逐局流水，聚合平台拿不到输赢数据（返水/打码/风控失效）。
 * 为此在 C++ 侧补了逐局明细写入，本类负责转成平台可消费的 SettleRecord。
 *
 * 幂等键：{gameType}:{roundId}:{playerId}
 *  · roundId 为自增主键，一局内每玩家一条记录，因此需带 playerId
 */
@Component
public class BiJiSettleProvider implements SettleProvider {

    private final JdbcTemplate jdbcTemplate;

    public BiJiSettleProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String gameCode() {
        return "BIJI";
    }

    @Override
    public int gameType() {
        return NiuMaConstants.GAME_TYPE_BI_JI;
    }

    @Override
    public List<SettleRecord> fetchSince(long sinceMillis, int limit) {
        // 按记录 ID 升序，保证扫描稳定推进（不依赖时间精度）
        String sql = "SELECT id, venue_id, round_no, player_id, seat, score, di_zhu, win_gold, tax, time "
                + "FROM game_bi_ji_round "
                + "WHERE time >= ? "
                + "ORDER BY id ASC "
                + "LIMIT ?";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql, new Timestamp(sinceMillis), limit);

        List<SettleRecord> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            SettleRecord r = new SettleRecord();
            r.setGameType(gameType());
            r.setGameCode(gameCode());

            String recordId = String.valueOf(row.get("id"));
            String playerId = String.valueOf(row.get("player_id"));
            r.setRoundId(recordId);
            r.setPlayerId(playerId);
            r.setVenueId(row.get("venue_id") == null ? null : String.valueOf(row.get("venue_id")));
            r.setSettleId(gameType() + ":" + recordId + ":" + playerId);

            // 输赢金额：金币 1:1 换算成平台金额
            Object winGold = row.get("win_gold");
            long gold = winGold == null ? 0L : ((Number) winGold).longValue();
            r.setWinGold(gold);
            r.setWinLoss(BigDecimal.valueOf(gold));

            Object time = row.get("time");
            r.setSettleTime(time == null ? null : time.toString());

            result.add(r);
        }
        return result;
    }
}
