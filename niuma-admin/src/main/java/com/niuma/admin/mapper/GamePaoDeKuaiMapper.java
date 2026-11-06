package com.niuma.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuma.admin.entity.GamePaoDeKuai;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GamePaoDeKuaiMapper extends BaseMapper<GamePaoDeKuai> {
    Integer hasNumber(@Param("number") String number);

    String getIdByNumber(@Param("number") String number);

    String getNumber(@Param("venueId") String venueId);
}