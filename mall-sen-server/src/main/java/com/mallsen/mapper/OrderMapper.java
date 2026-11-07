package com.mallsen.mapper;

import com.github.pagehelper.Page;
import com.mallsen.dto.GoodsSalesDTO;
import com.mallsen.dto.OrdersPageQueryDTO;
import com.mallsen.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {
    /**
     * 插入订单数据
     * @param orders
     */
    void insert(Orders orders);

    /**
     * 根据订单号查询订单
     * @param orderNumber
     * @return
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByOrderNumber(String orderNumber);

    /**
     * 根据id更新订单
     * @param orders
     */
    void updateById(Orders orders);

    /**
     * 分页查询订单
     * @param ordersPageQueryDTO
     * @return
     */
    Page<Orders> page(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     *  根据id查询订单
     * @param id
     * @return
     */
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 根据状态查询订单数量
     * @param toBeConfirmed
     * @return
     */
    @Select("select count(id) from orders where status = #{status}")
    Integer countByStatus(Integer toBeConfirmed);

    @Select("select * from orders where status = #{status} and order_time < #{time}")
    List<Orders> getByStatusAndOrderTimeLT(Integer status, LocalDateTime time);

    /**
     * 超时取消待付款订单：仅当订单仍处于待付款状态时才置为已取消。
     * 防止定时任务与用户正在支付的操作竞态，把刚支付的订单误取消、误归还库存。
     * @return 影响行数，0 表示订单状态已变化（已支付等），本次取消不生效
     */
    @Update("update orders set status = 6, cancel_reason = #{cancelReason}, cancel_time = #{cancelTime} " +
            "where id = #{id} and status = 1")
    int cancelPendingPaymentOrder(@Param("id") Long id,
                                  @Param("cancelReason") String cancelReason,
                                  @Param("cancelTime") LocalDateTime cancelTime);

    /**
     * 超时完成派送中订单：仅当订单仍处于派送中状态时才置为已完成，避免竞态覆盖。
     * @return 影响行数，0 表示订单状态已变化，本次更新不生效
     */
    @Update("update orders set status = 5, delivery_time = now() where id = #{id} and status = 4")
    int completeDeliveryInProgressOrder(@Param("id") Long id);

    /**
     * 根据条件查询订单总金额
     * @param map
     * @return
     */
    Double sumByMap(Map map);

    /**
     * 根据条件查询订单数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);

    /**
     * 统计指定时间区间内的销量排名前10
     * @param begin
     * @param end
     * @return
     */
    List<GoodsSalesDTO> getSalesTop(LocalDateTime begin, LocalDateTime end);

    /**
     * 查询指定商品在时间范围内的每日销量
     */
    @Select("SELECT DATE(o.order_time) as date, SUM(od.number) as quantity " +
            "FROM orders o INNER JOIN order_detail od ON o.id = od.order_id " +
            "WHERE o.status = #{status} AND o.order_time BETWEEN #{begin} AND #{end} " +
            "AND od.product_id = #{productId} " +
            "GROUP BY DATE(o.order_time) " +
            "ORDER BY DATE(o.order_time)")
    List<Map<String, Object>> getDailySalesByProduct(Map<String, Object> params);

    /**
     * 按天分组统计营业额（报表用，一条 GROUP BY 代替逐日查询，避免 N 次 SQL）
     *
     * @return [{date, turnover}] turnover 为当日已完成订单金额合计
     */
    List<Map<String, Object>> getTurnoverByDate(@Param("begin") LocalDateTime begin,
                                                @Param("end") LocalDateTime end,
                                                @Param("status") Integer status);

    /**
     * 按天分组统计订单总数与有效订单数（报表用，一条 GROUP BY 代替逐日两次查询）
     *
     * @return [{date, total, valid}] total 当日订单总数，valid 当日已完成订单数
     */
    List<Map<String, Object>> getOrderStatsByDate(@Param("begin") LocalDateTime begin,
                                                  @Param("end") LocalDateTime end);

    /**
     * 按天分组统计已完成订单的营业额与订单数（AI 销售报告用）
     *
     * @return [{date, turnover, orderCount}]
     */
    List<Map<String, Object>> getDailySalesSummary(@Param("begin") LocalDateTime begin,
                                                   @Param("end") LocalDateTime end);
}
