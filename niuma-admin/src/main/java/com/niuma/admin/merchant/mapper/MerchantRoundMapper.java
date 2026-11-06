package com.niuma.admin.merchant.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 注单查询（对账用）—— 只读既有注单表，不参与自营流程
 */
@Repository
public interface MerchantRoundMapper {

    @Select("SELECT id AS roundId, venue_id AS venueId, round_no AS roundNo, `time` AS time, "
            + "CASE WHEN player_id0 = #{pid} THEN 0 WHEN player_id1 = #{pid} THEN 1 "
            + "WHEN player_id2 = #{pid} THEN 2 WHEN player_id3 = #{pid} THEN 3 ELSE -1 END AS seat, "
            + "CASE WHEN player_id0 = #{pid} THEN win_gold0 WHEN player_id1 = #{pid} THEN win_gold1 "
            + "WHEN player_id2 = #{pid} THEN win_gold2 WHEN player_id3 = #{pid} THEN win_gold3 ELSE 0 END AS winGold "
            + "FROM game_mahjong_record "
            + "WHERE (#{pid} IN (player_id0, player_id1, player_id2, player_id3)) "
            + "AND (#{fromTime} IS NULL OR `time` >= #{fromTime}) "
            + "AND (#{toTime} IS NULL OR `time` <= #{toTime}) "
            + "ORDER BY id DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> pageMahjongRounds(@Param("pid") String vendorPlayerId,
                                                @Param("fromTime") String fromTime,
                                                @Param("toTime") String toTime,
                                                @Param("offset") int offset,
                                                @Param("size") int size);

    @Select("SELECT COUNT(1) FROM game_mahjong_record "
            + "WHERE (#{pid} IN (player_id0, player_id1, player_id2, player_id3)) "
            + "AND (#{fromTime} IS NULL OR `time` >= #{fromTime}) "
            + "AND (#{toTime} IS NULL OR `time` <= #{toTime})")
    int countMahjongRounds(@Param("pid") String vendorPlayerId,
                           @Param("fromTime") String fromTime,
                           @Param("toTime") String toTime);
}
