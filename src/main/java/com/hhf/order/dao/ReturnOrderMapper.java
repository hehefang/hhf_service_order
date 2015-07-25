package com.hhf.order.dao;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

import com.hhf.common.mybatis.Page;
import com.hhf.model.order.ReturnOrder;

public interface ReturnOrderMapper {
	int deleteByPrimaryKey(Integer retOrderId);

	int insert(ReturnOrder record);

	int insertSelective(ReturnOrder record);

	ReturnOrder selectByPrimaryKey(Integer retOrderId);

	int updateByPrimaryKeySelective(ReturnOrder record);

	int updateByPrimaryKey(ReturnOrder record);

	List<ReturnOrder> getRetOrdersByUserIdPage(@Param("userId") long userId,
			@Param("page") Page<ReturnOrder> page);

	ReturnOrder getRetOrderByIdUid(@Param("retOrderId") Long retOrderId,
			@Param("userId") Long uid);

	public List<ReturnOrder> getRetOrdersByPage(
			@Param("cond") Map<String, ?> cond,
			@Param("page") Page<ReturnOrder> page);

	int getToBeProcessRetOrderCountOfSeller(@Param("sellerId") int sellerId);
}