package com.zeropick.commerceservice.web;

import com.zeropick.commerceservice.dto.CommerceDtos.OrderCreate;
import com.zeropick.commerceservice.dto.CommerceDtos.OrderResponse;
import com.zeropick.commerceservice.dto.CommerceDtos.PayRequest;
import com.zeropick.commerceservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/commerce-service/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderCreate req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(req));
    }

    @GetMapping
    public List<OrderResponse> list(@RequestParam Long memberId) {
        return orderService.listByMember(memberId);
    }

    @PostMapping("/{orderId}/pay")
    public OrderResponse pay(@PathVariable Long orderId, @Valid @RequestBody PayRequest req) {
        return orderService.pay(orderId, req.paymentMethod());
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(@PathVariable Long orderId) {
        return orderService.cancel(orderId);
    }
}
