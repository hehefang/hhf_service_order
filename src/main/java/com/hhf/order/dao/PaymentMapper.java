package com.hhf.order.dao;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.hhf.model.order.Payment;

public interface PaymentMapper {

	/**
	 * This method was generated by MyBatis Generator. This method corresponds to the database table T_PAYMENT
	 * @mbggenerated  Tue Aug 12 17:32:36 CST 2014
	 */
	int deleteByPrimaryKey(Long paymentId);

	/**
	 * This method was generated by MyBatis Generator. This method corresponds to the database table T_PAYMENT
	 * @mbggenerated  Tue Aug 12 17:32:36 CST 2014
	 */
	int insert(Payment record);

	/**
	 * This method was generated by MyBatis Generator. This method corresponds to the database table T_PAYMENT
	 * @mbggenerated  Tue Aug 12 17:32:36 CST 2014
	 */
	int insertSelective(Payment record);

	/**
	 * This method was generated by MyBatis Generator. This method corresponds to the database table T_PAYMENT
	 * @mbggenerated  Tue Aug 12 17:32:36 CST 2014
	 */
	Payment selectByPrimaryKey(Long paymentId);

	/**
	 * This method was generated by MyBatis Generator. This method corresponds to the database table T_PAYMENT
	 * @mbggenerated  Tue Aug 12 17:32:36 CST 2014
	 */
	int updateByPrimaryKeySelective(Payment record);

	/**
	 * This method was generated by MyBatis Generator. This method corresponds to the database table T_PAYMENT
	 * @mbggenerated  Tue Aug 12 17:32:36 CST 2014
	 */
	int updateByPrimaryKey(Payment record);
	
	int updateByPrimaryKeyPaying(Payment record);
	
	List<Payment>getPaymentExpired(@Param("days") int days);
}