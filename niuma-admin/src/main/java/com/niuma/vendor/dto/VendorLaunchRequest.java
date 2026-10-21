package com.niuma.vendor.dto;

import java.io.Serializable;

/**
 * 厂商启动游戏请求
 */
public class VendorLaunchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 聚合平台侧玩家唯一 ID（作为 NiuMa 账号名） */
    private String playerId;

    /** 玩家昵称（可选，默认取 playerId） */
    private String nickname;

    /** 游戏编码，取值见 GET /vendor/catalog，如 MAHJONG、LACKEY、GUANDAN */
    private String gameCode;

    /** WebSocket 基础地址（可选；不传则返回服务端默认配置） */
    private String wsBaseUrl;

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getGameCode() {
        return gameCode;
    }

    public void setGameCode(String gameCode) {
        this.gameCode = gameCode;
    }

    public String getWsBaseUrl() {
        return wsBaseUrl;
    }

    public void setWsBaseUrl(String wsBaseUrl) {
        this.wsBaseUrl = wsBaseUrl;
    }
}
