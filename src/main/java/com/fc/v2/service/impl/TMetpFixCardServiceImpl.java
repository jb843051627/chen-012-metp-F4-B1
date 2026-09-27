package com.fc.v2.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TMetpFixCardMapper;
import com.fc.v2.model.auto.TMetpFixCard;
import com.fc.v2.service.ITMetpFixCardService;

/**
 * 探测环境违规整改事务卡 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpFixCardServiceImpl implements ITMetpFixCardService {

    /** 关口上限：schema 契约 当前关口 0..5 */
    private static final int MAX_STAGE = 5;
    /** 情形：0在办 */
    private static final int STATUS_ACTIVE = 0;
    /** 情形：2已归档（办结） */
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TMetpFixCardMapper metpFixCardMapper;

    @Override
    public TMetpFixCard selectTMetpFixCardById(Long id) {
        return this.metpFixCardMapper.selectById(id);
    }

    @Override
    public List<TMetpFixCard> selectTMetpFixCardList(QueryWrapper<TMetpFixCard> queryWrapper) {
        queryWrapper.eq("del_flag", 0);
        return this.metpFixCardMapper.selectList(queryWrapper);
    }

    /** 可空列的等值把守：NULL 与 0 是两般值，须各按各的口径拼条件 */
    private static void eqNullable(UpdateWrapper<TMetpFixCard> w, String column, Integer val) {
        if (val == null) {
            w.isNull(column);
        } else {
            w.eq(column, val);
        }
    }

    @Override
    public TMetpFixCard advance(Long id, String remark) {
        if (id == null) {
            return null;
        }
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() != 0)) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        int status = r.getStatus() == null ? STATUS_ACTIVE : r.getStatus();
        if (status != STATUS_ACTIVE) {
            // 已办结（已解除/已归档），推进被拒
            return null;
        }
        // 推进一档；推到顶档即归卷
        int newStage = Math.min(st + 1, MAX_STAGE);
        int newStatus = newStage >= MAX_STAGE ? STATUS_TERMINAL : STATUS_ACTIVE;
        // 条件更新：并发挤碰或状态已变时本次推进被拒，两处收梢一个口径
        UpdateWrapper<TMetpFixCard> uw = new UpdateWrapper<TMetpFixCard>()
                .eq("id", r.getId())
                .eq("del_flag", 0)
                .set("stage", newStage)
                .set("status", newStatus)
                .set("last_action", remark)
                .set("update_time", new Date());
        eqNullable(uw, "stage", r.getStage());
        eqNullable(uw, "status", r.getStatus());
        int rows = this.metpFixCardMapper.update(null, uw);
        if (rows == 0) {
            return null;
        }
        return this.metpFixCardMapper.selectById(id);
    }

    @Override
    public TMetpFixCard rollback(Long id, String remark) {
        if (id == null) {
            return null;
        }
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() != 0)) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        int status = r.getStatus() == null ? STATUS_ACTIVE : r.getStatus();
        if (status != STATUS_TERMINAL && st <= 0) {
            // 在办且已退到起档，退卷被拒
            return null;
        }
        // 退卷与归卷同一把尺：也只挪一档；已归档的退卷即起回在办
        int newStage = Math.max(st - 1, 0);
        UpdateWrapper<TMetpFixCard> uw = new UpdateWrapper<TMetpFixCard>()
                .eq("id", r.getId())
                .eq("del_flag", 0)
                .set("stage", newStage)
                .set("status", STATUS_ACTIVE)
                .set("last_action", remark)
                .set("update_time", new Date());
        eqNullable(uw, "stage", r.getStage());
        eqNullable(uw, "status", r.getStatus());
        int rows = this.metpFixCardMapper.update(null, uw);
        if (rows == 0) {
            return null;
        }
        return this.metpFixCardMapper.selectById(id);
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        if (id == null) {
            return false;
        }
        // 已办结不得再改：只有在办（含情形未落）的卡可改
        return this.metpFixCardMapper.update(null, new UpdateWrapper<TMetpFixCard>()
                .eq("id", id)
                .eq("del_flag", 0)
                .and(w -> w.isNull("status").or().eq("status", STATUS_ACTIVE))
                .set("content", remark)
                .set("update_time", new Date())) > 0;
    }

    @Override
    public boolean remove(Long id) {
        if (id == null) {
            return false;
        }
        // 已办结不得删除：只有在办（含情形未落）的卡可删
        return this.metpFixCardMapper.delete(new QueryWrapper<TMetpFixCard>()
                .eq("id", id)
                .eq("del_flag", 0)
                .and(w -> w.isNull("status").or().eq("status", STATUS_ACTIVE))) > 0;
    }

}
