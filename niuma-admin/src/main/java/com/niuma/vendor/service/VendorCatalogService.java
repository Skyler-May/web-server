package com.niuma.vendor.service;

import com.niuma.admin.constant.NiuMaConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 厂商游戏目录（标准厂商 API 的 catalog 实现）
 *
 * <p>对外暴露的是<b>平铺式游戏列表</b>：一款游戏一个条目，
 * 聚合平台拿到后在自家大厅直接平铺显示，玩家点某个游戏即进入该游戏的
 * 选场次页（跳过 NiuMa 总大厅）。
 *
 * <p>gameType 取值来源为 NiuMa 既有常量 {@link NiuMaConstants}，
 * 本类不新增游戏，只做"对外映射"，避免与 C++ 侧实际支持的游戏脱节。
 */
@Component
public class VendorCatalogService {

    /**
     * 可对外提供的游戏（平铺）
     *
     * <p>说明：仅列出 C++ 服务端已实现且客户端有资源目录的游戏。
     * 如需下架某款游戏，从本列表移除即可（不影响原有 NiuMa 大厅）。
     */
    public List<GameItem> listGames() {
        List<GameItem> list = new ArrayList<>();
        list.add(new GameItem("MAHJONG", NiuMaConstants.GAME_TYPE_MAHJONG, "麻将", "mahjong", 10));
        list.add(new GameItem("DOUDIZHU", NiuMaConstants.GAME_TYPE_DOU_DI_ZHU, "斗地主", "doudizhu", 20));
        list.add(new GameItem("NIUNIU100", NiuMaConstants.GAME_TYPE_NIU_NIU_100, "百人牛牛", "niuniu100", 30));
        list.add(new GameItem("NIUNIU", NiuMaConstants.GAME_TYPE_NIU_NIU, "牛牛", "niuniu", 40));
        list.add(new GameItem("REDBLACK", NiuMaConstants.GAME_TYPE_RED_BLACK, "红黑大战", "redblack", 50));
        list.add(new GameItem("ZHAJINHUA", NiuMaConstants.GAME_TYPE_ZHA_JIN_HUA, "炸金花", "zhajinhua", 60));
        list.add(new GameItem("BIJI", NiuMaConstants.GAME_TYPE_BI_JI, "比鸡", "biji", 70));
        list.add(new GameItem("LACKEY", NiuMaConstants.GAME_TYPE_LACKEY, "跑得快", "lackey", 80));
        list.add(new GameItem("TRACTOR", NiuMaConstants.GAME_TYPE_TRACTOR, "拖拉机", "tractor", 90));
        list.add(new GameItem("GUANDAN", NiuMaConstants.GAME_TYPE_GUAN_DAN, "掼蛋", "guandan", 100));
        return list;
    }

    /**
     * 按 gameCode 查游戏；找不到返回 null
     */
    public GameItem findByCode(String gameCode) {
        if (gameCode == null) {
            return null;
        }
        for (GameItem item : listGames()) {
            if (item.getGameCode().equalsIgnoreCase(gameCode)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 按 gameType 查游戏；找不到返回 null
     */
    public GameItem findByGameType(Integer gameType) {
        if (gameType == null) {
            return null;
        }
        for (GameItem item : listGames()) {
            if (gameType.equals(item.getGameType())) {
                return item;
            }
        }
        return null;
    }

    /**
     * 对外游戏条目
     */
    public static class GameItem {

        /** 平台内统一游戏编码（聚合平台用于自己的 gameId 映射） */
        private final String gameCode;

        /** NiuMa 内部游戏类型（进房时传给 /player/game/create 或 /enter） */
        private final Integer gameType;

        /** 显示名称 */
        private final String name;

        /** 客户端资源标识（H5/Unity 端路由用） */
        private final String resourceKey;

        /** 排序（越小越前） */
        private final Integer sort;

        public GameItem(String gameCode, Integer gameType, String name, String resourceKey, Integer sort) {
            this.gameCode = gameCode;
            this.gameType = gameType;
            this.name = name;
            this.resourceKey = resourceKey;
            this.sort = sort;
        }

        public String getGameCode() {
            return gameCode;
        }

        public Integer getGameType() {
            return gameType;
        }

        public String getName() {
            return name;
        }

        public String getResourceKey() {
            return resourceKey;
        }

        public Integer getSort() {
            return sort;
        }
    }
}
