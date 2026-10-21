package com.niuma.vendor.settle;

import java.util.List;

/**
 * 结算数据提供者（每个游戏一个实现）
 *
 * 各游戏的记录表结构差异极大，本接口把差异封装在实现类里：
 *  · 麻将：game_mahjong_record，玩家信息是 player_id0~3 / win_gold0~3 宽表
 *  · 跑得快：game_lackey_round + game_lackey_round_player 子表
 *  · 掼蛋：game_guan_dan_round，玩家 player_id_0~3，含红蓝方得分
 *
 * 新增游戏 = 新增一个实现类 + 注册到 SettleProviderRegistry，**不改扫描任务**。
 */
public interface SettleProvider {

    /** 对应 NiuMa 的游戏类型 */
    int gameType();

    /** 游戏编码（与厂商 catalog 的 gameCode 一致） */
    String gameCode();

    /**
     * 查询指定时间之后结束的局结算记录
     *
     * @param sinceMillis 起始时间戳（毫秒）；只返回该时间之后的记录
     * @param limit       单次最多返回条数（防止一次拉爆内存）
     * @return 统一格式的结算记录；无数据返回空列表
     */
    List<SettleRecord> fetchSince(long sinceMillis, int limit);
}
