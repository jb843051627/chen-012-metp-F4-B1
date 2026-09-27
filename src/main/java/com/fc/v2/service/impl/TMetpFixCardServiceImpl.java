package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

    /** 关口 0..5，推进/回退同用这一把尺 */
    private static final int MAX_STAGE = 5;
    /** 卡的落定：0在办 1已解除 2已归档（办结） */
    private static final int STATUS_ACTIVE = 0;
    private static final int STATUS_ARCHIVED = 2;

    @javax.annotation.Resource
    private TMetpFixCardMapper metpFixCardMapper;

    @Override
    public TMetpFixCard selectTMetpFixCardById(Long id) {
        return this.metpFixCardMapper.selectById(id);
    }

    @Override
    public List<TMetpFixCard> selectTMetpFixCardList(QueryWrapper<TMetpFixCard> queryWrapper) {
        return this.metpFixCardMapper.selectList(queryWrapper);
    }

    /** 已办结（已归档）的卡不再受理任何流转/改动 */
    private static boolean isTerminal(TMetpFixCard r) {
        return r.getStatus() != null && r.getStatus().intValue() == STATUS_ARCHIVED;
    }

    @Override
    public TMetpFixCard advance(Long id, String remark) {
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null || isTerminal(r)) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage().intValue();
        st = Math.min(st + 1, MAX_STAGE);
        r.setStage(Integer.valueOf(st));
        // 闯过末关即归卷办结；否则仍在办
        r.setStatus(Integer.valueOf(st >= MAX_STAGE ? STATUS_ARCHIVED : STATUS_ACTIVE));
        r.setLastAction(remark);
        this.metpFixCardMapper.updateById(r);
        return r;
    }

    @Override
    public TMetpFixCard rollback(Long id, String remark) {
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage().intValue();
        // 退卷：与推进同一把尺，一次只退一档，退回到在办
        r.setStage(Integer.valueOf(Math.max(st - 1, 0)));
        r.setStatus(Integer.valueOf(STATUS_ACTIVE));
        r.setLastAction(remark);
        this.metpFixCardMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null || isTerminal(r)) {
            return false;
        }
        r.setContent(remark);
        return this.metpFixCardMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null || isTerminal(r)) {
            return false;
        }
        return this.metpFixCardMapper.deleteById(id) > 0;
    }

}
