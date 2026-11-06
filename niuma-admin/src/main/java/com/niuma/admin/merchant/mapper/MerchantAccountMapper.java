package com.niuma.admin.merchant.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

/**
 * 商户玩家的账户操作。
 *
 * 注意：不能用 playerMapper.updateById(entity) —— 实体会触发 RuoYi 的自动填充
 * （MyMetaObjectHandler 里取当前后台登录用户），而商户接口是匿名调用，
 * 会抛 ServiceException「获取用户账户异常」。
 * 这里用参数化的原生 UPDATE，绕开自动填充。
 */
@Repository
public interface MerchantAccountMapper {

    @Update("UPDATE player SET password = #{password} WHERE id = #{id}")
    int updatePassword(@Param("id") String id, @Param("password") String password);
}
