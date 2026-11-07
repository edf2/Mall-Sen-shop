package com.mallsen.controller.admin;

import com.mallsen.result.Result;
import com.mallsen.service.WorkspaceService;
import com.mallsen.vo.BusinessDataVO;
import com.mallsen.vo.OrderOverViewVO;
import com.mallsen.vo.ProductOverViewVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.LocalTime;

@RestController
@RequestMapping("/admin/workspace")
@Tag(name = "工作台管理")
public class WorkSpaceController {

    @Autowired
    private WorkspaceService workspaceService;

    @GetMapping("/businessData")
    @Operation(summary = "工作台今日数据查询")
    public Result<BusinessDataVO> getBusinessData(){
        LocalDateTime begin = LocalDateTime.now().with(LocalTime.MIN);

        LocalDateTime end = LocalDateTime.now().with(LocalTime.MAX);

        BusinessDataVO businessDataVO = workspaceService.getBusinessData(begin, end);
        return Result.success(businessDataVO);
    }

    @GetMapping("/overviewOrders")
    @Operation(summary = "工作台订单管理数据查询")
    public Result<OrderOverViewVO> getOrderOverView(){
        OrderOverViewVO orderOverViewVO = workspaceService.getOrderOverView();
        return Result.success(orderOverViewVO);
    }

    @GetMapping("/overviewProducts")
    @Operation(summary = "工作台商品管理数据查询")
    public Result<ProductOverViewVO> getProductOverView(){
        ProductOverViewVO productOverViewVO = workspaceService.getProductOverView();
        return Result.success(productOverViewVO);
    }

}
