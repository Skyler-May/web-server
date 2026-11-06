package com.niuma.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuma.admin.dto.RoomItemDTO;
import com.niuma.admin.entity.GameZhejiangMahjong;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameZhejiangMahjongMapper extends BaseMapper<GameZhejiangMahjong> {
    Integer hasNumber(@Param("number") String number);
    String getIdByNumber(@Param("number") String number);
    String getNumber(@Param("venueId") String venueId);

    /** open rooms of this game (lobby room list) */
    List<RoomItemDTO> getPublicRooms();
}