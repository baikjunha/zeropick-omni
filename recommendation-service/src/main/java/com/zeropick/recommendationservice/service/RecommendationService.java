package com.zeropick.recommendationservice.service;

import com.zeropick.recommendationservice.client.ProductServiceClient;
import com.zeropick.recommendationservice.client.dto.ProductResponse;
import com.zeropick.recommendationservice.domain.BehaviorLog;
import com.zeropick.recommendationservice.domain.Preference;
import com.zeropick.recommendationservice.domain.RecoResult;
import com.zeropick.recommendationservice.dto.RecoDetailResponse;
import com.zeropick.recommendationservice.dto.RecoResponse;
import com.zeropick.recommendationservice.repository.BehaviorLogRepository;
import com.zeropick.recommendationservice.repository.PreferenceRepository;
import com.zeropick.recommendationservice.repository.RecoResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private final BehaviorLogRepository behaviorLogRepository;
    private final RecoResultRepository recoResultRepository;
    private final PreferenceRepository preferenceRepository;
    private final ProductServiceClient productServiceClient;

    @Transactional
    public List<RecoResponse> calculateAndGetRecommendations(Long memberId) {
        long startTime = System.currentTimeMillis();

        List<BehaviorLog> logs = behaviorLogRepository.findByMemberIdOrderByOccurredAtDesc(memberId);

        if (logs.isEmpty()) {
            return generatePreferenceBasedRecommendations(memberId);
        }

        Map<Long, Double> productScores = new HashMap<>();
        Map<Long, String> productReasons = new HashMap<>();

        for (BehaviorLog logItem : logs) {
            Long productId = logItem.getProductId();
            if (productId == null) continue;

            double weight = switch (logItem.getEventType()) {
                case "ORDER_COMPLETED" -> 50.0;
                case "PRODUCT_VIEWED" -> 1.0;
                default -> 0.0;
            };

            productScores.put(productId, productScores.getOrDefault(productId, 0.0) + weight);

            if ("ORDER_COMPLETED".equals(logItem.getEventType())) {
                productReasons.put(productId, "최근 구매한 상품 기반 추천");
            } else if (!productReasons.containsKey(productId)) {
                productReasons.put(productId, "최근 조회/관심 상품 기반 추천");
            }
        }

        List<Map.Entry<Long, Double>> sortedList = productScores.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(3)
                .toList();

        if (sortedList.isEmpty()) {
            return generatePreferenceBasedRecommendations(memberId);
        }

        List<RecoResult> newResults = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<Long, Double> entry : sortedList) {
            newResults.add(RecoResult.builder()
                    .memberId(memberId)
                    .productId(entry.getKey())
                    .rankNo(rank++)
                    .score(BigDecimal.valueOf(entry.getValue()))
                    .reason(productReasons.getOrDefault(entry.getKey(), "사용자 맞춤 추천"))
                    .build());
        }

        recoResultRepository.deleteByMemberId(memberId);
        List<RecoResult> saved = recoResultRepository.saveAll(newResults);

        long duration = System.currentTimeMillis() - startTime;
        log.info("[행동 로그 기반 추천 완료] memberId={}, 추천 상품 수={}, 소요시간={}ms", memberId, saved.size(), duration);

        return saved.stream()
                .map(RecoResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<RecoResponse> generatePreferenceBasedRecommendations(Long memberId) {
        Optional<Preference> prefOpt = preferenceRepository.findById(memberId);

        String category = null;
        String excludeSweetener = null;
        String excludeAllergen = null;

        if (prefOpt.isPresent()) {
            Preference pref = prefOpt.get();

            if (pref.getCategories() != null && !pref.getCategories().isEmpty()) {
                var firstCat = pref.getCategories().get(0);
                if (firstCat != null && firstCat.getId() != null) {
                    category = firstCat.getId().getCategory();
                }
            }
            if (pref.getExcludedSweeteners() != null && !pref.getExcludedSweeteners().isEmpty()) {
                var firstSweetener = pref.getExcludedSweeteners().get(0);
                if (firstSweetener != null && firstSweetener.getId() != null) {
                    excludeSweetener = firstSweetener.getId().getSweetener();
                }
            }
            if (pref.getAllergens() != null && !pref.getAllergens().isEmpty()) {
                var firstAllergen = pref.getAllergens().get(0);
                if (firstAllergen != null && firstAllergen.getId() != null) {
                    excludeAllergen = firstAllergen.getId().getAllergen();
                }
            }
        }

        log.info("[콜드스타트 추천 계산] memberId={}, category={}, excludeSweetener={}, excludeAllergen={}",
                memberId, category, excludeSweetener, excludeAllergen);

        List<ProductResponse> products;
        try {
            products = productServiceClient.getProducts(
                    category, excludeSweetener, excludeAllergen,
                    null, null, null, null, null
            );
            if (products == null) products = Collections.emptyList();
        } catch (Exception e) {
            log.error("[콜드스타트] product-service 조회 실패: {}", e.getMessage());
            products = Collections.emptyList();
        }

        List<RecoResult> newResults = new ArrayList<>();
        int rank = 1;
        for (ProductResponse p : products.stream().limit(3).toList()) {
            newResults.add(RecoResult.builder()
                    .memberId(memberId)
                    .productId(p.getId())
                    .rankNo(rank++)
                    .score(BigDecimal.valueOf(10.0))
                    .reason(category != null ? "선호 카테고리(" + category + ") 맞춤 추천" : "온보딩 선호 맞춤 추천")
                    .build());
        }

        recoResultRepository.deleteByMemberId(memberId);
        List<RecoResult> saved = recoResultRepository.saveAll(newResults);
        return saved.stream().map(RecoResponse::from).collect(Collectors.toList());
    }

    public List<RecoDetailResponse> getDetailedRecommendations(Long memberId) {
        List<RecoResult> recoResults = recoResultRepository.findByMemberIdOrderByRankNoAsc(memberId);
        if (recoResults.isEmpty()) {
            calculateAndGetRecommendations(memberId);
            recoResults = recoResultRepository.findByMemberIdOrderByRankNoAsc(memberId);
        }

        if (recoResults.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> productIds = recoResults.stream()
                .map(RecoResult::getProductId)
                .toList();

        Map<Long, ProductResponse> productMap = new HashMap<>();
        try {
            List<ProductResponse> products = productServiceClient.getProductsByIds(productIds);
            for (ProductResponse p : products) {
                productMap.put(p.getId(), p);
            }
        } catch (Exception e) {
            log.error("[Feign 실패] product-service 호출 실패: {}", e.getMessage());
        }

        return recoResults.stream()
                .map(r -> RecoDetailResponse.of(r, productMap.get(r.getProductId())))
                .collect(Collectors.toList());
    }
}
