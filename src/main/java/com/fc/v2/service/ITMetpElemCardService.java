package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TMetpElemCard;

import java.util.List;

/**
 * 观测要素挂用建档卡 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITMetpElemCardService {

    /** 按主键查询 */
    TMetpElemCard selectTMetpElemCardById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TMetpElemCard> selectTMetpElemCardList(Wrapper<TMetpElemCard> queryWrapper);

    /** 新增 */
    int insertTMetpElemCard(TMetpElemCard record);

    /** 修改 */
    int updateTMetpElemCard(TMetpElemCard record);

    /** 批量删除 */
    int deleteTMetpElemCardByIds(String ids);

    /** 按主键删除 */
    int deleteTMetpElemCardById(Long id);
}
