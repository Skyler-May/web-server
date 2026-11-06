package com.niuma.admin.merchant.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聚合商户（一个商户 = 一个聚合平台）
 * 只服务于"聚合接入"模式，自营玩家的流程完全不受影响
 */
@Data
@TableName("merchant")
public class Merchant {
    /** 商户号 */
    @TableId
    private String id;

    /** 商户名称 */
    private String name;

    /** 当前密钥 */
    private String secretKey;

    /** 轮换期的旧密钥（并行验签用） */
    private String secretKeyPrev;

    /** 旧密钥失效时间 */
    private LocalDateTime prevExpireAt;

    /** 环境 test/prod */
    private String env;

    /** IP 白名单，逗号分隔，空=不限 */
    private String ipWhitelist;

    /** 启动地址前缀 */
    private String launchBaseUrl;

    /** 启动参数名 userData/token */
    private String launchParam;

    /** 1启用 0停用 */
    private Integer enabled;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
