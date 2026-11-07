package com.mallsen.mapper;

import com.mallsen.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderDetailMapper {

    void insertBatch(List<OrderDetail> orderDetails);

    @Select("SELECT * FROM order_detail WHERE order_id = #{orderId}")
    List<OrderDetail> getByOrderId(Long orderId);

    @Select("<script>" +
            "SELECT * FROM order_detail WHERE order_id IN " +
            "<foreach collection='orderIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<OrderDetail> getByOrderIds(@Param("orderIds") List<Long> orderIds);

    @Select("<script>" +
            "SELECT od.*, COALESCE(p.image, od.image) AS image FROM order_detail od " +
            "LEFT JOIN product p ON od.product_id = p.id " +
            "WHERE od.order_id = #{orderId}" +
            "</script>")
    List<OrderDetail> getByOrderIdWithLatestImage(Long orderId);

    @Select("<script>" +
            "SELECT od.*, COALESCE(p.image, od.image) AS image FROM order_detail od " +
            "LEFT JOIN product p ON od.product_id = p.id " +
            "WHERE od.order_id IN " +
            "<foreach collection='orderIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<OrderDetail> getByOrderIdsWithLatestImage(@Param("orderIds") List<Long> orderIds);

    List<Map<String, Object>> getProductDailySales(@Param("productId") Long productId,
                                                     @Param("begin") LocalDateTime begin,
                                                     @Param("end") LocalDateTime end);

    List<Map<String, Object>> getProductSalesInPeriod(@Param("begin") LocalDateTime begin,
                                                        @Param("end") LocalDateTime end);
}
