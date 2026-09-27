package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TMetpElemCard;
import com.fc.v2.service.ITMetpElemCardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 观测要素挂用建档卡 Controller
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "观测要素挂用建档卡")
@Controller
@RequestMapping("/MetpElemCardController")
public class MetpElemCardController extends BaseController {

    private final String prefix = "admin/metpElemCard";

    @Autowired
    private ITMetpElemCardService metpElemCardService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("metp:metpElemCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "观测要素挂用建档卡集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("metp:metpElemCard:list")
    @ResponseBody
    public ResultTable list(TMetpElemCard record) {
        QueryWrapper<TMetpElemCard> queryWrapper = new QueryWrapper<TMetpElemCard>();
        startPage();
        com.github.pagehelper.PageInfo<TMetpElemCard> page =
                new com.github.pagehelper.PageInfo<TMetpElemCard>(metpElemCardService.selectTMetpElemCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "观测要素挂用建档卡新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("metp:metpElemCard:add")
    @ResponseBody
    public AjaxResult add(TMetpElemCard record) {
        return toAjax(metpElemCardService.insertTMetpElemCard(record));
    }

    @Log(title = "观测要素挂用建档卡修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("metp:metpElemCard:edit")
    @ResponseBody
    public AjaxResult editSave(TMetpElemCard record) {
        return toAjax(metpElemCardService.updateTMetpElemCard(record));
    }

    @Log(title = "观测要素挂用建档卡删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("metp:metpElemCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(metpElemCardService.deleteTMetpElemCardByIds(ids));
    }
}
