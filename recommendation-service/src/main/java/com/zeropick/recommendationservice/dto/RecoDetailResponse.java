package com.zeropick.recommendationservice.dto;

import com.zeropick.recommendationservice.client.dto.ProductResponse;
import com.zeropick.recommendationservice.domain.RecoResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecoDetailResponse {

    private Long productId;
    private Integer rankNo;
    private BigDecimal score;
    private String reason;

    private String productName;
    private String brand;
    private String category;
    private Integer price;
    private String imageUrl;
    private BigDecimal kcal;
    private BigDecimal sugarG;
    private List<String> sweeteners;

    public static RecoDetailResponse of(RecoResult result, ProductResponse product) {
        return RecoDetailResponse.builder()
                .productId(result.getProductId())
                .rankNo(result.getRankNo())
                .score(result.getScore())
                .reason(result.getReason())
                .productName(product != null ? product.getName() : null)
                .brand(product != null ? product.getBrand() : null)
                .category(product != null ? product.getCategory() : null)
                .price(product != null ? product.getPrice() : null)
                .imageUrl(product != null ? product.getImageUrl() : null)
                .kcal(product != null ? product.getKcal() : null)
                .sugarG(product != null ? product.getSugarG() : null)
                .sweeteners(product != null ? product.getSweeteners() : null)
                .build();
    }
}
