package com.mallsen.service;

import java.time.LocalDate;
import java.util.Map;

public interface BusinessAIService {

    Map<String, Object> getProductSales(Long productId, int days);

    Object getStockPrediction(int days);

    Map<String, Object> getSalesReport(LocalDate start, LocalDate end);

    Map<String, Object> getInventoryOverview();
}
