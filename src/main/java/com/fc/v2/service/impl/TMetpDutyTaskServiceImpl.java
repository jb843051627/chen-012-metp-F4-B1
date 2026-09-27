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

    @Override
    public List<TMetpDutyTask> listDue(Date at) {
        List<TMetpDutyTask> due = new java.util.ArrayList<TMetpDutyTask>();
        if (at == null) {
            return due;
        }
        List<TMetpDutyTask> all = this.metpDutyTaskMapper.selectList(new QueryWrapper<TMetpDutyTask>()
                .eq("del_flag", 0)
                .eq("status", STATUS_WAIT));
        String now = ts(at);
        java.math.BigDecimal winFrom = new java.math.BigDecimal(WIN_FROM);
        java.math.BigDecimal winTo = new java.math.BigDecimal(WIN_TO);
        for (TMetpDutyTask r : all) {
            if (r.getDueAt() == null || r.getAmount() == null) {
                continue;
            }
            // 执行窗口内：提前派发天数须落在 [WIN_FROM, WIN_TO]
            if (r.getAmount().compareTo(winFrom) < 0 || r.getAmount().compareTo(winTo) > 0) {
                continue;
            }
            // 到期即派，恰好到期也算
            if (ts(r.getDueAt()).compareTo(now) <= 0) {
                due.add(r);
            }
        }
        return due;
    }

    @Override
    public int runOnce(Date at) {
        List<TMetpDutyTask> due = listDue(at);
        int ok = 0;
        for (TMetpDutyTask r : due) {
            try {
                // 条件认领：只有仍是"待派"的条目才翻成"已派"，重跑/并发都不会一单算两回
                int rows = this.metpDutyTaskMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TMetpDutyTask>()
                                .eq("id", r.getId())
                                .eq("status", STATUS_WAIT)
                                .set("status", STATUS_DONE)
                                .set("update_time", new Date()));
                if (rows > 0) {
                    ok++;
                }
            } catch (Exception e) {
                // 单条失败跳过继续
            }
        }
        return ok;
    }
}
