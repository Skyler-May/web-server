package com.niuma.vendor.dto;

import java.io.Serializable;

/**
 * 厂商资金划转请求
 *
 * 由聚合平台服务端调用，在「平台中心钱包」与「NiuMa 游戏金币」之间划转。
 * 金额单位：NiuMa 金币（整数），与平台货币 1:1。
 */
public class VendorTransferRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 平台玩家标识（NiuMa 侧会解析为内部 ID；也接受内部 ID） */
    private String playerId;

    /** 划转数量（必须 > 0，单位金币） */
    private Long amount;

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }
}
