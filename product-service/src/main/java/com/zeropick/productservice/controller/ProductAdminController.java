package com.zeropick.productservice.controller;

import com.zeropick.productservice.domain.Product;
import com.zeropick.productservice.dto.ProductAdminRequest;
import com.zeropick.productservice.exception.ProductNotFoundException;
import com.zeropick.productservice.service.ProductAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/product-service/products")
public class ProductAdminController {

    private final ProductAdminService productAdminService;

    public ProductAdminController(ProductAdminService productAdminService) {
        this.productAdminService = productAdminService;
    }

    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody ProductAdminRequest request) {
        Product created = productAdminService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id:[0-9]+}")
    public Product update(@PathVariable Long id, @Valid @RequestBody ProductAdminRequest request) {
        return productAdminService.update(id, request);
    }

    @DeleteMapping("/{id:[0-9]+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productAdminService.delete(id);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(ProductNotFoundException e) {
        return e.getMessage();
    }
}
