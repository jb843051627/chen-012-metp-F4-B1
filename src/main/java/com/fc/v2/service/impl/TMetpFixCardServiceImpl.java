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

    private static final int MAX_STAGE = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

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

    @Override
    public TMetpFixCard advance(Long id, String remark) {
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        r.setStage(Math.min(st + 2, MAX_STAGE));
        r.setStatus(STATUS_ACTIVE);
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
        r.setStage(0);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.metpFixCardMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null) {
            return false;
        }
        r.setContent(remark);
        return this.metpFixCardMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null) {
            return false;
        }
        return this.metpFixCardMapper.deleteById(id) > 0;
    }

}
