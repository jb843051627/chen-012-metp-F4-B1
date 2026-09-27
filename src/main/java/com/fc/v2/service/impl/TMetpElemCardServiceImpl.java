package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TMetpElemCardMapper;
import com.fc.v2.mapper.auto.TMetpStnDocMapper;
import com.fc.v2.model.auto.TMetpElemCard;
import com.fc.v2.model.auto.TMetpStnDoc;
import com.fc.v2.service.ITMetpElemCardService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 观测要素挂用建档卡Service业务层处理
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TMetpElemCardServiceImpl extends ServiceImpl<TMetpElemCardMapper, TMetpElemCard> implements ITMetpElemCardService {

    @Autowired
    private TMetpStnDocMapper metpStnDocMapper;

    @Override
    public TMetpElemCard selectTMetpElemCardById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TMetpElemCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TMetpElemCard> selectTMetpElemCardList(Wrapper<TMetpElemCard> queryWrapper) {
        // 查询条件与分页统归调用方：台面与导出同一条道、同一个数
        return this.baseMapper.selectList(queryWrapper);
    }

    /** 档案照应：须在册且未停测，否则返回 null（新增/修改同一把尺） */
    private TMetpStnDoc requireActiveArch(Integer siteId) {
        if (siteId == null) {
            return null;
        }
        TMetpStnDoc arch = metpStnDocMapper.selectOne(new QueryWrapper<TMetpStnDoc>()
                .eq("id", siteId).eq("del_flag", 0));
        if (arch == null) {
            return null;
        }
        if (arch.getStatus() != null && arch.getStatus() == 1) {
            return null;
        }
        return arch;
    }

    /** 定档：等于上限算高一档（与规程文本同口径） */
    private int bandLevelOf(TMetpStnDoc arch, BigDecimal qty) {
        if (arch == null || qty == null
                || arch.getTh1Max() == null || arch.getTh2Max() == null || arch.getTh3Max() == null) {
            return 0;
        }
        if (qty.compareTo(arch.getTh1Max()) < 0) {
            return 1;
        }
        if (qty.compareTo(arch.getTh2Max()) < 0) {
            return 2;
        }
        if (qty.compareTo(arch.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }

    @Override
    public int insertTMetpElemCard(TMetpElemCard record) {
        if (record == null) {
            return 0;
        }

        TMetpStnDoc refArch = requireActiveArch(record.getSiteId());
        if (refArch == null) {
            return 0;
        }
        record.setSiteNo(refArch.getSiteNo());
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TMetpElemCard>()
                    .eq("bill_no", record.getBillNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        record.setGradeLevel(bandLevelOf(refArch, record.getQty()));

        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTMetpElemCard(TMetpElemCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        TMetpElemCard exist = this.baseMapper.selectOne(new QueryWrapper<TMetpElemCard>()
                .eq("id", record.getId()).eq("del_flag", 0));
        if (exist == null) {
            return 0;
        }

        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TMetpElemCard>()
                    .eq("bill_no", record.getBillNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        // 档案照应线两头并到一张桌上：改挂别家档案或档案自身挪窝，挂用卡跟着变
        Integer siteId = record.getSiteId() != null ? record.getSiteId() : exist.getSiteId();
        BigDecimal qty = record.getQty() != null ? record.getQty() : exist.getQty();
        TMetpStnDoc refArch = requireActiveArch(siteId);
        if (refArch == null) {
            return 0;
        }
        record.setSiteId(siteId);
        record.setSiteNo(refArch.getSiteNo());
        record.setGradeLevel(bandLevelOf(refArch, qty));

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TMetpElemCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTMetpElemCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTMetpElemCardById(Long id) {
        return this.baseMapper.deleteById(id);
    }
}
