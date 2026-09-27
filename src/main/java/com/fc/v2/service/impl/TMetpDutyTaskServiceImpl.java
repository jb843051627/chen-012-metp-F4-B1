package com.fc.v2.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMetpDutyTaskMapper;
import com.fc.v2.model.auto.TMetpDutyTask;
import com.fc.v2.service.ITMetpDutyTaskService;

/**
 * 标定巡检到期派工条目 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpDutyTaskServiceImpl implements ITMetpDutyTaskService {

    /** 执行窗口：单条提前派发天数须落在 [WIN_FROM, WIN_TO] 内（含两端） */
    private static final int WIN_FROM = 2;
    private static final int WIN_TO = 5;
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TMetpDutyTaskMapper metpDutyTaskMapper;

    @Override
    public TMetpDutyTask selectTMetpDutyTaskById(Long id) {
        return this.metpDutyTaskMapper.selectById(id);
    }

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    /** 执行窗口内：提前派发天数落在 [WIN_FROM, WIN_TO]（含两端） */
    private static boolean inWindow(TMetpDutyTask r) {
        if (r.getAmount() == null) {
            return false;
        }
        return r.getAmount().compareTo(new java.math.BigDecimal(WIN_FROM)) >= 0
                && r.getAmount().compareTo(new java.math.BigDecimal(WIN_TO)) <= 0;
    }

    @Override
    public List<TMetpDutyTask> listDue(Date at) {
        List<TMetpDutyTask> due = new java.util.ArrayList<TMetpDutyTask>();
        if (at == null) {
            return due;
        }
        List<TMetpDutyTask> all = this.metpDutyTaskMapper.selectList(new QueryWrapper<TMetpDutyTask>());
        for (TMetpDutyTask r : all) {
            // 尚未处理：只认待派
            if (r.getStatus() == null || r.getStatus().intValue() != STATUS_WAIT) {
                continue;
            }
            if (r.getDelFlag() != null && r.getDelFlag().intValue() != 0) {
                continue;
            }
            // 到期：due_at 不晚于 at，恰好到期也算到期
            if (r.getDueAt() == null || ts(r.getDueAt()).compareTo(ts(at)) > 0) {
                continue;
            }
            // 执行窗口内
            if (!inWindow(r)) {
                continue;
            }
            due.add(r);
        }
        return due;
    }

    @Override
    public int runOnce(Date at) {
        List<TMetpDutyTask> due = listDue(at);
        if (due.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TMetpDutyTask r : due) {
            try {
                if (r.getAmount() == null || r.getAmount().signum() < 0) {
                    // 单条失败：标记派不出去，跳过继续
                    r.setStatus(Integer.valueOf(STATUS_FAIL));
                } else {
                    r.setStatus(Integer.valueOf(STATUS_DONE));
                    ok++;
                }
                this.metpDutyTaskMapper.updateById(r);
            } catch (RuntimeException e) {
                // 单条失败跳过继续，不拖垮整批
            }
        }
        return ok;
    }
}
