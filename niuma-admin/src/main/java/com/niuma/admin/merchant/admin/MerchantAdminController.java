package com.niuma.admin.merchant.admin;

import com.alibaba.fastjson2.JSONObject;
import com.niuma.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台「商户管理」接口（聚合平台接入用）
 * 权限标识：niuma:merchant
 */
@RestController
@RequestMapping("/admin/merchant")
public class MerchantAdminController {

    @Autowired
    private MerchantAdminService merchantAdminService;

    @PostMapping("/page")
    @PreAuthorize("@ss.hasPermi('niuma:merchant')")
    public Map<String, Object> page(@RequestBody(required = false) JSONObject req) {
        JSONObject body = req == null ? new JSONObject() : req;
        return merchantAdminService.page(body.getIntValue("pageNum", 1), body.getIntValue("pageSize", 10),
                body.getString("id"), body.getString("name"), body.getInteger("enabled"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('niuma:merchant')")
    public AjaxResult detail(@PathVariable("id") String id) {
        Map<String, Object> vo = merchantAdminService.detail(id);
        if (vo == null) {
            return AjaxResult.error("商户不存在");
        }
        return AjaxResult.success(vo);
    }

    /** 新建商户：响应里带一次明文密钥 */
    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermi('niuma:merchant:add')")
    public AjaxResult create(@RequestBody JSONObject req) {
        return AjaxResult.success(merchantAdminService.create(req));
    }

    @PostMapping("/update")
    @PreAuthorize("@ss.hasPermi('niuma:merchant:edit')")
    public AjaxResult update(@RequestBody JSONObject req) {
        return AjaxResult.success(merchantAdminService.update(req));
    }

    @PostMapping("/enable/{id}/{enabled}")
    @PreAuthorize("@ss.hasPermi('niuma:merchant:edit')")
    public AjaxResult enable(@PathVariable("id") String id, @PathVariable("enabled") int enabled) {
        return AjaxResult.success(merchantAdminService.enable(id, enabled));
    }

    /** 重置密钥：响应里带一次明文新密钥，旧密钥并行 prevDays 天（默认 7） */
    @PostMapping("/reset-key/{id}")
    @PreAuthorize("@ss.hasPermi('niuma:merchant:reset')")
    public AjaxResult resetKey(@PathVariable("id") String id,
                               @RequestParam(value = "prevDays", required = false) Integer prevDays) {
        return AjaxResult.success(merchantAdminService.resetKey(id, prevDays));
    }

    @PostMapping("/{id}/players")
    @PreAuthorize("@ss.hasPermi('niuma:merchant')")
    public Map<String, Object> players(@PathVariable("id") String id, @RequestBody(required = false) JSONObject req) {
        JSONObject body = req == null ? new JSONObject() : req;
        return merchantAdminService.players(id, body.getIntValue("pageNum", 1), body.getIntValue("pageSize", 10),
                body.getString("playerId"));
    }

    @PostMapping("/{id}/txns")
    @PreAuthorize("@ss.hasPermi('niuma:merchant')")
    public Map<String, Object> txns(@PathVariable("id") String id, @RequestBody(required = false) JSONObject req) {
        JSONObject body = req == null ? new JSONObject() : req;
        return merchantAdminService.txns(id, body.getIntValue("pageNum", 1), body.getIntValue("pageSize", 10),
                body.getString("playerId"), body.getString("type"));
    }
}
