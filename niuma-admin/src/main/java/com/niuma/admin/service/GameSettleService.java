package com.niuma.admin.service;

import com.niuma.admin.data.CapitalAmount;
import com.niuma.admin.entity.CapitalLog;
import com.niuma.admin.entity.GameSettleOutbox;
import com.niuma.admin.mapper.CapitalLogMapper;
import com.niuma.admin.mapper.CapitalMapper;
import com.niuma.admin.mapper.GameSettleOutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 牌局结算消费者：把 game_settle_outbox 里的 pending 记录结算到玩家 capital.gold。
 *
 * 幂等：outbox 行以 (venue_id, round_no, player_id) 唯一；只有结算成功才置 settled，
 * 且更新语句带 status='pending' 条件，重复消费不会重复入账。
 */
@Service
public class GameSettleService {
    private static final Logger log = LoggerFactory.getLogger(GameSettleService.class);

    /** 每批处理条数 */
    private static final int BATCH = 50;

    /** 乐观锁重试次数 */
    private static final int RETRY = 3;

    @Resource
    private GameSettleOutboxMapper outboxMapper;

    @Resource
    private CapitalMapper capitalMapper;

    @Resource
    private CapitalLogMapper capitalLogMapper;

    @Scheduled(fixedDelay = 3000)
    public void settlePending() {
        List<GameSettleOutbox> list;
        try {
            list = outboxMapper.selectPending(BATCH);
        } catch (Exception ex) {
            log.warn("读取待结算记录失败: {}", ex.getMessage());
            return;
        }
        if (list == null || list.isEmpty())
            return;

        for (GameSettleOutbox row : list) {
            try {
                settleOne(row);
            } catch (Exception ex) {
                log.error("结算失败 id={} player={} delta={} err={}",
                        row.getId(), row.getPlayerId(), row.getDelta(), ex.getMessage());
                outboxMapper.markFailed(row.getId(), safe(ex.getMessage()));
            }
        }
    }

    private void settleOne(GameSettleOutbox row) {
        if (row.getDelta() == null || row.getDelta() == 0) {
            outboxMapper.markSettled(row.getId());
            return;
        }
        for (int attempt = 0; attempt < RETRY; attempt++) {
            CapitalAmount cap = capitalMapper.getCapital(row.getPlayerId());
            if (cap == null) {
                outboxMapper.markFailed(row.getId(), "capital not found");
                return;
            }
            long version = cap.getVersion() == null ? 0L : cap.getVersion();
            long gold = cap.getGold() == null ? 0L : cap.getGold();
            long target = gold + row.getDelta();
            if (target < 0) {
                log.warn("玩家 {} 余额不足: gold={} delta={}，按 0 结算", row.getPlayerId(), gold, row.getDelta());
                target = 0;
            }
            int updated = capitalMapper.setCapital(row.getPlayerId(), target, null, null, version);
            if (updated <= 0)
                continue;   // 乐观锁冲突，重试

            CapitalLog logRow = new CapitalLog();
            logRow.setPlayerId(row.getPlayerId());
            logRow.setChangeAmount(target - gold);
            logRow.setBalanceAfter(target);
            logRow.setType("game_settle");
            logRow.setRefKey(row.getVenueId() + ":" + row.getRoundNo());
            capitalLogMapper.insert(logRow);

            outboxMapper.markSettled(row.getId());
            log.info("结算成功 player={} delta={} balance={} ref={}:{}",
                    row.getPlayerId(), row.getDelta(), target, row.getVenueId(), row.getRoundNo());
            return;
        }
        outboxMapper.markFailed(row.getId(), "optimistic lock retry exhausted");
    }

    private String safe(String s) {
        if (s == null)
            return "";
        return s.length() > 250 ? s.substring(0, 250) : s;
    }
}