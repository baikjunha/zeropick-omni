package com.zeropick.commerceservice.web;

import com.zeropick.commerceservice.dto.CommerceDtos.CartAdd;
import com.zeropick.commerceservice.dto.CommerceDtos.CartItemResponse;
import com.zeropick.commerceservice.dto.CommerceDtos.CartQty;
import com.zeropick.commerceservice.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/commerce-service/carts")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    public ResponseEntity<CartItemResponse> add(@Valid @RequestBody CartAdd req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.add(req));
    }

    @GetMapping("/{memberId}")
    public List<CartItemResponse> list(@PathVariable Long memberId) {
        return cartService.list(memberId);
    }

    @PutMapping("/{cartItemId}")
    public CartItemResponse updateQty(@PathVariable Long cartItemId, @Valid @RequestBody CartQty req) {
        return cartService.updateQty(cartItemId, req.qty());
    }

    @DeleteMapping("/{cartItemId}")
    public ResponseEntity<Void> remove(@PathVariable Long cartItemId) {
        cartService.remove(cartItemId);
        return ResponseEntity.noContent().build();
    }
}
