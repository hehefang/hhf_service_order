package com.hhf.order.dao;

import com.hhf.model.order.OrderLog;

public interface OrderLogMapper {
    int deleteByPrimaryKey(Long orderLogId);

    int insert(OrderLog record);

    int insertSelective(OrderLog record);

    OrderLog selectByPrimaryKey(Long orderLogId);

    int updateByPrimaryKeySelective(OrderLog record);

    int updateByPrimaryKey(OrderLog record);
}