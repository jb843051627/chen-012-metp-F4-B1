package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMetpQcLineMapper;
import com.fc.v2.model.auto.TMetpQcLine;
import com.fc.v2.service.ITMetpQcLineService;

/**
 * 观测数据质量控制界限 Service业务层处理（rule-eval 形状）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpQcLineServiceImpl implements ITMetpQcLineService {

    /** 优先级降序；并列按 ruleCode 降序（确定性） */
    private static final Comparator<TMetpQcLine> PRIORITY_THEN_CODE_DESC = new Comparator<TMetpQcLine>() {
        @Override
        public int compare(TMetpQcLine a, TMetpQcLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TMetpQcLineMapper metpQcLineMapper;

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）+ 字符串绑定：
     * JDBC 的 serverTimezone 与本机时区不对称，直接把 java.util.Date 作参数会整体偏移，
     * 使边界判定在"切换当日"这类用例上随机错档（实测踩到）。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    public TMetpQcLine selectTMetpQcLineById(Long id) {
        return this.metpQcLineMapper.selectById(id);
    }

    /** 可用口径：未删 + 现行 + 启用之日<=at + (交棒之日为空 或 at<交棒之日)。交棒当日即交棒，新旧不并立 */
    private static QueryWrapper<TMetpQcLine> availableWrapper(Date at) {
        String now = ts(at);
        return new QueryWrapper<TMetpQcLine>()
                .eq("del_flag", 0)
                .eq("status", 0)
                .and(w -> w.isNull("eff_start").or().le("eff_start", now))
                .and(w -> w.isNull("eff_end").or().gt("eff_end", now));
    }

    @Override
    public List<TMetpQcLine> listAvailable(Date at) {
        if (at == null) {
            return new java.util.ArrayList<TMetpQcLine>();
        }
        List<TMetpQcLine> list = this.metpQcLineMapper.selectList(availableWrapper(at));
        // 并档顺位降序，并列按 ruleCode 降序：谁先谁后得有章程
        list.sort(PRIORITY_THEN_CODE_DESC);
        return list;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        if (input.compareTo(BigDecimal.ZERO) < 0
                || input.compareTo(new BigDecimal("100")) > 0) {
            return 0;
        }
        TMetpQcLine rule = findRule(ruleCode, at);
        if (rule == null) {
            return 0;
        }
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        if (input.compareTo(BigDecimal.ZERO) < 0
                || input.compareTo(new BigDecimal("100")) > 0) {
            return 0;
        }
        List<TMetpQcLine> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        return levelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        // 与定位/列表同一把尺：存在且在 at 时点可用才算可用
        TMetpQcLine r = this.metpQcLineMapper.selectOne(availableWrapper(at).eq("id", id));
        return r != null;
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return listAvailable(at).size();
    }

    /** 取 ruleCode 在 at 时点生效的那版；够不着线的时点不托底，返回 null（调用方按不可用处理） */
    private TMetpQcLine findRule(String ruleCode, Date at) {
        List<TMetpQcLine> hit = this.metpQcLineMapper.selectList(availableWrapper(at)
                .eq("rule_code", ruleCode));
        if (hit.isEmpty()) {
            return null;
        }
        // 同一时点若有多版交叠，取启用之日最晚的一版；再并列按主键兜底，保证确定
        hit.sort(new Comparator<TMetpQcLine>() {
            @Override
            public int compare(TMetpQcLine a, TMetpQcLine b) {
                Date ea = a.getEffStart();
                Date eb = b.getEffStart();
                if (ea == null && eb == null) {
                    return byId(a, b);
                }
                if (ea == null) {
                    return 1;
                }
                if (eb == null) {
                    return -1;
                }
                int c = eb.compareTo(ea);
                return c != 0 ? c : byId(a, b);
            }

            private int byId(TMetpQcLine a, TMetpQcLine b) {
                Long ia = a.getId() == null ? Long.valueOf(0L) : a.getId();
                Long ib = b.getId() == null ? Long.valueOf(0L) : b.getId();
                return ib.compareTo(ia);
            }
        });
        return hit.get(0);
    }

    /** 分档：等于上限取高一档（规程文本口径） */
    private int levelOf(TMetpQcLine rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) < 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) < 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }
}
