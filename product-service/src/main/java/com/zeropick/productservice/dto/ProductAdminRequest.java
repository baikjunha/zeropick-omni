package com.zeropick.productservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record ProductAdminRequest(
        @NotBlank String name,
        @NotBlank String brand,
        @NotBlank String category,
        @NotNull @Min(0) Integer price,
        @NotNull @Min(0) Integer stock,
        String claimType,
        BigDecimal kcal,
        BigDecimal sugarG,
        BigDecimal carbG,
        String imageUrl,
        List<String> sweeteners,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal sodiumMg,
        BigDecimal servingSize,
        String servingUnit,
        String nutritionFactsUrl
) {
}
