package com.niuma.admin.merchant.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

/**
 * 商户钱包操作：直接作用于既有 capital 表（不改表结构），
 * 用单条原子 SQL 完成加减，避免读改写竞态。
 */
@Repository
public interface MerchantCapitalMapper {

    /**
     * 原子加减金币：只有结果不为负时才会更新
     *
     * @param playerId NiuMa 玩家ID
     * @param delta    变动值（IN 为正，OUT 为负）
     * @return 影响行数（0 表示余额不足）
     */
    @Update("UPDATE capital SET gold = gold + #{delta}, version = version + 1 "
            + "WHERE player_id = #{playerId} AND gold + #{delta} >= 0")
    int changeGold(@Param("playerId") String playerId, @Param("delta") long delta);

    @Select("SELECT gold FROM capital WHERE player_id = #{playerId}")
    Long getGold(@Param("playerId") String playerId);
}
