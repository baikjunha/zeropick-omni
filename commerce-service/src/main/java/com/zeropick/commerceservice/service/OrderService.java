package com.zeropick.commerceservice.service;

import com.zeropick.commerceservice.dto.CommerceDtos.OrderCreate;
import com.zeropick.commerceservice.dto.CommerceDtos.OrderCreateItem;
import com.zeropick.commerceservice.dto.CommerceDtos.OrderResponse;
import com.zeropick.commerceservice.entity.Order;
import com.zeropick.commerceservice.entity.OrderItem;
import com.zeropick.commerceservice.feign.ProductClient;
import com.zeropick.commerceservice.feign.StockClient;
import com.zeropick.commerceservice.feign.ProductClient.ProductInfo;
import com.zeropick.commerceservice.kafka.AvroEventPublisher;
import com.zeropick.commerceservice.repository.OrderItemRepository;
import com.zeropick.commerceservice.repository.OrderRepository;
import com.zeropick.commerceservice.web.ApiException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductClient productClient;
    private final StockClient stockClient;
    private final AvroEventPublisher eventPublisher;
    private final AtomicLong orderNoSeq = new AtomicLong(1000);

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                        ProductClient productClient, StockClient stockClient, AvroEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productClient = productClient;
        this.stockClient = stockClient;
        this.eventPublisher = eventPublisher;
    }

    // 주문 생성은 PENDING 까지 — 재고 차감은 결제 시점에 한다.
    @Transactional
    public OrderResponse create(OrderCreate req) {
        long total = 0;
        List<ProductInfo> snapshots = new ArrayList<>();
        for (OrderCreateItem item : req.items()) {
            ProductInfo info = fetchProduct(item.productId());
            snapshots.add(info);
            total += (long) info.price() * item.qty();
        }
        Order order = orderRepository.save(
                new Order("ZP" + orderNoSeq.incrementAndGet(), req.memberId(), total));

        List<OrderItem> items = new ArrayList<>();
        for (int i = 0; i < req.items().size(); i++) {
            OrderCreateItem item = req.items().get(i);
            ProductInfo info = snapshots.get(i);
            items.add(orderItemRepository.save(new OrderItem(
                    order.getId(), item.productId(), info.name(), item.qty(), (long) info.price())));
        }
        return OrderResponse.of(order, items);
    }

    // 결제 승인: 품목별 재고 차감(OpenFeign). 하나라도 실패하면 이미 차감한 품목을 복구하고 CANCELLED + 409.
    // noRollbackFor: 409 를 던져도 CANCELLED 전이는 커밋되어야 한다 (롤백되면 PENDING 으로 남는 버그).
    @Transactional(noRollbackFor = ApiException.class)
    public OrderResponse pay(Long orderId, String paymentMethod) {
        Order order = find(orderId);
        if (!Order.Status.PENDING.name().equals(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS",
                    "결제 가능한 상태가 아닙니다: " + order.getStatus());
        }
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        List<OrderItem> deducted = new ArrayList<>();

        for (OrderItem item : items) {
            try {
                stockClient.deduct(item.getProductId(), Map.of("qty", item.getQty()));
                deducted.add(item);
            } catch (FeignException.Conflict e) {
                rollbackDeducted(deducted);
                order.markCancelled();
                throw new ApiException(HttpStatus.CONFLICT, "OUT_OF_STOCK",
                        "재고 부족: " + item.getProductName());
            }
        }
        order.markPaid(paymentMethod);

        long now = System.currentTimeMillis();
        for (OrderItem item : deducted) {
            // 이벤트의 카테고리는 상품 스냅샷에서 — 실패해도 발행은 계속한다
            String category = null;
            try {
                category = fetchProduct(item.getProductId()).category();
            } catch (Exception ignored) {
            }
            eventPublisher.publishOrderCompleted(order.getMemberId(), item.getProductId(),
                    category, item.getQty(), item.getUnitPrice(),
                    order.getOrderNo(), paymentMethod, now);
        }
        return OrderResponse.of(order, items);
    }

    // 주문 취소 (핵심 7 주문 CRUD): PAID 취소는 재고를 복구한다.
    @Transactional
    public OrderResponse cancel(Long orderId) {
        Order order = find(orderId);
        String status = order.getStatus();
        if (Order.Status.CANCELLED.name().equals(status) || Order.Status.COMPLETED.name().equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS", "취소할 수 없는 상태입니다: " + status);
        }
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        if (Order.Status.PAID.name().equals(status)) {
            rollbackDeducted(items);
        }
        order.markCancelled();
        return OrderResponse.of(order, items);
    }

    public List<OrderResponse> listByMember(Long memberId) {
        return orderRepository.findByMemberIdOrderByOrderedAtDesc(memberId).stream()
                .map(o -> OrderResponse.of(o, orderItemRepository.findByOrderId(o.getId())))
                .toList();
    }

    private Order find(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "없는 주문입니다"));
    }

    private ProductInfo fetchProduct(Long productId) {
        try {
            return productClient.get(productId);
        } catch (FeignException.NotFound e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "없는 상품입니다: " + productId);
        }
    }

    private void rollbackDeducted(List<OrderItem> items) {
        for (OrderItem item : items) {
            try {
                stockClient.restore(item.getProductId(), Map.of("qty", item.getQty()));
            } catch (Exception e) {
                log.error("재고 복구 실패 productId={} qty={}: {}", item.getProductId(), item.getQty(), e.getMessage());
            }
        }
    }
}
