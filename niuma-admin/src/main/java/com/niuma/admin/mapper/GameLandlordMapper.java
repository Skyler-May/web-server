package com.niuma.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuma.admin.entity.GameLandlord;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GameLandlordMapper extends BaseMapper<GameLandlord> {
    /** whether the number already exists */
    Integer hasNumber(@Param("number") String number);

    /** query venue id by room number */
    String getIdByNumber(@Param("number") String number);

    /** query room number by venue id */
    String getNumber(@Param("venueId") String venueId);
}