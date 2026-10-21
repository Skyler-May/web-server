package com.niuma.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niuma.admin.dto.*;
import com.niuma.admin.entity.Capital;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.page.PageBody;
import com.niuma.common.page.PageResult;

/**
 * 玩家资产相关服务接口
 * @author wujian
 * @email 393817707@qq.com
 * @date 2024.11.03
 */
public interface ICapitalService extends IService<Capital> {
    AjaxResult getCapital();

    Capital getCapital(String playerId);

    /**
     * 把「外部平台玩家标识」解析为 NiuMa 内部玩家 ID
     *
     * 支持两种入参：
     *  · 已是内部 ID（10 位随机码）→ 原样返回
     *  · 平台侧玩家名（player.name）→ 查表得到内部 ID
     *
     * @param nameOrId 平台玩家标识或内部 ID
     * @return 内部玩家 ID；不存在返回 null
     */
    String resolvePlayerId(String nameOrId);

    /**
     * 厂商模式资金划转（不依赖「当前登录玩家」上下文）
     *
     * 供聚合平台服务端调用：把玩家在 NiuMa 的游戏金币（capital.gold）
     * 与平台中心钱包之间划转。
     *
     * @param nameOrId 平台玩家标识或内部 ID
     * @param amount   划转数量（正整数，单位同金币）
     * @param isIn     true=平台转入 NiuMa(加金币)；false=NiuMa 转出到平台(扣金币)
     * @return true=成功；false=并发冲突
     * @throws IllegalStateException 玩家不存在或余额不足（消息可直接展示）
     */
    boolean vendorTransfer(String nameOrId, Long amount, boolean isIn) throws IllegalStateException;

    AjaxResult bankPassword(BankPwdDTO dto);

    /**
     * 从银行取出或向银行存入
     * @param dto 请求体
     * @param isDebit true-取出，false-存入
     */
    AjaxResult debitOrDeposit(AmountDTO dto, boolean isDebit);

    AjaxResult getAccount();

    AjaxResult bindAccount(BindAccountDTO dto);

    AjaxResult exchange(ExchangeDTO dto);

    PageResult<ExchangeRecordDTO> exchangeRecord(PageBody dto);

    AjaxResult transfer(TransferDTO dto);

    PageResult<TransferRecordDTO> transferRecord(PageBody dto);

    AjaxResult transferAcc();

    AjaxResult buyDiamond(Integer index);
}
