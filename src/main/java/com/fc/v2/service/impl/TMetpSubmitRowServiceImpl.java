package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMetpSubmitRowMapper;
import com.fc.v2.model.auto.TMetpSubmitRow;
import com.fc.v2.service.ITMetpSubmitRowService;

/**
 * 正点观测数据核收转存行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpSubmitRowServiceImpl implements ITMetpSubmitRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TMetpSubmitRowMapper metpSubmitRowMapper;

    @Override
    public TMetpSubmitRow selectTMetpSubmitRowById(Long id) {
        return this.metpSubmitRowMapper.selectById(id);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TMetpSubmitRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        if (rows.size() > MAX_ROWS) {
            // 超单批上限：整批不入库
            return -1;
        }
        String no = batchNo != null && !batchNo.trim().isEmpty()
                ? batchNo : rows.get(0).getBatchNo();
        if (no == null || no.trim().isEmpty()) {
            return 0;
        }
        // 同一批次重复提交不得重复入库
        Integer exists = this.metpSubmitRowMapper.selectCount(new QueryWrapper<TMetpSubmitRow>()
                .eq("batch_no", no).eq("del_flag", 0));
        if (exists != null && exists > 0) {
            return 0;
        }
        int ok = 0;
        for (TMetpSubmitRow r : rows) {
            boolean valid = r.getItemCode() != null && !r.getItemCode().trim().isEmpty()
                    && r.getQty() != null
                    && r.getQty().compareTo(java.math.BigDecimal.ZERO) > 0;
            r.setBatchNo(no);
            // 保留原册内行次，不重排；非法行记入失败明细
            r.setStatus(valid ? STATUS_OK : STATUS_FAIL);
            r.setDelFlag(0);
            this.metpSubmitRowMapper.insert(r);
            if (valid) {
                ok++;
            }
        }
        return ok;
    }

    @Override
    public List<TMetpSubmitRow> listErrors(String batchNo) {
        return this.metpSubmitRowMapper.selectList(new QueryWrapper<TMetpSubmitRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL).eq("del_flag", 0)
                .orderByAsc("row_no"));
    }
}
