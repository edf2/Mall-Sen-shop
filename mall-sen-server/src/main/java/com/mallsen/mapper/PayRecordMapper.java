package com.mallsen.mapper;

import com.mallsen.entity.PayRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PayRecordMapper {

    /**
     * 插入支付流水
     */
    @Insert("insert into pay_record (order_id, order_number, transaction_id, pay_method, amount, status, create_time) " +
            "values (#{orderId}, #{orderNumber}, #{transactionId}, #{payMethod}, #{amount}, #{status}, #{createTime})")
    void insert(PayRecord record);

    /**
     * 根据订单号查询待支付流水
     */
    @Select("select * from pay_record where order_number = #{orderNumber} and status = 0 limit 1")
    PayRecord getPendingByOrderNumber(@Param("orderNumber") String orderNumber);

    /**
     * 支付成功，更新流水状态（幂等：仅待支付流水会被更新）
     */
    @Update("update pay_record set status = 1, pay_time = now() " +
            "where order_number = #{orderNumber} and status = 0")
    int markSuccess(@Param("orderNumber") String orderNumber);

    /**
     * 退款：仅将已支付成功(status=1)的流水置为已退款(status=3)，幂等
     */
    @Update("update pay_record set status = 3 where order_number = #{orderNumber} and status = 1")
    int markRefunded(@Param("orderNumber") String orderNumber);
}
