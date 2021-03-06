package com.hhf.order.dao;

import java.util.List;

import com.hhf.model.order.ReturnOrderItem;

public interface ReturnOrderItemMapper {
    int deleteByPrimaryKey(Integer retOrderItemId);

    int insert(ReturnOrderItem record);

    int insertSelective(ReturnOrderItem record);

    ReturnOrderItem selectByPrimaryKey(Integer retOrderItemId);

    int updateByPrimaryKeySelective(ReturnOrderItem record);

    int updateByPrimaryKey(ReturnOrderItem record);
    
    public List<ReturnOrderItem> getRetOrderItemsByRetOrderId(Long retOrderId);
}