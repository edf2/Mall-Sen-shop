package com.mallsen.controller.aigc;

import com.mallsen.result.Result;
import com.mallsen.service.BusinessAIService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/admin/business/ai")
@Tag(name = "商家 AI 数据接口")
@Slf4j
@RequiredArgsConstructor
public class BusinessAIController {

    private final BusinessAIService businessAIService;

    @GetMapping("/product-sales")
    @Operation(summary = "查询商品历史销量")
    public Result<?> getProductSales(
            @RequestParam Long productId,
            @RequestParam(defaultValue = "30") int days) {
        log.info("查询商品销售趋势: productId={}, days={}", productId, days);
        return Result.success(businessAIService.getProductSales(productId, days));
    }

    @GetMapping("/stock-prediction")
    @Operation(summary = "预测缺货商品列表")
    public Result<?> getStockPrediction(
            @RequestParam(defaultValue = "7") int days) {
        log.info("预测缺货商品: days={}", days);
        return Result.success(businessAIService.getStockPrediction(days));
    }

    @GetMapping("/sales-report")
    @Operation(summary = "生成销售报告")
    public Result<?> getSalesReport(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        log.info("生成销售报告: start={}, end={}", start, end);
        return Result.success(businessAIService.getSalesReport(start, end));
    }

    @GetMapping("/inventory-overview")
    @Operation(summary = "获取库存概览")
    public Result<?> getInventoryOverview() {
        log.info("获取库存概览");
        return Result.success(businessAIService.getInventoryOverview());
    }
}
