package com.niuma.admin.merchant.admin;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.niuma.admin.merchant.entity.Merchant;
import com.niuma.admin.merchant.entity.MerchantPlayer;
import com.niuma.admin.merchant.entity.MerchantTxn;
import com.niuma.admin.merchant.mapper.MerchantMapper;
import com.niuma.admin.merchant.mapper.MerchantPlayerMapper;
import com.niuma.admin.merchant.mapper.MerchantTxnMapper;
import com.niuma.common.utils.StringUtils;
import com.niuma.common.utils.uuid.IdUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台「商户管理」服务：新建 / 修改 / 启停 / 重置密钥 / 查询
 *
 * 说明：项目没有配置 MyBatis-Plus 分页插件，这里用 count + limit 手工分页，
 * 避免为了一个后台页面去改动全局分页行为。
 *
 * 密钥只在下发时明文返回一次，列表与详情一律掩码显示。
 */
@Service
public class MerchantAdminService {

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private MerchantPlayerMapper merchantPlayerMapper;

    @Autowired
    private MerchantTxnMapper merchantTxnMapper;

    /** 分页查询 */
    public Map<String, Object> page(int pageNum, int pageSize, String id, String name, Integer enabled) {
        int page = Math.max(pageNum, 1);
        int size = pageSize < 1 || pageSize > 200 ? 10 : pageSize;

        LambdaQueryWrapper<Merchant> count = Wrappers.<Merchant>lambdaQuery()
                .like(StringUtils.isNotEmpty(id), Merchant::getId, id)
                .like(StringUtils.isNotEmpty(name), Merchant::getName, name)
                .eq(enabled != null, Merchant::getEnabled, enabled);
        Integer total = merchantMapper.selectCount(count);

        LambdaQueryWrapper<Merchant> list = Wrappers.<Merchant>lambdaQuery()
                .like(StringUtils.isNotEmpty(id), Merchant::getId, id)
                .like(StringUtils.isNotEmpty(name), Merchant::getName, name)
                .eq(enabled != null, Merchant::getEnabled, enabled)
                .orderByDesc(Merchant::getCreateTime)
                .last("limit " + ((page - 1) * size) + "," + size);
        List<Merchant> rows = merchantMapper.selectList(list);

        List<Map<String, Object>> records = new ArrayList<>();
        for (Merchant m : rows) {
            records.add(toVo(m, true));
        }
        return paged(records, page, total == null ? 0 : total);
    }

    /** 详情 */
    public Map<String, Object> detail(String id) {
        Merchant m = merchantMapper.selectById(id);
        return m == null ? null : toVo(m, true);
    }

    /** 新建商户，返回含明文密钥的对象（仅此一次） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(JSONObject req) {
        String name = req.getString("name");
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("商户名称不能为空");
        }
        Integer exists = merchantMapper.selectCount(Wrappers.<Merchant>lambdaQuery().eq(Merchant::getName, name));
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("商户名称已存在");
        }
        Merchant m = new Merchant();
        m.setId("m" + IdUtils.fastSimpleUUID().substring(0, 10));
        m.setName(name);
        m.setSecretKey(newSecret());
        m.setEnv(StringUtils.isNotEmpty(req.getString("env")) ? req.getString("env") : "prod");
        m.setIpWhitelist(req.getString("ipWhitelist"));
        m.setLaunchBaseUrl(req.getString("launchBaseUrl"));
        m.setLaunchParam(StringUtils.isNotEmpty(req.getString("launchParam")) ? req.getString("launchParam") : "userData");
        m.setEnabled(req.getIntValue("enabled", 1));
        m.setRemark(req.getString("remark"));
        m.setCreateTime(LocalDateTime.now());
        m.setUpdateTime(LocalDateTime.now());
        merchantMapper.insert(m);

        Map<String, Object> vo = toVo(m, false);
        // 明文密钥只在这里出现一次
        vo.put("secretKey", m.getSecretKey());
        return vo;
    }

    /** 修改（不含密钥） */
    public Map<String, Object> update(JSONObject req) {
        String id = req.getString("id");
        Merchant m = merchantMapper.selectById(id);
        if (m == null) {
            throw new IllegalArgumentException("商户不存在");
        }
        if (StringUtils.isNotEmpty(req.getString("name"))) {
            m.setName(req.getString("name"));
        }
        if (StringUtils.isNotEmpty(req.getString("env"))) {
            m.setEnv(req.getString("env"));
        }
        if (req.containsKey("ipWhitelist")) {
            m.setIpWhitelist(req.getString("ipWhitelist"));
        }
        if (req.containsKey("launchBaseUrl")) {
            m.setLaunchBaseUrl(req.getString("launchBaseUrl"));
        }
        if (StringUtils.isNotEmpty(req.getString("launchParam"))) {
            m.setLaunchParam(req.getString("launchParam"));
        }
        if (req.containsKey("remark")) {
            m.setRemark(req.getString("remark"));
        }
        if (req.containsKey("enabled")) {
            m.setEnabled(req.getIntValue("enabled", 1));
        }
        m.setUpdateTime(LocalDateTime.now());
        merchantMapper.updateById(m);
        return toVo(m, true);
    }

    /** 启停 */
    public Map<String, Object> enable(String id, int enabled) {
        Merchant m = merchantMapper.selectById(id);
        if (m == null) {
            throw new IllegalArgumentException("商户不存在");
        }
        m.setEnabled(enabled);
        m.setUpdateTime(LocalDateTime.now());
        merchantMapper.updateById(m);
        return toVo(m, true);
    }

    /**
     * 重置密钥：旧密钥保留 prevDays 天（默认 7），期间新旧都能验签
     */
    public Map<String, Object> resetKey(String id, Integer prevDays) {
        Merchant m = merchantMapper.selectById(id);
        if (m == null) {
            throw new IllegalArgumentException("商户不存在");
        }
        int days = prevDays == null || prevDays <= 0 ? 7 : prevDays;
        m.setSecretKeyPrev(m.getSecretKey());
        m.setPrevExpireAt(LocalDateTime.now().plusDays(days));
        m.setSecretKey(newSecret());
        m.setUpdateTime(LocalDateTime.now());
        merchantMapper.updateById(m);

        Map<String, Object> vo = toVo(m, true);
        vo.put("secretKey", m.getSecretKey());
        return vo;
    }

    /** 商户下的玩家（分页） */
    public Map<String, Object> players(String merchantId, int pageNum, int pageSize, String playerId) {
        int page = Math.max(pageNum, 1);
        int size = pageSize < 1 || pageSize > 200 ? 10 : pageSize;

        Integer total = merchantPlayerMapper.selectCount(Wrappers.<MerchantPlayer>lambdaQuery()
                .eq(MerchantPlayer::getMerchantId, merchantId)
                .like(StringUtils.isNotEmpty(playerId), MerchantPlayer::getPlayerId, playerId));
        List<MerchantPlayer> rows = merchantPlayerMapper.selectList(Wrappers.<MerchantPlayer>lambdaQuery()
                .eq(MerchantPlayer::getMerchantId, merchantId)
                .like(StringUtils.isNotEmpty(playerId), MerchantPlayer::getPlayerId, playerId)
                .orderByDesc(MerchantPlayer::getId)
                .last("limit " + ((page - 1) * size) + "," + size));
        return paged(rows, page, total == null ? 0 : total);
    }

    /** 商户流水（分页），对账用 */
    public Map<String, Object> txns(String merchantId, int pageNum, int pageSize, String playerId, String type) {
        int page = Math.max(pageNum, 1);
        int size = pageSize < 1 || pageSize > 200 ? 10 : pageSize;

        Integer total = merchantTxnMapper.selectCount(Wrappers.<MerchantTxn>lambdaQuery()
                .eq(MerchantTxn::getMerchantId, merchantId)
                .eq(StringUtils.isNotEmpty(type), MerchantTxn::getType, type)
                .eq(StringUtils.isNotEmpty(playerId), MerchantTxn::getPlayerId, playerId));
        List<MerchantTxn> rows = merchantTxnMapper.selectList(Wrappers.<MerchantTxn>lambdaQuery()
                .eq(MerchantTxn::getMerchantId, merchantId)
                .eq(StringUtils.isNotEmpty(type), MerchantTxn::getType, type)
                .eq(StringUtils.isNotEmpty(playerId), MerchantTxn::getPlayerId, playerId)
                .orderByDesc(MerchantTxn::getId)
                .last("limit " + ((page - 1) * size) + "," + size));
        return paged(rows, page, total == null ? 0 : total);
    }

    // ==========================================================

    private Map<String, Object> paged(List<?> records, int pageNum, int total) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", "200");
        data.put("msg", "success");
        data.put("pageNum", pageNum);
        data.put("total", total);
        data.put("records", records);
        return data;
    }

    private Map<String, Object> toVo(Merchant m, boolean mask) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", m.getId());
        vo.put("name", m.getName());
        vo.put("env", m.getEnv());
        vo.put("ipWhitelist", m.getIpWhitelist());
        vo.put("launchBaseUrl", m.getLaunchBaseUrl());
        vo.put("launchParam", m.getLaunchParam());
        vo.put("enabled", m.getEnabled());
        vo.put("remark", m.getRemark());
        vo.put("createTime", m.getCreateTime());
        vo.put("updateTime", m.getUpdateTime());
        vo.put("prevExpireAt", m.getPrevExpireAt());
        vo.put("secretKey", mask ? maskSecret(m.getSecretKey()) : null);
        vo.put("secretKeyPrevSet", StringUtils.isNotEmpty(m.getSecretKeyPrev()));
        return vo;
    }

    private String maskSecret(String secret) {
        if (StringUtils.isEmpty(secret) || secret.length() < 12) {
            return "****";
        }
        return secret.substring(0, 4) + "********" + secret.substring(secret.length() - 4);
    }

    private String newSecret() {
        return IdUtils.fastSimpleUUID() + IdUtils.fastSimpleUUID();
    }
}
