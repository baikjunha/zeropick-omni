package com.zeropick.productservice.service;

import com.zeropick.productservice.domain.Product;
import com.zeropick.productservice.domain.ProductSweetener;
import com.zeropick.productservice.domain.Sweetener;
import com.zeropick.productservice.dto.ProductAdminRequest;
import com.zeropick.productservice.exception.ProductNotFoundException;
import com.zeropick.productservice.repository.ProductAllergenRepository;
import com.zeropick.productservice.repository.ProductRepository;
import com.zeropick.productservice.repository.ProductSweetenerRepository;
import com.zeropick.productservice.repository.SweetenerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductAdminService {

    private final ProductRepository productRepository;
    private final SweetenerRepository sweetenerRepository;
    private final ProductSweetenerRepository productSweetenerRepository;
    private final ProductAllergenRepository productAllergenRepository;

    public ProductAdminService(ProductRepository productRepository,
                                SweetenerRepository sweetenerRepository,
                                ProductSweetenerRepository productSweetenerRepository,
                                ProductAllergenRepository productAllergenRepository) {
        this.productRepository = productRepository;
        this.sweetenerRepository = sweetenerRepository;
        this.productSweetenerRepository = productSweetenerRepository;
        this.productAllergenRepository = productAllergenRepository;
    }

    @Transactional
    public Product create(ProductAdminRequest request) {
        Product product = new Product(
                request.name(),
                request.brand(),
                request.category(),
                request.price(),
                request.claimType(),
                request.kcal(),
                request.sugarG(),
                request.carbG()
        );
        product.setStock(request.stock());
        applyOptionalFields(product, request);
        Product saved = productRepository.save(product);

        linkSweeteners(saved.getId(), request.sweeteners());

        return saved;
    }

    @Transactional
    public Product update(Long id, ProductAdminRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        product.setName(request.name());
        product.setBrand(request.brand());
        product.setCategory(request.category());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setClaimType(request.claimType());
        product.setKcal(request.kcal());
        product.setSugarG(request.sugarG());
        product.setCarbG(request.carbG());
        applyOptionalFields(product, request);

        productSweetenerRepository.deleteByIdProductId(id);
        linkSweeteners(id, request.sweeteners());

        return product;
    }

    @Transactional
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productSweetenerRepository.deleteByIdProductId(id);
        productAllergenRepository.deleteByIdProductId(id);
        productRepository.deleteById(id);
    }

    private void applyOptionalFields(Product product, ProductAdminRequest request) {
        product.setImageUrl(request.imageUrl());
        product.setProteinG(request.proteinG());
        product.setFatG(request.fatG());
        product.setSodiumMg(request.sodiumMg());
        product.setServingSize(request.servingSize());
        product.setServingUnit(request.servingUnit());
        product.setNutritionFactsUrl(request.nutritionFactsUrl());
    }

    private void linkSweeteners(Long productId, List<String> sweetenerNames) {
        if (sweetenerNames == null || sweetenerNames.isEmpty()) {
            return;
        }
        for (String name : sweetenerNames) {
            Sweetener sweetener = sweetenerRepository.findAll().stream()
                    .filter(s -> s.getName().equals(name))
                    .findFirst()
                    .orElseGet(() -> sweetenerRepository.save(new Sweetener(name)));
            productSweetenerRepository.save(new ProductSweetener(productId, sweetener.getId(), null));
        }
    }
}
