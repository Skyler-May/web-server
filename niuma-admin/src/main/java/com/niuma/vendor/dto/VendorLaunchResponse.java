package com.niuma.vendor.dto;

import java.io.Serializable;

/**
 * 厂商启动游戏响应
 *
 * <p>聚合平台拿到 token + gameType 后，直接用客户端进入该游戏的选场次页，
 * 不经过 NiuMa 总大厅。
 */
public class VendorLaunchResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** NiuMa 玩家令牌（放入 HTTP 头 PLAYER-AUTHORIZATION 使用） */
    private String token;

    /** 聚合平台侧玩家 ID（回显，便于对账） */
    private String externalPlayerId;

    /** NiuMa 内部玩家 ID */
    private String playerId;

    /** 游戏编码（回显） */
    private String gameCode;

    /** NiuMa 游戏类型（进房时传给 /player/game/create 或 /enter） */
    private Integer gameType;

    /** 客户端资源标识（路由用） */
    private String resourceKey;

    /** WebSocket 基础地址（客户端连接对局长连接用） */
    private String wsUrl;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getExternalPlayerId() {
        return externalPlayerId;
    }

    public void setExternalPlayerId(String externalPlayerId) {
        this.externalPlayerId = externalPlayerId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getGameCode() {
        return gameCode;
    }

    public void setGameCode(String gameCode) {
        this.gameCode = gameCode;
    }

    public Integer getGameType() {
        return gameType;
    }

    public void setGameType(Integer gameType) {
        this.gameType = gameType;
    }

    public String getResourceKey() {
        return resourceKey;
    }

    public void setResourceKey(String resourceKey) {
        this.resourceKey = resourceKey;
    }

    public String getWsUrl() {
        return wsUrl;
    }

    public void setWsUrl(String wsUrl) {
        this.wsUrl = wsUrl;
    }
}
