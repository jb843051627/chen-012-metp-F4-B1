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
        String no = rows.get(0).getBatchNo();
        java.util.List<TMetpSubmitRow> errors = new java.util.ArrayList<TMetpSubmitRow>();
        int seq = 0;
        for (TMetpSubmitRow r : rows) {
            if (r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                    || r.getQty() == null
                    || r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                seq++;
                r.setRowNo(Integer.valueOf(seq));
                r.setBatchNo(no);
                r.setStatus(STATUS_FAIL);
                this.metpSubmitRowMapper.insert(r);
                errors.add(r);
            }
        }
        if (!errors.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TMetpSubmitRow r : rows) {
            r.setBatchNo(no);
            r.setStatus(STATUS_OK);
            this.metpSubmitRowMapper.insert(r);
            ok++;
        }
        return ok;
    }

    @Override
    public List<TMetpSubmitRow> listErrors(String batchNo) {
        return this.metpSubmitRowMapper.selectList(new QueryWrapper<TMetpSubmitRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL));
    }
}
