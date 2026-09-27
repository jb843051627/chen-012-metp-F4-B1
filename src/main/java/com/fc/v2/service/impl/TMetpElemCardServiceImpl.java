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
        // 按条件查询列表：分页与条件由调用方统一处理，此处不得再夹带私货，
        // 否则台面上的账与导出的表会数出两家
        return this.baseMapper.selectList(queryWrapper);
    }

    /** 在册档案（未删除）；查无或已删除返回 null */
    private TMetpStnDoc loadActiveArch(Integer siteId) {
        if (siteId == null) {
            return null;
        }
        return metpStnDocMapper.selectOne(new QueryWrapper<TMetpStnDoc>()
                .eq("id", siteId).eq("del_flag", 0));
    }

    /** 档案已停测 */
    private static boolean isStopped(TMetpStnDoc arch) {
        return arch != null && arch.getStatus() != null && arch.getStatus().intValue() == 1;
    }

    /**
     * 定档：等于上限算高一档（严格小于上限才落入本档），
     * 与观测质控界限的分档规程同一口径；要素数或档案缺失判 0。
     */
    private static int bandOf(BigDecimal qty, TMetpStnDoc arch) {
        if (qty == null || arch == null) {
            return 0;
        }
        if (arch.getTh1Max() != null && qty.compareTo(arch.getTh1Max()) < 0) {
            return 1;
        }
        if (arch.getTh2Max() != null && qty.compareTo(arch.getTh2Max()) < 0) {
            return 2;
        }
        if (arch.getTh3Max() != null && qty.compareTo(arch.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }

    @Override
    public int insertTMetpElemCard(TMetpElemCard record) {
        if (record == null) {
            return 0;
        }

        TMetpStnDoc refArch = loadActiveArch(record.getSiteId());
        if (refArch == null) {
            return 0;
        }
        if (isStopped(refArch)) {
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
        record.setGradeLevel(Integer.valueOf(bandOf(record.getQty(), refArch)));

        record.setDelFlag(Integer.valueOf(0));
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTMetpElemCard(TMetpElemCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TMetpElemCard>()
                    .eq("bill_no", record.getBillNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        TMetpElemCard db = this.baseMapper.selectById(record.getId());
        if (db == null || (db.getDelFlag() != null && db.getDelFlag().intValue() != 0)) {
            return 0;
        }

        // 档案挪窝：挂接档案变了，台站编号跟着变；要素数变了，等级档跟着变
        Integer effSiteId = record.getSiteId() != null ? record.getSiteId() : db.getSiteId();
        TMetpStnDoc arch = null;
        if (record.getSiteId() != null) {
            arch = loadActiveArch(record.getSiteId());
            if (arch == null || isStopped(arch)) {
                return 0;
            }
            record.setSiteNo(arch.getSiteNo());
        }
        if (record.getQty() != null) {
            if (arch == null) {
                arch = loadActiveArch(effSiteId);
            }
            record.setGradeLevel(Integer.valueOf(bandOf(record.getQty(), arch)));
        }

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
