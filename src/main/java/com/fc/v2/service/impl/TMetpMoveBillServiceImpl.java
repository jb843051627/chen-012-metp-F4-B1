package com.fc.v2.service.impl;

import java.util.Date;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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

    /** 层级上限：schema 契约 当前所在层 0..3 */
    private static final int MAX_NODE = 3;
    /** 同层核准方式：0任一人 */
    private static final int MODE_OR = 0;
    /** 同层核准方式：1两名点齐 */
    private static final int MODE_AND = 1;
    /** 报批情形：0在核 */
    private static final int STATUS_RUNNING = 0;
    /** 报批情形：1已核讫 */
    private static final int STATUS_PASS = 1;
    /** 报批情形：2已打回 */
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TMetpMoveBillMapper metpMoveBillMapper;

    @Override
    public TMetpMoveBill selectTMetpMoveBillById(Long id) {
        return this.metpMoveBillMapper.selectById(id);
    }

    /** 可空列的等值把守：NULL 与 0 是两般值，须各按各的口径拼条件 */
    private static void eqNullable(UpdateWrapper<TMetpMoveBill> w, String column, Integer val) {
        if (val == null) {
            w.isNull(column);
        } else {
            w.eq(column, val);
        }
    }

    @Override
    public TMetpMoveBill approve(Long id, String approver, String comment) {
        if (id == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        TMetpMoveBill r = this.metpMoveBillMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() != 0)) {
            return null;
        }
        int status = r.getStatus() == null ? STATUS_RUNNING : r.getStatus();
        if (status != STATUS_RUNNING) {
            return null;
        }
        Integer node0 = r.getNodeNo();
        int node = node0 == null ? 0 : node0;

        // 先原子累一票：同一刻两双手下手也不丢票
        UpdateWrapper<TMetpMoveBill> vote = new UpdateWrapper<TMetpMoveBill>()
                .eq("id", r.getId())
                .eq("del_flag", 0)
                .setSql("sign_count = COALESCE(sign_count, 0) + 1")
                .set("update_time", new Date());
        eqNullable(vote, "node_no", node0);
        eqNullable(vote, "status", r.getStatus());
        if (this.metpMoveBillMapper.update(null, vote) == 0) {
            return null;
        }

        r = this.metpMoveBillMapper.selectById(id);
        int nodeNow = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (nodeNow != node) {
            // 层已被别家推进，我这票随之结清
            return r;
        }
        int signed = r.getSignCount() == null ? 0 : r.getSignCount();
        int need = r.getNeedCount() == null || r.getNeedCount() < 1 ? 1 : r.getNeedCount();
        // 任一人即过；两名点齐须签够应签人数
        boolean layerPass = (r.getSignMode() != null && r.getSignMode() == MODE_AND)
                ? signed >= need : true;
        if (!layerPass) {
            return r;
        }

        // 本层签齐：推进一层；已到顶层则全单核讫。把守"仍是我投票时的那层"，只许一家推进
        UpdateWrapper<TMetpMoveBill> uw = new UpdateWrapper<TMetpMoveBill>()
                .eq("id", r.getId())
                .eq("del_flag", 0)
                .set("sign_count", 0)
                .set("update_time", new Date());
        eqNullable(uw, "node_no", node0);
        eqNullable(uw, "status", r.getStatus());
        if (node >= MAX_NODE) {
            uw.set("status", STATUS_PASS);
        } else {
            uw.set("node_no", node + 1);
        }
        this.metpMoveBillMapper.update(null, uw);
        return this.metpMoveBillMapper.selectById(id);
    }

    @Override
    public TMetpMoveBill reject(Long id, String approver, String comment) {
        if (id == null) {
            return null;
        }
        TMetpMoveBill r = this.metpMoveBillMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() != 0)) {
            return null;
        }
        int status = r.getStatus() == null ? STATUS_RUNNING : r.getStatus();
        if (status != STATUS_RUNNING) {
            return null;
        }
        // 否决即打回：只有在核的单可否决
        UpdateWrapper<TMetpMoveBill> uw = new UpdateWrapper<TMetpMoveBill>()
                .eq("id", r.getId())
                .eq("del_flag", 0)
                .set("status", STATUS_VETO)
                .set("update_time", new Date());
        eqNullable(uw, "status", r.getStatus());
        if (this.metpMoveBillMapper.update(null, uw) == 0) {
            return null;
        }
        return this.metpMoveBillMapper.selectById(id);
    }

    @Override
    public TMetpMoveBill rollback(Long id, String comment) {
        if (id == null) {
            return null;
        }
        TMetpMoveBill r = this.metpMoveBillMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() != 0)) {
            return null;
        }
        int status = r.getStatus() == null ? STATUS_RUNNING : r.getStatus();
        if (status != STATUS_RUNNING) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (node <= 0) {
            // 已在头一层，退回被拒
            return null;
        }
        // 退回上一环节：层号退一，本层已签清零；条件把守只许一家退
        UpdateWrapper<TMetpMoveBill> uw = new UpdateWrapper<TMetpMoveBill>()
                .eq("id", r.getId())
                .eq("del_flag", 0)
                .set("node_no", node - 1)
                .set("sign_count", 0)
                .set("update_time", new Date());
        eqNullable(uw, "node_no", r.getNodeNo());
        eqNullable(uw, "status", r.getStatus());
        if (this.metpMoveBillMapper.update(null, uw) == 0) {
            return null;
        }
        return this.metpMoveBillMapper.selectById(id);
    }
}
