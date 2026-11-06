package com.niuma.admin.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户转账流水（幂等键：merchantId + txnId）
 */
@Data
@TableName("merchant_txn")
public class MerchantTxn {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String merchantId;

    /** 平台业务流水号 */
    private String txnId;

    /** 平台玩家ID */
    private String playerId;

    /** IN / OUT */
    private String type;

    private Long amount;

    private Long balanceAfter;

    private String status;

    /** 请求参数指纹，用于识别"同 txnId 不同参数"的冲突 */
    private String requestHash;

    private String remark;

    private LocalDateTime createTime;
}
