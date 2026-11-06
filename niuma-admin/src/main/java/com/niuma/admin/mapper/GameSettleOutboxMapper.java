package com.niuma.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuma.admin.entity.GameSettleOutbox;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameSettleOutboxMapper extends BaseMapper<GameSettleOutbox> {
    List<GameSettleOutbox> selectPending(@Param("limit") int limit);

    int markSettled(@Param("id") Long id);

    int markFailed(@Param("id") Long id, @Param("remark") String remark);
}