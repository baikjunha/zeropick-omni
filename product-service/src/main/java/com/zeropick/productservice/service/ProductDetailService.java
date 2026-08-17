package com.zeropick.productservice.service;

import com.zeropick.productservice.domain.Product;
import com.zeropick.productservice.domain.ProductAllergen;
import com.zeropick.productservice.domain.ProductSweetener;
import com.zeropick.productservice.domain.Sweetener;
import com.zeropick.productservice.dto.ProductDetailResponse;
import com.zeropick.productservice.exception.ProductNotFoundException;
import com.zeropick.productservice.repository.ProductAllergenRepository;
import com.zeropick.productservice.repository.ProductRepository;
import com.zeropick.productservice.repository.ProductSweetenerRepository;
import com.zeropick.productservice.repository.SweetenerRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductDetailService {

    private final StockOverlay stockOverlay;

    private final ProductRepository productRepository;
    private final ProductSweetenerRepository productSweetenerRepository;
    private final ProductAllergenRepository productAllergenRepository;
    private final SweetenerRepository sweetenerRepository;

    public ProductDetailService(ProductRepository productRepository,
                                 ProductSweetenerRepository productSweetenerRepository,
                                 ProductAllergenRepository productAllergenRepository,
                                 SweetenerRepository sweetenerRepository,
                                StockOverlay stockOverlay) {
        this.stockOverlay = stockOverlay;
        this.productRepository = productRepository;
        this.productSweetenerRepository = productSweetenerRepository;
        this.productAllergenRepository = productAllergenRepository;
        this.sweetenerRepository = sweetenerRepository;
    }

    public List<ProductDetailResponse> compare(String idsParam) {
        List<Long> ids = Arrays.stream(idsParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());

        List<ProductDetailResponse> results = new ArrayList<>();
        for (Long id : ids) {
            try {
                results.add(getDetail(id));
            } catch (ProductNotFoundException e) {
                // 존재하지 않는 id는 조용히 스킵
            }
        }
        return results;
    }

    public ProductDetailResponse getDetail(Long id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        stockOverlay.overlayOne(p);

        List<ProductSweetener> productSweeteners = productSweetenerRepository.findAll().stream()
                .filter(ps -> ps.getId().getProductId().equals(p.getId()))
                .collect(Collectors.toList());

        Map<Long, String> sweetenerNameById = sweetenerRepository.findAll().stream()
                .collect(Collectors.toMap(Sweetener::getId, Sweetener::getName));

        List<String> sweetenerNames = productSweeteners.stream()
                .map(ps -> sweetenerNameById.get(ps.getId().getSweetenerId()))
                .filter(name -> name != null)
                .collect(Collectors.toList());

        List<ProductDetailResponse.SweetenerAmount> sweetenerAmounts = productSweeteners.stream()
                .map(ps -> new ProductDetailResponse.SweetenerAmount(
                        sweetenerNameById.get(ps.getId().getSweetenerId()),
                        ps.getAmountG()))
                .collect(Collectors.toList());

        List<String> allergens = productAllergenRepository.findAll().stream()
                .filter(pa -> pa.getId().getProductId().equals(p.getId()))
                .map(pa -> pa.getId().getAllergen())
                .collect(Collectors.toList());

        return new ProductDetailResponse(
                p.getId(), p.getName(), p.getBrand(), p.getCategory(), p.getPrice(),
                p.getImageUrl(), p.getStock(), p.getClaimType(), p.getKcal(),
                p.getSugarG(), p.getCarbG(), sweetenerNames, allergens,
                p.getProteinG(), p.getFatG(), p.getSodiumMg(), p.getServingSize(),
                p.getServingUnit(), p.getNutritionFactsUrl(), sweetenerAmounts,
                p.getVerificationSource(), p.getCreatedAt()
        );
    }
}
