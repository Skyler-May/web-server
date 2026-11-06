package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Settlement outbox written by the game server (one row per player per round).
 */
@Data
@TableName("game_settle_outbox")
public class GameSettleOutbox {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String venueId;
    private Integer roundNo;
    private Integer gameType;
    private String playerId;
    private Integer delta;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime settledAt;
    private String remark;
}