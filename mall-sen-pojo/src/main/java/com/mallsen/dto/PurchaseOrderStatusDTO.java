package com.mallsen.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class PurchaseOrderStatusDTO implements Serializable {

    private String status;
}
