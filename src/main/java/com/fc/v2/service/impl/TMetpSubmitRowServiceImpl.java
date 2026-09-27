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
    public int submitBatch(String batchNo, List<TMetpSubmitRow> rows) {
        // 空批次返回 0
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // 行数超过单批上限返回 -1 且整批不入库
        if (rows.size() > MAX_ROWS) {
            return -1;
        }
        String no = (batchNo != null && !batchNo.trim().isEmpty())
                ? batchNo : rows.get(0).getBatchNo();
        if (no == null || no.trim().isEmpty()) {
            return 0;
        }
        // 同一批次重复提交不得重复入库
        Integer exists = this.metpSubmitRowMapper.selectCount(new QueryWrapper<TMetpSubmitRow>()
                .eq("batch_no", no));
        if (exists != null && exists.intValue() > 0) {
            return 0;
        }
        // 逐行校验：合法的入库、非法的记入失败明细（保留原始行号）
        int ok = 0;
        int seq = 0;
        for (TMetpSubmitRow r : rows) {
            seq++;
            r.setRowNo(Integer.valueOf(seq));
            r.setBatchNo(no);
            if (r.getDelFlag() == null) {
                r.setDelFlag(Integer.valueOf(0));
            }
            boolean valid = r.getItemCode() != null && !r.getItemCode().trim().isEmpty()
                    && r.getQty() != null
                    && r.getQty().compareTo(java.math.BigDecimal.ZERO) > 0;
            r.setStatus(Integer.valueOf(valid ? STATUS_OK : STATUS_FAIL));
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
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL)
                .orderByAsc("row_no"));
    }
}
