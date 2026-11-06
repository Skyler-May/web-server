package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Player gold change log (audit trail for settlements).
 */
@Data
@TableName("capital_log")
public class CapitalLog {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String playerId;
    private Long changeAmount;
    private Long balanceAfter;
    /** game_settle / recharge / withdraw ... */
    private String type;
    /** reference key, e.g. venueId:roundNo */
    private String refKey;
    private LocalDateTime createdAt;
}