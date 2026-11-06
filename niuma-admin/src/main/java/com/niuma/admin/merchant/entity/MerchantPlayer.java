package com.niuma.admin.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户玩家映射：平台玩家ID ↔ NiuMa 玩家ID
 */
@Data
@TableName("merchant_player")
public class MerchantPlayer {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String merchantId;

    /** 平台玩家ID */
    private String playerId;

    /** NiuMa 玩家ID */
    private String vendorPlayerId;

    private String nickname;

    private LocalDateTime createTime;
}
