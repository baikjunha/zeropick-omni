package com.zeropick.productservice.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "brand", nullable = false, length = 60)
    private String brand;

    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "stock", nullable = false)
    private Integer stock = 0;

    @Column(name = "claim_type", length = 20)
    private String claimType;

    @Column(name = "kcal", nullable = false, precision = 7, scale = 1)
    private BigDecimal kcal;

    @Column(name = "sugar_g", nullable = false, precision = 6, scale = 2)
    private BigDecimal sugarG;

    @Column(name = "carb_g", nullable = false, precision = 6, scale = 2)
    private BigDecimal carbG;

    @Column(name = "protein_g", precision = 6, scale = 2)
    private BigDecimal proteinG;

    @Column(name = "fat_g", precision = 6, scale = 2)
    private BigDecimal fatG;

    @Column(name = "sodium_mg", precision = 8, scale = 2)
    private BigDecimal sodiumMg;

    @Column(name = "serving_size", precision = 7, scale = 1)
    private BigDecimal servingSize;

    @Column(name = "serving_unit", length = 10)
    private String servingUnit;

    @Column(name = "nutrition_facts_url", length = 255)
    private String nutritionFactsUrl;

    @Column(name = "verification_source", length = 255)
    private String verificationSource;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Product() {

    }

    public Product(String name, String brand, String category, Integer price,
                    String claimType, BigDecimal kcal, BigDecimal sugarG, BigDecimal carbG) {
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.price = price;
        this.claimType = claimType;
        this.kcal = kcal;
        this.sugarG = sugarG;
        this.carbG = carbG;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getClaimType() {
        return claimType;
    }

    public void setClaimType(String claimType) {
        this.claimType = claimType;
    }

    public BigDecimal getKcal() {
        return kcal;
    }

    public void setKcal(BigDecimal kcal) {
        this.kcal = kcal;
    }

    public BigDecimal getSugarG() {
        return sugarG;
    }

    public void setSugarG(BigDecimal sugarG) {
        this.sugarG = sugarG;
    }

    public BigDecimal getCarbG() {
        return carbG;
    }

    public void setCarbG(BigDecimal carbG) {
        this.carbG = carbG;
    }

    public BigDecimal getProteinG() {
        return proteinG;
    }

    public void setProteinG(BigDecimal proteinG) {
        this.proteinG = proteinG;
    }

    public BigDecimal getFatG() {
        return fatG;
    }

    public void setFatG(BigDecimal fatG) {
        this.fatG = fatG;
    }

    public BigDecimal getSodiumMg() {
        return sodiumMg;
    }

    public void setSodiumMg(BigDecimal sodiumMg) {
        this.sodiumMg = sodiumMg;
    }

    public BigDecimal getServingSize() {
        return servingSize;
    }

    public void setServingSize(BigDecimal servingSize) {
        this.servingSize = servingSize;
    }

    public String getServingUnit() {
        return servingUnit;
    }

    public void setServingUnit(String servingUnit) {
        this.servingUnit = servingUnit;
    }

    public String getNutritionFactsUrl() {
        return nutritionFactsUrl;
    }

    public void setNutritionFactsUrl(String nutritionFactsUrl) {
        this.nutritionFactsUrl = nutritionFactsUrl;
    }

    public String getVerificationSource() {
        return verificationSource;
    }

    public void setVerificationSource(String verificationSource) {
        this.verificationSource = verificationSource;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
