package com.zeropick.commerceservice.service;

import com.zeropick.commerceservice.dto.CommerceDtos.CartAdd;
import com.zeropick.commerceservice.dto.CommerceDtos.CartItemResponse;
import com.zeropick.commerceservice.entity.CartItem;
import com.zeropick.commerceservice.feign.ProductClient;
import com.zeropick.commerceservice.kafka.AvroEventPublisher;
import com.zeropick.commerceservice.repository.CartItemRepository;
import com.zeropick.commerceservice.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final CartItemRepository cartItemRepository;
    private final ProductClient productClient;
    private final AvroEventPublisher eventPublisher;

    public CartService(CartItemRepository cartItemRepository, ProductClient productClient,
                       AvroEventPublisher eventPublisher) {
        this.cartItemRepository = cartItemRepository;
        this.productClient = productClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CartItemResponse add(CartAdd req) {
        CartItem item = cartItemRepository.findByMemberIdAndProductId(req.memberId(), req.productId())
                .map(existing -> {
                    existing.setQty(existing.getQty() + req.qty());
                    return existing;
                })
                .orElseGet(() -> cartItemRepository.save(new CartItem(req.memberId(), req.productId(), req.qty())));

        publishCartAdded(req);
        return CartItemResponse.of(item);
    }

    public List<CartItemResponse> list(Long memberId) {
        return cartItemRepository.findByMemberIdOrderByAddedAt(memberId).stream()
                .map(CartItemResponse::of)
                .toList();
    }

    @Transactional
    public CartItemResponse updateQty(Long cartItemId, int qty) {
        CartItem item = find(cartItemId);
        item.setQty(qty);
        return CartItemResponse.of(item);
    }

    @Transactional
    public void remove(Long cartItemId) {
        cartItemRepository.delete(find(cartItemId));
    }

    private CartItem find(Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "없는 항목입니다"));
    }

    private void publishCartAdded(CartAdd req) {
        try {
            String category = productClient.get(req.productId()).category();
            eventPublisher.publishCartAdded(req.memberId(), req.productId(), category,
                    req.qty(), System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("CART_ADDED 발행 스킵 productId={}: {}", req.productId(), e.getMessage());
        }
    }
}
