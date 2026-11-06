package com.niuma.admin.merchant.service;

import com.alibaba.fastjson2.JSONObject;
import com.niuma.admin.merchant.entity.Merchant;

import java.util.Map;

/**
 * 聚合商户接入服务（转账钱包）
 */
public interface IMerchantService {

    /**
     * 校验商户身份（开关 / 存在 / 启用 / IP 白名单 / 时间窗 / 签名）
     *
     * @param merchantId 商户号
     * @param timestamp  秒级时间戳
     * @param nonce      随机串
     * @param rawBody    请求原始 body
     * @param clientIp   调用方 IP
     * @return 已校验通过的商户
     */
    Merchant verify(String merchantId, String timestamp, String nonce, String rawBody, String clientIp);

    /** 校验请求签名（支持新旧密钥并行） */
    boolean checkSign(Merchant merchant, String timestamp, String nonce, String rawBody, String sign);

    /** 登录并获取启动地址 */
    Map<String, Object> login(Merchant merchant, JSONObject req);

    /** 上分 / 下分 */
    Map<String, Object> transfer(Merchant merchant, JSONObject req);

    /** 余额查询 */
    Map<String, Object> balance(Merchant merchant, JSONObject req);

    /** 强制下线（撤销登录态） */
    Map<String, Object> kick(Merchant merchant, JSONObject req);

    /** 注单查询（麻将） */
    Map<String, Object> rounds(Merchant merchant, JSONObject req);
}
