package com.mallsen.service;

import com.mallsen.vo.BusinessDataVO;
import com.mallsen.vo.OrderOverViewVO;
import com.mallsen.vo.ProductOverViewVO;
import java.time.LocalDateTime;

public interface WorkspaceService {

    BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end);

    OrderOverViewVO getOrderOverView();

    ProductOverViewVO getProductOverView();
}
