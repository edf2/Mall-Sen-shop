package com.mallsen.message;

import com.mallsen.entity.OrderDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAsyncMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long orderId;
    private String orderNumber;
    private Long userId;
    private List<OrderDetail> orderDetailList;
}
