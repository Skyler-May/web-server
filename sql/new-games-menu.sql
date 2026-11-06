-- 新游戏（网狐移植）后台菜单
-- 斗地主 1031 / 跑得快 1032 / 经典牛牛 1024 / 浙江麻将 1033
-- 依赖：sys_menu 表（RuoYi 管理端）

INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
    ('斗地主',   1, 7,  'landlord',        'niuma/landlord/index',        1, 0, 'C', '0', '0', 'niuma:landlord',        'education', 'admin', NOW(), '斗地主房间管理'),
    ('跑得快',   1, 8,  'paodekuai',       'niuma/paodekuai/index',       1, 0, 'C', '0', '0', 'niuma:paodekuai',       'education', 'admin', NOW(), '跑得快房间管理'),
    ('经典牛牛', 1, 9,  'niuniu',          'niuma/niuniu/index',          1, 0, 'C', '0', '0', 'niuma:niuniu',          'education', 'admin', NOW(), '经典牛牛房间管理'),
    ('浙江麻将', 1, 10, 'zhejiangmahjong', 'niuma/zhejiangmahjong/index', 1, 0, 'C', '0', '0', 'niuma:zhejiangmahjong', 'education', 'admin', NOW(), '浙江麻将房间管理');
