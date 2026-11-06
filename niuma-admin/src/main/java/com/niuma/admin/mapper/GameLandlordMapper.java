package com.niuma.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuma.admin.dto.GameRoomDTO;
import com.niuma.admin.dto.RoomItemDTO;
import com.niuma.admin.entity.GameLandlord;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameLandlordMapper extends BaseMapper<GameLandlord> {
    /** whether the number already exists */
    Integer hasNumber(@Param("number") String number);

    /** query venue id by room number */
    String getIdByNumber(@Param("number") String number);

    /** query room number by venue id */
    String getNumber(@Param("venueId") String venueId);

    /** open rooms of this game (lobby room list) */
    List<RoomItemDTO> getPublicRooms();

    /** 房间数量（后台分页） */
    Integer countRoom(@Param("venueId") String venueId, @Param("ownerId") String ownerId, @Param("number") String number,
                      @Param("startTime") String startTime, @Param("endTime") String endTime);

    /** 房间列表（后台分页） */
    List<GameRoomDTO> getRooms(@Param("venueId") String venueId, @Param("ownerId") String ownerId, @Param("number") String number,
                               @Param("startTime") String startTime, @Param("endTime") String endTime,
                               @Param("offset") Integer offset, @Param("pageSize") Integer pageSize);
}