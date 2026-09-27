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

    /** 界限情形：现行 */
    private static final int STATUS_CURRENT = 0;

    /** 输入合法区间 [0, 100]，越界一律判 0（evaluate / evaluateTop 同一口径） */
    private static final BigDecimal INPUT_MIN = BigDecimal.ZERO;
    private static final BigDecimal INPUT_MAX = new BigDecimal("100");

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

    /**
     * 在用口径（全模块唯一判定来源，定位/列表/计数/旁路复用都走这里）：
     * 删除标记正常 + 现行 + 生效窗口 [eff_start, eff_end) 覆盖 at。
     * 交棒之日不含——换版当日旧版让位、新版当值，不得两家争。
     */
    private static boolean effectiveAt(TMetpQcLine r, Date at) {
        if (r == null || at == null) {
            return false;
        }
        if (r.getDelFlag() != null && r.getDelFlag().intValue() != 0) {
            return false;
        }
        if (r.getStatus() != null && r.getStatus().intValue() != STATUS_CURRENT) {
            return false;
        }
        if (r.getEffStart() != null && ts(r.getEffStart()).compareTo(ts(at)) > 0) {
            return false;
        }
        if (r.getEffEnd() != null && ts(r.getEffEnd()).compareTo(ts(at)) <= 0) {
            return false;
        }
        return true;
    }

    private static boolean outOfRange(BigDecimal input) {
        return input.compareTo(INPUT_MIN) < 0 || input.compareTo(INPUT_MAX) > 0;
    }

    @Override
    public TMetpQcLine selectTMetpQcLineById(Long id) {
        return this.metpQcLineMapper.selectById(id);
    }

    @Override
    public List<TMetpQcLine> listAvailable(Date at) {
        List<TMetpQcLine> out = new java.util.ArrayList<TMetpQcLine>();
        if (at == null) {
            return out;
        }
        for (TMetpQcLine r : this.metpQcLineMapper.selectList(new QueryWrapper<TMetpQcLine>())) {
            if (effectiveAt(r, at)) {
                out.add(r);
            }
        }
        out.sort(PRIORITY_THEN_CODE_DESC);
        return out;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        if (outOfRange(input)) {
            return 0;
        }
        TMetpQcLine rule = findRule(ruleCode, at);
        if (rule == null) {
            // 查无在用版本：与 evaluateTop 同一口径，判 0，不许抓作废旧版顶数
            return 0;
        }
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        if (outOfRange(input)) {
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
        return effectiveAt(this.metpQcLineMapper.selectById(id), at);
    }

    @Override
    public int countAvailable(Date at) {
        return listAvailable(at).size();
    }

    /**
     * 定位 at 时刻生效的版本：只认生效窗口覆盖 at 的现行版；
     * 同一代号多版同时覆盖时取 eff_start 最新者；查无在用版本返回 null。
     */
    private TMetpQcLine findRule(String ruleCode, Date at) {
        TMetpQcLine best = null;
        for (TMetpQcLine r : listAvailable(at)) {
            if (!ruleCode.equals(r.getRuleCode())) {
                continue;
            }
            if (best == null || startOf(r).compareTo(startOf(best)) > 0) {
                best = r;
            }
        }
        return best;
    }

    /** 启用之日串；未维护按最旧处理（空串排在一切日期串之前） */
    private static String startOf(TMetpQcLine r) {
        return r.getEffStart() == null ? "" : ts(r.getEffStart());
    }

    /** 阈值解析：档案为主、明细覆盖；两者皆缺返回 null（调用方按不可用处理），精度统一 2 位 */
    private BigDecimal resolveLimit(TMetpQcLine r, int tier) {
        if (tier == 1) {
            return r.getTh1Max();
        }
        if (tier == 2) {
            return r.getTh2Max();
        }
        return r.getTh3Max();
    }

    /** 档案级默认阈值（明细未维护时的回退来源） */
    private BigDecimal defaultThreshold(TMetpQcLine r, int tier) {
        return r.getTh1Max();
    }

    /** 分档：等于上限算高一档（严格小于上限才落入本档）；规则或输入缺失判 0 */
    private int levelOf(TMetpQcLine rule, BigDecimal input) {
        if (rule == null || input == null) {
            return 0;
        }
        for (int tier = 1; tier <= 3; tier++) {
            BigDecimal limit = resolveLimit(rule, tier);
            if (limit != null && input.compareTo(limit) < 0) {
                return tier;
            }
        }
        return 4;
    }
}
