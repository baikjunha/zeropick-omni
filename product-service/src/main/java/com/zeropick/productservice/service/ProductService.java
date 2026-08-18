package com.zeropick.productservice.service;

import com.zeropick.productservice.domain.Product;
import com.zeropick.productservice.domain.ProductAllergen;
import com.zeropick.productservice.domain.ProductSweetener;
import com.zeropick.productservice.dto.ProductResponse;
import com.zeropick.productservice.repository.ProductAllergenRepository;
import com.zeropick.productservice.repository.ProductRepository;
import com.zeropick.productservice.repository.ProductSpecifications;
import com.zeropick.productservice.repository.ProductSweetenerRepository;
import com.zeropick.productservice.repository.SweetenerRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductSweetenerRepository productSweetenerRepository;
    private final ProductAllergenRepository productAllergenRepository;
    private final SweetenerRepository sweetenerRepository;
    private final StockOverlay stockOverlay;

    public ProductService(ProductRepository productRepository,
                           ProductSweetenerRepository productSweetenerRepository,
                           ProductAllergenRepository productAllergenRepository,
                           SweetenerRepository sweetenerRepository,
                           StockOverlay stockOverlay) {
        this.productRepository = productRepository;
        this.productSweetenerRepository = productSweetenerRepository;
        this.productAllergenRepository = productAllergenRepository;
        this.sweetenerRepository = sweetenerRepository;
        this.stockOverlay = stockOverlay;
    }

    public List<ProductResponse> findProducts(String category,
                                               String sweetenerExclude,
                                               String allergenExclude,
                                               BigDecimal sugarMax,
                                               BigDecimal kcalMin,
                                               BigDecimal kcalMax,
                                               String q,
                                               String sort) {

        List<String> excludedSweeteners = splitOrEmpty(sweetenerExclude);
        List<String> excludedAllergens = splitOrEmpty(allergenExclude);

        List<Specification<Product>> specs = new ArrayList<>();
        addIfNotNull(specs, ProductSpecifications.hasCategory(category));
        addIfNotNull(specs, ProductSpecifications.sugarLessThanOrEqual(sugarMax));
        addIfNotNull(specs, ProductSpecifications.kcalBetween(kcalMin, kcalMax));
        addIfNotNull(specs, ProductSpecifications.nameOrBrandContains(q));
        addIfNotNull(specs, ProductSpecifications.excludesSweeteners(excludedSweeteners));
        addIfNotNull(specs, ProductSpecifications.excludesAllergens(excludedAllergens));

        Specification<Product> spec = specs.isEmpty()
                ? Specification.unrestricted()
                : Specification.allOf(specs);

        List<Product> products = productRepository.findAll(spec);
        stockOverlay.overlay(products);

        Map<Long, List<ProductSweetener>> sweetenersByProduct = productSweetenerRepository.findAll().stream()
                .collect(Collectors.groupingBy(ps -> ps.getId().getProductId()));
        Map<Long, String> sweetenerNameById = sweetenerRepository.findAll().stream()
                .collect(Collectors.toMap(s -> s.getId(), s -> s.getName()));
        Map<Long, List<String>> allergensByProduct = productAllergenRepository.findAll().stream()
                .collect(Collectors.groupingBy(pa -> pa.getId().getProductId(),
                        Collectors.mapping(pa -> pa.getId().getAllergen(), Collectors.toList())));

        List<ProductResponse> responses = products.stream()
                .map(p -> toResponse(p,
                        sweetenersByProduct.getOrDefault(p.getId(), List.of()),
                        sweetenerNameById,
                        allergensByProduct.getOrDefault(p.getId(), List.of())))
                .collect(Collectors.toList());

        return applySort(responses, sort);
    }

    private void addIfNotNull(List<Specification<Product>> specs, Specification<Product> spec) {
        if (spec != null) specs.add(spec);
    }

    private List<String> splitOrEmpty(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private List<ProductResponse> applySort(List<ProductResponse> responses, String sort) {
        if (sort == null) return responses;
        Comparator<ProductResponse> comparator = switch (sort) {
            case "kcal" -> Comparator.comparing(ProductResponse::kcal);
            case "sugar" -> Comparator.comparing(ProductResponse::sugarG);
            case "price" -> Comparator.comparing(ProductResponse::price);
            default -> null;
        };
        if (comparator == null) return responses;
        return responses.stream().sorted(comparator).collect(Collectors.toList());
    }

    private ProductResponse toResponse(Product p,
                                        List<ProductSweetener> productSweeteners,
                                        Map<Long, String> sweetenerNameById,
                                        List<String> allergens) {
        List<String> sweetenerNames = productSweeteners.stream()
                .map(ps -> sweetenerNameById.get(ps.getId().getSweetenerId()))
                .filter(name -> name != null)
                .collect(Collectors.toList());

        List<ProductResponse.SweetenerAmount> sweetenerAmounts = productSweeteners.stream()
                .map(ps -> new ProductResponse.SweetenerAmount(
                        sweetenerNameById.get(ps.getId().getSweetenerId()),
                        ps.getAmountG()))
                .collect(Collectors.toList());

        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getBrand(),
                p.getCategory(),
                p.getPrice(),
                p.getImageUrl(),
                p.getStock(),
                p.getClaimType(),
                p.getKcal(),
                p.getSugarG(),
                p.getCarbG(),
                sweetenerNames,
                allergens,
                p.getProteinG(),
                p.getFatG(),
                p.getSodiumMg(),
                p.getServingSize(),
                p.getServingUnit(),
                p.getNutritionFactsUrl(),
                sweetenerAmounts
        );
    }
}
