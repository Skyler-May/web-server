package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("game_zhejiang_mahjong")
public class GameZhejiangMahjong {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String venueId;
    private String number;
    private Integer baseScore;
}