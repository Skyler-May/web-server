-- 后台「商户管理」菜单 + 权限（聚合平台接入）
-- 挂在顶级菜单（parent_id = 1），与「玩家管理/麻将房间」等并列

INSERT INTO `sys_menu`
(`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
 `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '商户管理', 1, 6, 'merchant', 'niuma/merchant/index', '', 1, 0,
       'C', '0', '0', 'niuma:merchant', 'money', 'admin', sysdate(), '', NULL, '聚合商户接入管理'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` m WHERE m.`perms` = 'niuma:merchant');

-- 给超级管理员角色授权（role_id = 1）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, m.`menu_id` FROM `sys_menu` m
WHERE m.`perms` = 'niuma:merchant'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = m.`menu_id`);
