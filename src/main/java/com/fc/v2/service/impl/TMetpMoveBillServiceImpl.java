package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TMetpMoveBillMapper;
import com.fc.v2.model.auto.TMetpMoveBill;
import com.fc.v2.service.ITMetpMoveBillService;

/**
 * 台站迁移变更逐级核准单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpMoveBillServiceImpl implements ITMetpMoveBillService {

    /** 当前所在层 0..3，末层签讫即核讫 */
    private static final int MAX_NODE = 3;
    /** 同层核准方式：0任一人 1两名点齐 */
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    /** 报批情形：0在核 1已核讫 2已打回 */
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TMetpMoveBillMapper metpMoveBillMapper;

    @Override
    public TMetpMoveBill selectTMetpMoveBillById(Long id) {
        return this.metpMoveBillMapper.selectById(id);
    }

    /** 只有在核的单子才受理签批/否决 */
    private static boolean isRunning(TMetpMoveBill r) {
        return r.getStatus() == null || r.getStatus().intValue() == STATUS_RUNNING;
    }

    /** 本层应签人数：任一人模式 1 票；两名点齐模式取 need_count（未维护按 2 计） */
    private static int needOf(TMetpMoveBill r) {
        if (r.getSignMode() != null && r.getSignMode().intValue() == MODE_AND) {
            return r.getNeedCount() != null && r.getNeedCount().intValue() > 0
                    ? r.getNeedCount().intValue() : 2;
        }
        return 1;
    }

    @Override
    public TMetpMoveBill approve(Long id, String approver, String comment) {
        TMetpMoveBill r = this.metpMoveBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty() || !isRunning(r)) {
            return null;
        }
        int signed = (r.getSignCount() == null ? 0 : r.getSignCount().intValue()) + 1;
        r.setSignCount(Integer.valueOf(signed));
        if (signed >= needOf(r)) {
            // 本层点齐：末层核讫，否则升入下一层重新计签
            int node = r.getNodeNo() == null ? 0 : r.getNodeNo().intValue();
            if (node >= MAX_NODE) {
                r.setStatus(Integer.valueOf(STATUS_PASS));
            } else {
                r.setNodeNo(Integer.valueOf(node + 1));
                r.setSignCount(Integer.valueOf(0));
                r.setStatus(Integer.valueOf(STATUS_RUNNING));
            }
        }
        this.metpMoveBillMapper.updateById(r);
        return r;
    }

    @Override
    public TMetpMoveBill reject(Long id, String approver, String comment) {
        TMetpMoveBill r = this.metpMoveBillMapper.selectById(id);
        if (r == null || !isRunning(r)) {
            return null;
        }
        r.setStatus(Integer.valueOf(STATUS_VETO));
        this.metpMoveBillMapper.updateById(r);
        return r;
    }

    @Override
    public TMetpMoveBill rollback(Long id, String comment) {
        TMetpMoveBill r = this.metpMoveBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo().intValue();
        // 退回上一环节：与签批同一把尺，一次只退一层，回到在核重新计签
        r.setNodeNo(Integer.valueOf(Math.max(0, node - 1)));
        r.setSignCount(Integer.valueOf(0));
        r.setStatus(Integer.valueOf(STATUS_RUNNING));
        this.metpMoveBillMapper.updateById(r);
        return r;
    }
}
