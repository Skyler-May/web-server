package com.niuma.vendor.settle;

import java.math.BigDecimal;

/**
 * 统一结算记录
 *
 * 各游戏表结构完全不同（麻将是 player_id0~3 宽表，跑得快是子表），
 * 本类把差异吸收在各自的 Provider 内部，对外只暴露这一种形状。
 *
 * 幂等键约定：`settleId` 由「游戏 + 局ID + 玩家ID」确定性生成，
 * 同一局同一玩家重复扫描产生相同 settleId，平台侧靠唯一约束去重。
 */
public class SettleRecord {

    /** 幂等键：{gameType}:{roundId}:{playerId} */
    private String settleId;

    /** NiuMa 游戏类型（1021 麻将 / 1028 跑得快 / 1030 掼蛋…） */
    private Integer gameType;

    /** 游戏编码（MAHJONG / LACKEY / GUANDAN…），与 catalog 一致 */
    private String gameCode;

    /** NiuMa 局号（厂商侧唯一） */
    private String roundId;

    /** NiuMa 玩家 ID */
    private String playerId;

    /** 场地 ID */
    private String venueId;

    /** 本局输赢（正=赢，负=输）；该玩家视角 */
    private BigDecimal winLoss;

    /** 本局输赢金币（NiuMa 口径，整数） */
    private Long winGold;

    /** 本局结束时间（yyyy-MM-dd HH:mm:ss） */
    private String settleTime;

    public String getSettleId() {
        return settleId;
    }

    public void setSettleId(String settleId) {
        this.settleId = settleId;
    }

    public Integer getGameType() {
        return gameType;
    }

    public void setGameType(Integer gameType) {
        this.gameType = gameType;
    }

    public String getGameCode() {
        return gameCode;
    }

    public void setGameCode(String gameCode) {
        this.gameCode = gameCode;
    }

    public String getRoundId() {
        return roundId;
    }

    public void setRoundId(String roundId) {
        this.roundId = roundId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getVenueId() {
        return venueId;
    }

    public void setVenueId(String venueId) {
        this.venueId = venueId;
    }

    public BigDecimal getWinLoss() {
        return winLoss;
    }

    public void setWinLoss(BigDecimal winLoss) {
        this.winLoss = winLoss;
    }

    public Long getWinGold() {
        return winGold;
    }

    public void setWinGold(Long winGold) {
        this.winGold = winGold;
    }

    public String getSettleTime() {
        return settleTime;
    }

    public void setSettleTime(String settleTime) {
        this.settleTime = settleTime;
    }
}
