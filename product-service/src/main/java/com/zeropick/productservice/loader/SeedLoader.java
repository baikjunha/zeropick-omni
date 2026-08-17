package com.zeropick.productservice.loader;

import com.zeropick.productservice.domain.Product;
import com.zeropick.productservice.domain.ProductAllergen;
import com.zeropick.productservice.domain.ProductSweetener;
import com.zeropick.productservice.domain.Sweetener;
import com.zeropick.productservice.repository.ProductAllergenRepository;
import com.zeropick.productservice.repository.ProductRepository;
import com.zeropick.productservice.repository.ProductSweetenerRepository;
import com.zeropick.productservice.repository.SweetenerRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class SeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    private final ProductRepository productRepository;
    private final SweetenerRepository sweetenerRepository;
    private final ProductSweetenerRepository productSweetenerRepository;
    private final ProductAllergenRepository productAllergenRepository;

    public SeedLoader(ProductRepository productRepository,
                       SweetenerRepository sweetenerRepository,
                       ProductSweetenerRepository productSweetenerRepository,
                       ProductAllergenRepository productAllergenRepository) {
        this.productRepository = productRepository;
        this.sweetenerRepository = sweetenerRepository;
        this.productSweetenerRepository = productSweetenerRepository;
        this.productAllergenRepository = productAllergenRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (productRepository.count() > 0) {
            log.info("[SeedLoader] product 테이블에 이미 데이터가 있어 시드 로딩을 건너뜁니다.");
            return;
        }

        Map<String, Sweetener> sweetenerCache = new HashMap<>();
        int loaded = 0;
        int skippedMissingNutrition = 0;
        int skippedParseError = 0;

        java.io.InputStream is = new ClassPathResource("data/seed-product.csv").getInputStream();
        java.io.PushbackInputStream pis = new java.io.PushbackInputStream(is, 3);
        byte[] bom = new byte[3];
        int read = pis.read(bom, 0, 3);
        if (read != 3 || bom[0] != (byte) 0xEF || bom[1] != (byte) 0xBB || bom[2] != (byte) 0xBF) {
            pis.unread(bom, 0, read);
        }
        Reader reader = new InputStreamReader(pis, StandardCharsets.UTF_8);

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .build();

        try (CSVParser parser = new CSVParser(reader, format)) {
            for (CSVRecord record : parser) {
                try {
                    BigDecimal kcal = parseBigDecimal(record.get("kcal"));
                    BigDecimal sugarG = parseBigDecimal(record.get("sugar_g"));
                    BigDecimal carbG = parseBigDecimal(record.get("carb_g"));

                    if (kcal == null || sugarG == null || carbG == null) {
                        skippedMissingNutrition++;
                        continue;
                    }

                    String claimType = emptyToNull(record.get("claim_type"));

                    Product product = new Product(
                            record.get("name"),
                            record.get("brand"),
                            record.get("category"),
                            parseInt(record.get("price")),
                            claimType,
                            kcal,
                            sugarG,
                            carbG
                    );
                    product.setImageUrl(emptyToNull(record.get("image_url")));
                    product.setProteinG(parseBigDecimal(record.get("protein_g")));
                    product.setFatG(parseBigDecimal(record.get("fat_g")));
                    product.setSodiumMg(parseBigDecimal(record.get("sodium_mg")));
                    product.setServingSize(parseBigDecimal(record.get("serving_size")));
                    product.setServingUnit(emptyToNull(record.get("serving_unit")));
                    product.setNutritionFactsUrl(emptyToNull(record.get("nutrition_facts_url")));
                    product.setVerificationSource(emptyToNull(record.get("verification_source(제품 상세페이지 URL)")));

                    Integer stock = parseInt(record.get("stock"));
                    product.setStock(stock != null ? stock : 0);

                    Product saved = productRepository.save(product);

                    loadSweeteners(saved, record, sweetenerCache);
                    loadAllergens(saved, record);

                    loaded++;
                } catch (Exception e) {
                    skippedParseError++;
                    log.warn("[SeedLoader] 행 파싱 실패, 스킵: {} - {}", record.get("name"), e.getMessage());
                }
            }
        }

        log.info("[SeedLoader] 완료 — 로딩 {}건 / 영양정보 누락 스킵 {}건 / 파싱 에러 스킵 {}건",
                loaded, skippedMissingNutrition, skippedParseError);
    }

    private void loadSweeteners(Product product, CSVRecord record, Map<String, Sweetener> cache) {
        String names = record.get("sweeteners(쉼표구분)");
        String amounts = record.get("sweetener_amounts(이름:g)");
        if (names == null || names.isBlank()) {
            return;
        }

        Map<String, BigDecimal> amountMap = parseAmountMap(amounts);
        String[] nameArr = names.split(",");

        for (String rawName : nameArr) {
            String name = rawName.trim();
            if (name.isEmpty()) continue;

            Sweetener sweetener = cache.computeIfAbsent(name, key ->
                    sweetenerRepository.findAll().stream()
                            .filter(s -> s.getName().equals(key))
                            .findFirst()
                            .orElseGet(() -> sweetenerRepository.save(new Sweetener(key)))
            );

            BigDecimal amountG = amountMap.get(name);
            productSweetenerRepository.save(
                    new ProductSweetener(product.getId(), sweetener.getId(), amountG)
            );
        }
    }

    private void loadAllergens(Product product, CSVRecord record) {
        String allergens = record.get("allergens(쉼표구분)");
        if (allergens == null || allergens.isBlank()) {
            return;
        }
        for (String rawAllergen : allergens.split(",")) {
            String allergen = rawAllergen.trim();
            if (allergen.isEmpty()) continue;
            productAllergenRepository.save(new ProductAllergen(product.getId(), allergen));
        }
    }

    private Map<String, BigDecimal> parseAmountMap(String amounts) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (amounts == null || amounts.isBlank()) {
            return map;
        }
        for (String pair : amounts.split(",")) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                try {
                    map.put(kv[0].trim(), new BigDecimal(kv[1].trim()));
                } catch (NumberFormatException ignored) {

                }
            }
        }
        return map;
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return (int) Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
