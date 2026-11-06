package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * PaoDeKuai game table entity
 */
@Data
@TableName("game_pao_de_kuai")
public class GamePaoDeKuai {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** venue id */
    private String venueId;

    /** 6-digit room number */
    private String number;

    /** base score */
    private Integer diZhu;
}