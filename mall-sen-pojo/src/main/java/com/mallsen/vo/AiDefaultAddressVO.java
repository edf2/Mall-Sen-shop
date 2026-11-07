package com.mallsen.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户默认收货地址（供 AI 自动获取）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiDefaultAddressVO implements Serializable {

    private Long addressBookId;

    private String consignee;

    private String phone;

    private String fullAddress;

    private String label;
}
