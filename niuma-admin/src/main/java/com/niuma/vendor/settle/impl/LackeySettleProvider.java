package com.niuma.vendor.settle.impl;

import com.niuma.admin.constant.NiuMaConstants;
import com.niuma.vendor.settle.SettleProvider;
import com.niuma.vendor.settle.SettleRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 跑得快结算提供者
 *
 * 数据源：game_lackey_round（局）+ game_lackey_round_player（每玩家输赢）
 * 结构特点：主表 + 子表，一局多玩家。
 */
@Component
public class LackeySettleProvider implements SettleProvider {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public int gameType() {
        return NiuMaConstants.GAME_TYPE_LACKEY;
    }

    @Override
    public String gameCode() {
        return "LACKEY";
    }

    @Override
    public List<SettleRecord> fetchSince(long sinceMillis, int limit) {
        // 用 JOIN 一次取出「局 + 玩家」的完整信息；按局 ID 升序保证扫描稳定推进
        String sql = "SELECT r.id AS round_id, r.venue_id, r.time, " +
                "       p.player_id, p.score, p.win_gold " +
                "FROM game_lackey_round r " +
                "JOIN game_lackey_round_player p ON p.round_id = r.id " +
                "WHERE r.time >= ? " +
                "ORDER BY r.id ASC, p.player_id ASC " +
                "LIMIT ?";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql, new Timestamp(sinceMillis), limit);

        List<SettleRecord> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            SettleRecord r = new SettleRecord();
            r.setGameType(gameType());
            r.setGameCode(gameCode());

            String roundId = String.valueOf(row.get("round_id"));
            String playerId = String.valueOf(row.get("player_id"));
            r.setRoundId(roundId);
            r.setPlayerId(playerId);
            r.setVenueId(row.get("venue_id") == null ? null : String.valueOf(row.get("venue_id")));
            r.setSettleId(gameType() + ":" + roundId + ":" + playerId);

            Object score = row.get("score");
            r.setWinLoss(score == null ? BigDecimal.ZERO : new BigDecimal(score.toString()));

            Object winGold = row.get("win_gold");
            r.setWinGold(winGold == null ? 0L : ((Number) winGold).longValue());

            Object time = row.get("time");
            r.setSettleTime(time == null ? null : time.toString());

            result.add(r);
        }
        return result;
    }
}
