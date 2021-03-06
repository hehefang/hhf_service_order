package com.hhf.order.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.hhf.common.mybatis.Page;
import com.hhf.common.util.DateUtils;
import com.hhf.constants.order.OrderConstants;
import com.hhf.constants.product.ProductConstants;
import com.hhf.model.order.Order;
import com.hhf.model.order.OrderItem;
import com.hhf.model.order.OrderLog;
import com.hhf.model.user.User;
import com.hhf.model.user.UserAddress;
import com.hhf.order.dao.OrderItemMapper;
import com.hhf.order.dao.OrderLogMapper;
import com.hhf.order.dao.OrderMapper;
import com.hhf.order.util.InventoryException;
import com.hhf.param.cart.Trade;
import com.hhf.param.cart.TradeItem;
import com.hhf.param.order.OrderCondition;
import com.hhf.param.order.OrderInfo;
import com.hhf.service.order.IOrderService;
import com.hhf.service.product.IBrandShowService;
import com.hhf.service.product.IProductService;
import com.hhf.service.user.IAddressService;
import com.hhf.service.user.IUserService;

@Service("orderService")
public class OrderServiceImpl implements IOrderService {
	private final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

	@Autowired
	private OrderMapper orderMapper;
	@Autowired
	private OrderItemMapper orderItemMapper;
	@Autowired
	private OrderLogMapper orderLogMapper;

	@Autowired
	private IProductService productService;
	@Autowired
	private IAddressService addressService;
	@Autowired
	private IUserService userService;
	@Autowired
	private IBrandShowService brandShowService;

	@Autowired
	@Qualifier("redisNumber")
	private RedisTemplate<String, Map<Long, Long>> redisStock;

	@Autowired
	@Qualifier("redisNumber")
	private RedisTemplate<String, String> redisNumber;

	private final String STOCK_KEY_STR = "brandShowStock";

	public int addOrder() {
		Order order = new Order();
		order.setUserName("liubo");
		order.setCreatedDate(DateUtils.currentDate());
		return orderMapper.insert(order);
	}

	@Override
	public Order getOrderById(Long orderId) {
		return this.orderMapper.getOrderById(orderId);
	}

	@Override
	public int updateOrder2Sended(Order order) {
		int flag = this.orderMapper.updateByPrimaryKeySelective(order);

		// 记录操作日志
		if (flag > 0) {
			this.updOrderLog(order, OrderConstants.ORDER_STATUS_WAITDELIVERED,
					order.getStrOrderStatus(), order.getLastUpdateByName());
		}

		return flag;
	}

	@Override
	public List<Order> getOrdersByIdsAndUserId(Long[] orderIds, Long userId) {
		List<Order> orders = this.orderMapper.getOrdersByIdsAndUserId(orderIds,
				userId);
		return orders;
	}

	@Override
	public Page<Order> getOrdersByUserId(Long userId, Page<Order> page) {
		List<Order> orders = this.orderMapper.getOrdersByUserIdByPage(userId, page);
		if (orders != null && orders.size() > 0) {
			page.setResult(orders);

			this.getOrderItems(orders);
		}
		return page;
	}

	@Override
	public Page<Order> getOrdersByUserIdAndStatus(Long userId, String status,
			Page<Order> page) {
		List<Order> orders = this.orderMapper.getOrdersByUserIdAndStatusByPage(userId, status, page);
		if (orders != null && orders.size() > 0) {
			page.setResult(orders);
			this.getOrderItems(orders);
		}
		return page;
	}

	@Override
	public List<OrderInfo> batchSaveOrders(List<Trade> trades)
			throws InventoryException, Exception {
		List<OrderInfo> orderInfos = new ArrayList<OrderInfo>();
		for (Trade trade : trades) {
			OrderInfo orderInfo = this.saveOrder(trade);
			orderInfos.add(orderInfo);
		}
		return orderInfos;
	}

	@Override
	public int cancelOrderByIds(String optName, String cancelReason,
			Long... orderIds) {
		int re = 1;

		try {
			if (orderIds != null) {
				if (orderIds.length == 1) {
					Order order = this.orderMapper.getOrderById(orderIds[0]);

					this.cancelOrder(order, optName, cancelReason);
				} else if (orderIds.length > 1) {
					List<Order> orders = this.orderMapper
							.getOrdersByIds(orderIds);
					if (orders != null && orders.size() > 0) {
						for (Order order : orders) {
							this.cancelOrder(order, optName, cancelReason);
						}
					}
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			re = 0;
		}

		return re;
	}

	@Override
	public Page<Order> queryOrderByCondition(Map<String, ?> map,
			Page<Order> page) {
		page.setResult(this.orderMapper.queryOrderByPage(map, page));

		return page;
	}

	@Override
	public List<OrderItem> getOrderItemsByOrderId(Integer orderId) {
		return this.orderItemMapper.getOrderItemsByOrderId(orderId);
	}

	/**
	 * 根据检索条件查询订单
	 */
	public Page<Order> getOrdersByOrderConditon(OrderCondition orderCondition,
			Page<Order> page) {
		List<Order> orders = this.orderMapper.getOrdersByConditionPage(
				orderCondition, page);

		if (orders != null && orders.size() > 0) {
			page.setResult(orders);

			this.getOrderItems(orders);
		}

		return page;
	}

	private void getOrderItems(List<Order> orders) {
		List<Long> orderIds = new ArrayList<Long>(orders.size());
		for (Order order : orders) {
			orderIds.add(order.getOrderId());
		}

		List<OrderItem> orderItems = this.orderItemMapper
				.getOrderItemsByOrderIds(orderIds);

		Map<Long, List<OrderItem>> orderItemsMap = new HashMap<Long, List<OrderItem>>(
				orders.size());
		for (OrderItem orderItem : orderItems) {
			if (!orderItemsMap.containsKey(orderItem.getOrderId())) {
				orderItemsMap.put(orderItem.getOrderId(),
						new ArrayList<OrderItem>());
			}
			orderItemsMap.get(orderItem.getOrderId()).add(orderItem);
		}

		for (Order order : orders) {
			order.setOrderItems(orderItemsMap.get(order.getOrderId()));
		}
	}

	private OrderInfo saveOrder(Trade trade) throws InventoryException,
			Exception {
		OrderInfo orderInfo = new OrderInfo();
		UserAddress addr = this.addressService.getAddressById(trade
				.getAddressId());
		User user = this.userService.getUserById(trade.getUserId());
		if (null == addr) {
			orderInfo.setCode(-1);
			return orderInfo;
		}
		if (null == user) {
			orderInfo.setCode(-2);
			return orderInfo;
		}
		Order order = this.createOrder(trade, addr, user);
		this.orderMapper.insertSelective(order);
		Map<Long, Long> brandShowStockMapMq = new HashMap<Long, Long>();
		List<OrderItem> orderItems = this.createOrderItems(
				trade.getTradeItems(), order, brandShowStockMapMq);
		order.setOrderItems(orderItems);
		order.setOrderFee(order.getProdFee().add(order.getDeliverFee()));
		order.setOrderCode(order.getOrderId().toString());
		if (order.getOrderFee().compareTo(BigDecimal.ZERO) <= 0) {
			order.setOrderFee(BigDecimal.ZERO);
			order.setOrderStatus(OrderConstants.ORDER_STATUS_WAITDELIVERED);
		}

		this.orderMapper.updateByPrimaryKeySelective(order);
		this.createOrderLog(order, trade);

		this.updateRedisInventory(brandShowStockMapMq);
		this.brandShowService.addStock(brandShowStockMapMq);
		orderInfo.setCode(1);
		orderInfo.setOrder(order);
		return orderInfo;
	}

	private void updateRedisInventory(Map<Long, Long> brandShowStockMapMq)
			throws InventoryException {
		if (brandShowStockMapMq != null && brandShowStockMapMq.size() > 0) {
			Map<String, String> prevStock = new HashMap<String, String>();
			boolean outOfStock = false;
			Long outOfStockBSDId = 0l;
			for (Map.Entry<Long, Long> entry : brandShowStockMapMq.entrySet()) {
				Long brandShowDetailId = entry.getKey();
				Long increaseNum = entry.getValue();
				String redisKey = ProductConstants.CACHE_PERFIX_INVENTORY
						+ brandShowDetailId;
				prevStock.put(redisKey, String.valueOf(-increaseNum));
				Long currentInventory = this.redisNumber.opsForValue()
						.increment(redisKey, increaseNum);
				if (currentInventory < 0) {
					outOfStock = true;
					outOfStockBSDId = brandShowDetailId;
					break;
				}
			}
			if (outOfStock) {
				if (prevStock != null && prevStock.size() > 0) {
					for (Map.Entry<String, String> entry : prevStock.entrySet()) {
						String key = entry.getKey();
						String num = entry.getValue();
						this.redisNumber.opsForValue().increment(key,
								Long.parseLong(num));
					}
				}
				throw new InventoryException("brandShowDetail:"
						+ outOfStockBSDId + "库存不足！！");
			}
		}
	}

	private Order createOrder(Trade trade, UserAddress addr, User user) {
		Order order = new Order();
		Date now = new Date();
		order.setCreatedByName(trade.getOptName());
		order.setCreatedDate(now);
		order.setDeliverFee(trade.getDeliverFee() != null ? trade
				.getDeliverFee() : BigDecimal.ZERO);
		order.setDeliverDiscount(trade.getDeliverDiscountFee() != null ? trade
				.getDeliverDiscountFee() : BigDecimal.ZERO);
		order.setLastUpdateByName(trade.getOptName());
		order.setLastUpdateDate(now);
		order.setOrderSource(trade.getOrderSource());
		order.setOrderType(trade.getOrderType());
		order.setPayType(trade.getPayType());
		order.setPayMode(trade.getPayMode());
		order.setPayStatus(OrderConstants.PAY_STATUS_UNPAY);
		order.setOrderStatus(OrderConstants.ORDER_STATUS_WAITPAYMENT);

		order.setUserRemark(trade.getUserRemark());
		order.setSignedStatus(OrderConstants.SIGNED_STATUS_UNSIGNED);
		order.setSellerId(trade.getSellerId());
		order.setBrandShowId(trade.getBrandShowId());
		order.setBrandShowTitle(trade.getBrandShowTitle());
		order.setLogisticsCompa(null);
		order.setLogisticsName(null);
		order.setOrderCode(null);
		order.setOrderId(null);
		order.setProdFee(null);
		order.setOrderFee(null);
		/**** 地址 ***/
		order.setrAddr(addr.getAddr());
		order.setrCity(addr.getCityName());
		order.setrCounty(addr.getDistrictName());
		order.setrMobile(addr.getMobile());
		order.setrName(addr.getReceiver());
		order.setrPhone(addr.getTel());
		order.setrProvince(addr.getProvinceName());
		order.setrTown(addr.getTownName());
		order.setrZipcode(addr.getZipCode());
		order.setrEmail(user.getEmail());
		/**** 用户 ***/
		order.setUserId(user.getUserId());
		order.setUserName(user.getUserName());

		return order;
	}

	private List<OrderItem> createOrderItems(List<TradeItem> tradeItems,
			Order order, Map<Long, Long> brandShowStockMapMq)
			throws InventoryException, Exception {
		List<OrderItem> orderItems = new ArrayList<OrderItem>();
		if (tradeItems != null && tradeItems.size() > 0) {
			BigDecimal prodFee = BigDecimal.ZERO;
			BigDecimal discountFee = BigDecimal.ZERO;
			for (TradeItem tradeItem : tradeItems) {
				OrderItem orderItem = this.createOrderItem(tradeItem, order);
				if (orderItem != null) {
					orderItems.add(orderItem);
					BigDecimal transPrice = orderItem.getTransPrice() != null ? orderItem
							.getTransPrice() : BigDecimal.ZERO;
					BigDecimal salePrice = orderItem.getSalePrice() != null ? orderItem
							.getSalePrice() : BigDecimal.ZERO;
					prodFee = prodFee.add(transPrice.multiply(new BigDecimal(
							orderItem.getNumber())));
					discountFee = discountFee.add(salePrice
							.subtract(transPrice).multiply(
									new BigDecimal(orderItem.getNumber())));

					brandShowStockMapMq.put(tradeItem.getBrandShowDetailId(),
							-orderItem.getNumber());
				}
			}
			order.setProdDiscountFee(discountFee);
			order.setProdFee(prodFee);
			return orderItems;
		}
		return null;
	}

	private OrderItem createOrderItem(TradeItem tradeItem, Order order)
			throws InventoryException, Exception {
		if (tradeItem != null) {
			OrderItem orderItem = new OrderItem();
			orderItem.setIsComment(OrderConstants.ORDERITEM_COMMENT_NO);
			orderItem.setNumber(tradeItem.getNum());
			orderItem.setOrderCode(order.getOrderCode());
			orderItem.setOrderId(order.getOrderId().longValue());
			orderItem.setProdId(tradeItem.getProdId().longValue());
			orderItem.setProdCode(tradeItem.getProdCode());
			orderItem.setSkuId(tradeItem.getSkuId().longValue());
			orderItem.setSkuCode(tradeItem.getSkuCode());
			orderItem.setProdSpecId(tradeItem.getProdSpecId());
			orderItem.setProdSpecName(tradeItem.getProdSpecName());
			orderItem.setProdTitle(tradeItem.getProdTitle());
			orderItem.setProdImg(tradeItem.getProdImg());
			orderItem.setSalePrice(tradeItem.getMarketPrice());
			orderItem.setTransPrice(tradeItem.getShowPrice());
			orderItem.setStatus(OrderConstants.ORDERITEM_STATUS_NORMAL);
			orderItem.setSellerId(tradeItem.getSellerId());
			orderItem.setBcId(tradeItem.getBcId());
			orderItem.setBrandShowId(tradeItem.getBrandShowId());
			;
			orderItem.setBrandShowTitle(tradeItem.getBrandShowTitle());
			orderItem.setBsdId(tradeItem.getBrandShowDetailId());

			this.orderItemMapper.insertSelective(orderItem);
			return orderItem;
		}
		return null;
	}

	/**
	 * 创建订单日志
	 * 
	 * @param order
	 * @param trade
	 */
	private void createOrderLog(Order order, Trade trade) {
		if (order != null) {
			OrderLog orderLog = new OrderLog();
			orderLog.setOrderId(order.getOrderId());
			orderLog.setOrderCode(order.getOrderCode());
			orderLog.setOptTime(order.getCreatedDate());
			orderLog.setOptIp(trade.getUserIP());
			orderLog.setOptByName(trade.getOptName());
			orderLog.setOptContent("创建订单");
			orderLog.setFromOrderStatus(null);
			orderLog.setToOrderStatus(order.getOrderStatus());
			this.orderLogMapper.insert(orderLog);
		}

	}

	/**
	 * 取消订单
	 * 
	 * @param order
	 *            要取消的订单
	 * @param optName
	 *            操作人，如果为null，则默认订单创建人
	 * @param cancelReason
	 *            取消原因
	 * @return
	 */
	private int cancelOrder(Order order, String optName, String cancelReason) {
		// 订单不存在
		if (order == null) {
			return OrderConstants.ORDER_UPDATE_ORDER_NOT_EXIST;
		}
		// 订单状态不符合要求
		if (!OrderConstants.ORDER_STATUS_TOBEPROCESS.equals(order
				.getOrderStatus())
				&& !OrderConstants.ORDER_STATUS_WAITPAYMENT.equals(order
						.getOrderStatus())
				&& !OrderConstants.ORDER_STATUS_WAITDELIVERED.equals(order
						.getOrderStatus())
				&& !OrderConstants.ORDER_STATUS_DELIVERED.equals(order
						.getOrderStatus())) {
			return OrderConstants.ORDER_UPDATE_ORDER_STATUS_UNNORMAL;
		}

		// 原订单状态
		String oldStatus = order.getOrderStatus();
		// 新订单状态
		order.setOrderStatus(OrderConstants.ORDER_STATUS_CANCELLED);
		// 最近更新时间
		order.setLastUpdateDate(DateUtils.currentDate());
		// 取消时间
		order.setCancelDate(DateUtils.currentDate());
		// 最近更新人/取消人
		optName = StringUtils.defaultIfBlank(optName, order.getUserName());
		order.setLastUpdateByName(optName);
		order.setCancelByName(optName);
		// 取消原因
		order.setCancelReason(cancelReason);

		int code = this.orderMapper.updateByPrimaryKeySelective(order);
		if (code == 0) {
			return OrderConstants.ORDER_UPDATE_FAILURE;
		}

		try {
			this.restoreInventory(order.getOrderItems());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}

		this.updOrderLog(order, oldStatus, "取消订单", optName);

		return OrderConstants.ORDER_UPDATE_SUCCESS;
	}

	/**
	 * 取消订单还原库存
	 * 
	 * @param orderItems
	 * @throws InventoryException
	 */
	private void restoreInventory(List<OrderItem> orderItems)
			throws InventoryException {
		if (orderItems != null && orderItems.size() > 0) {
			Map<Long, Long> stockMapMq = new HashMap<Long, Long>();
			for (OrderItem orderItem : orderItems) {
				stockMapMq.put(orderItem.getBsdId(), orderItem.getNumber());
			}
			
			this.updateRedisInventory(stockMapMq);
			this.brandShowService.addStock(stockMapMq);
		}
	}

	/**
	 * 更改订单日志
	 * 
	 * @param order
	 * @param oldOrderStatus
	 * @param optName
	 */
	private void updOrderLog(Order order, String oldOrderStatus, String optMsg,
			String optName) {
		OrderLog orderLog = new OrderLog();
		orderLog.setFromOrderStatus(oldOrderStatus);
		orderLog.setOptByName(optName);
		orderLog.setOptContent(optMsg);
		orderLog.setOptTime(DateUtils.currentDate());
		orderLog.setOrderId(order.getOrderId());
		orderLog.setToOrderStatus(order.getOrderStatus());

		this.orderLogMapper.insert(orderLog);
	}

	@Override
	public List<Order> getOrdersByIds(Long[] orderIds) {
		return this.orderMapper.getOrdersByIds(orderIds);
	}

	@Override
	public OrderItem getOrderItemById(Long orderItemId) {
		return this.orderItemMapper.getOrderItemById(orderItemId);
	}

	@Override
	public Order getOrderByIdAndUser(Long orderId, Long userId) {
		return this.orderMapper.getOrderByIdAndUser(orderId, userId);
	}

	@Override
	public int cancelOrderByIdAndUser(Long orderId, Long userId,
			String userName, String cancelReason) {
		Date now = new Date();
		return this.orderMapper.cancelOrderByIdAndUser(orderId, userId,
				userName, cancelReason, now);
	}

	@Override
	public int confirmOrderByUser(Long orderId, Long userId, String userName) {
		Date now = new Date();
		return this.orderMapper.confirmOrderByUser(orderId, userId, userName,
				now);
	}

	@Override
	public int deleteOrderByUser(Long orderId, Long userId, String userName) {
		Date now = new Date();
		return this.orderMapper.deleteOrderByUser(orderId, userId, userName,
				now);
	}

	@Override
	public int getToBeProcessOrderCountOfSeller(int sellerId) {
		return orderMapper.getToBeProcessOrderCountOfSeller(sellerId);
	}
}
