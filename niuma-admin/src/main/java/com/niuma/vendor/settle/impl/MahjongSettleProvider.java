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
 * 麻将结算提供者
 *
 * 数据源：game_mahjong_record
 * 结构特点：**宽表**——玩家与输赢按座位号平铺在同一行（player_id0~3 / win_gold0~3），
 * 需要拆成「一行一玩家」的扁平记录。
 *
 * 这正是本层存在的意义：把不同游戏的表结构差异吸收在 Provider 内部，
 * 扫描任务只认识统一的 SettleRecord。
 */
@Component
public class MahjongSettleProvider implements SettleProvider {

    /** 麻将固定 4 个座位 */
    private static final int SEATS = 4;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public int gameType() {
        return NiuMaConstants.GAME_TYPE_MAHJONG;
    }

    @Override
    public String gameCode() {
        return "MAHJONG";
    }

    @Override
    public List<SettleRecord> fetchSince(long sinceMillis, int limit) {
        // 宽表：一局一行，玩家按座位平铺
        String sql = "SELECT id AS round_id, venue_id, time, " +
                "       player_id0, score0, win_gold0, " +
                "       player_id1, score1, win_gold1, " +
                "       player_id2, score2, win_gold2, " +
                "       player_id3, score3, win_gold3 " +
                "FROM game_mahjong_record " +
                "WHERE time >= ? " +
                "ORDER BY id ASC " +
                "LIMIT ?";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql, new Timestamp(sinceMillis), limit);

        List<SettleRecord> result = new ArrayList<>(rows.size() * SEATS);
        for (Map<String, Object> row : rows) {
            String roundId = String.valueOf(row.get("round_id"));
            String venueId = row.get("venue_id") == null ? null : String.valueOf(row.get("venue_id"));
            Object time = row.get("time");

            // 拆宽表：每个座位一条记录
            for (int seat = 0; seat < SEATS; seat++) {
                Object playerIdObj = row.get("player_id" + seat);
                if (playerIdObj == null) {
                    // 空座（人未满开局或中途退出）
                    continue;
                }
                String playerId = String.valueOf(playerIdObj);

                SettleRecord r = new SettleRecord();
                r.setGameType(gameType());
                r.setGameCode(gameCode());
                r.setRoundId(roundId);
                r.setPlayerId(playerId);
                r.setVenueId(venueId);
                r.setSettleId(gameType() + ":" + roundId + ":" + playerId);

                Object score = row.get("score" + seat);
                r.setWinLoss(score == null ? BigDecimal.ZERO : new BigDecimal(score.toString()));

                Object winGold = row.get("win_gold" + seat);
                r.setWinGold(winGold == null ? 0L : ((Number) winGold).longValue());

                r.setSettleTime(time == null ? null : time.toString());
                result.add(r);
            }
        }
        return result;
    }
}
