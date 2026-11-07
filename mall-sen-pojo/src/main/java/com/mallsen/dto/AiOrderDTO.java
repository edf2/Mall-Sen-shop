package com.mallsen.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI代客下单参数
 */
@Data
public class AiOrderDTO implements Serializable {

    private Long userId;

    private String addressDetail;

    private String consignee;

    private String phone;

    private Long addressBookId;

    private String remark;

    private List<AiOrderItemDTO> items;

    @Data
    public static class AiOrderItemDTO implements Serializable {
        private Long productId;
        private Integer quantity;
    }
}
