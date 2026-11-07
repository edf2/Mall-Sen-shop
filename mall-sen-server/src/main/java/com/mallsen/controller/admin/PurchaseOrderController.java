package com.mallsen.controller.admin;

import com.mallsen.dto.PurchaseOrderDTO;
import com.mallsen.dto.PurchaseOrderPageQueryDTO;
import com.mallsen.dto.PurchaseOrderStatusDTO;
import com.mallsen.result.PageResult;
import com.mallsen.result.Result;
import com.mallsen.service.PurchaseOrderService;
import com.mallsen.vo.PurchaseOrderVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/purchase-order")
@Tag(name = "采购订单管理")
@Slf4j
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @PostMapping
    @Operation(summary = "创建采购单")
    public Result<PurchaseOrderVO> create(@RequestBody PurchaseOrderDTO dto) {
        log.info("创建采购单：{}", dto);
        PurchaseOrderVO vo = purchaseOrderService.create(dto);
        return Result.success(vo);
    }

    @GetMapping("/page")
    @Operation(summary = "采购单分页查询")
    public Result<PageResult> page(PurchaseOrderPageQueryDTO dto) {
        log.info("采购单分页查询：{}", dto);
        PageResult pageResult = purchaseOrderService.pageQuery(dto);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询采购单详情")
    public Result<PurchaseOrderVO> getById(@PathVariable Long id) {
        log.info("查询采购单详情：{}", id);
        PurchaseOrderVO vo = purchaseOrderService.getById(id);
        return Result.success(vo);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "更新采购单状态")
    public Result updateStatus(@PathVariable Long id, @RequestBody PurchaseOrderStatusDTO dto) {
        log.info("更新采购单状态：id={}, status={}", id, dto.getStatus());
        purchaseOrderService.updateStatus(id, dto.getStatus());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除采购单")
    public Result delete(@PathVariable Long id) {
        log.info("删除采购单：{}", id);
        purchaseOrderService.delete(id);
        return Result.success();
    }
}
