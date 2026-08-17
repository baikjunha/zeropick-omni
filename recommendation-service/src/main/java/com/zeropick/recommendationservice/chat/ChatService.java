package com.zeropick.recommendationservice.chat;

import com.zeropick.recommendationservice.chat.dto.ChatRequest;
import com.zeropick.recommendationservice.chat.dto.ChatResponse;
import com.zeropick.recommendationservice.client.ProductServiceClient;
import com.zeropick.recommendationservice.client.dto.ProductResponse;
import com.zeropick.recommendationservice.domain.BehaviorLog;
import com.zeropick.recommendationservice.llm.LlmQueryService;
import com.zeropick.recommendationservice.llm.dto.LlmParseResult;
import com.zeropick.recommendationservice.parser.dto.SearchCondition;
import com.zeropick.recommendationservice.repository.BehaviorLogRepository;
import com.zeropick.recommendationservice.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final LlmQueryService llmQueryService;
    private final ProductServiceClient productServiceClient;
    private final BehaviorLogRepository behaviorLogRepository;
    private final MetricsService metricsService;

    public ChatResponse processChat(ChatRequest request) {
        long startTime = System.currentTimeMillis();

        String message = request != null ? request.getMessage() : "";
        Long memberId = request != null ? request.getMemberId() : null;

        LlmParseResult parseResult = llmQueryService.extractCondition(message);
        SearchCondition condition = parseResult.getCondition();
        boolean usedFallback = parseResult.isUsedFallback();

        metricsService.incrementChatRequest(usedFallback);

        List<ProductResponse> allProducts;
        try {
            allProducts = productServiceClient.getProducts(
                    condition.getCategory(),
                    condition.getSweetenerExclude(),
                    condition.getAllergenExclude(),
                    condition.getSugarMax(),
                    condition.getKcalMin(),
                    condition.getKcalMax(),
                    condition.getQuery(),
                    null
            );
            if (allProducts == null) {
                allProducts = Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("[product-service 통신 오류] 상품 목록 조회 실패: {}", e.getMessage());
            allProducts = Collections.emptyList();
        }

        List<ProductResponse> filteredProducts = allProducts.stream()

                .filter(p -> {
                    if (condition.getSweetenerExclude() == null) return true;
                    String exclude = condition.getSweetenerExclude();
                    return p.getSweeteners() == null || !p.getSweeteners().contains(exclude);
                })

                .filter(p -> {
                    if (condition.getAllergenExclude() == null) return true;
                    String allergen = condition.getAllergenExclude();
                    return p.getAllergens() == null || !p.getAllergens().contains(allergen);
                })

                .filter(p -> {
                    if (condition.getKcalMax() == null) return true;
                    return p.getKcal() != null && p.getKcal().compareTo(condition.getKcalMax()) <= 0;
                })

                .filter(p -> {
                    if (condition.getMaxPrice() == null) return true;
                    return p.getPrice() != null && p.getPrice() <= condition.getMaxPrice();
                })

                .filter(p -> {
                    if (condition.getSugarMax() == null) return true;
                    return p.getSugarG() != null && p.getSugarG().compareTo(condition.getSugarMax()) <= 0;
                })
                .toList();

        Map<Long, Double> scoreMap = calculateBehaviorScores(memberId);

        List<ProductResponse> rankedProducts = filteredProducts.stream()
                .sorted((p1, p2) -> {
                    double score1 = scoreMap.getOrDefault(p1.getId(), 0.0);
                    double score2 = scoreMap.getOrDefault(p2.getId(), 0.0);
                    return Double.compare(score2, score1);
                })
                .limit(3)
                .toList();

        List<ChatResponse.ChatProductItem> recommendedItems = rankedProducts.stream()
                .map(p -> ChatResponse.ChatProductItem.builder()
                        .productId(p.getId())
                        .name(p.getName())
                        .brand(p.getBrand())
                        .category(p.getCategory())
                        .price(p.getPrice())
                        .imageUrl(p.getImageUrl())
                        .sugarG(p.getSugarG())
                        .sweeteners(p.getSweeteners())
                        .reason(generateProductReason(p, condition, scoreMap.get(p.getId())))
                        .build())
                .collect(Collectors.toList());

        String reply = generateBotReply(condition, recommendedItems, usedFallback);

        long duration = System.currentTimeMillis() - startTime;
        log.info("[챗봇 응답 완료] memberId={}, 추천 건수={}, usedFallback={}, 소요시간={}ms",
                memberId, recommendedItems.size(), usedFallback, duration);

        return ChatResponse.builder()
                .reply(reply)
                .extractedCondition(condition)
                .usedFallback(usedFallback)
                .products(recommendedItems)
                .build();
    }

    private Map<Long, Double> calculateBehaviorScores(Long memberId) {
        if (memberId == null) {
            return Collections.emptyMap();
        }
        List<BehaviorLog> logs = behaviorLogRepository.findByMemberIdOrderByOccurredAtDesc(memberId);
        Map<Long, Double> scores = new HashMap<>();
        for (BehaviorLog logItem : logs) {
            Long pid = logItem.getProductId();
            if (pid == null) continue;

            double weight = switch (logItem.getEventType()) {
                case "ORDER_COMPLETED" -> 50.0;
                case "PRODUCT_VIEWED" -> 1.0;
                case "CART_ADDED" -> 0.0;
                default -> 0.0;
            };
            scores.put(pid, scores.getOrDefault(pid, 0.0) + weight);
        }
        return scores;
    }

    private String generateProductReason(ProductResponse p, SearchCondition condition, Double score) {
        StringBuilder sb = new StringBuilder();
        if (condition.getSweetenerExclude() != null) {
            sb.append(condition.getSweetenerExclude()).append(" 무첨가, ");
        }
        if (p.getSugarG() != null) {
            sb.append("당류 ").append(p.getSugarG()).append("g, ");
        }
        if (score != null && score >= 50.0) {
            sb.append("최근 주문 이력 반영 맞춤 추천");
        } else if (score != null && score >= 1.0) {
            sb.append("최근 관심 상품 기반 추천");
        } else {
            sb.append(p.getCategory() != null ? p.getCategory() : "맞춤").append(" 추천 상품");
        }
        return sb.toString();
    }

    private String generateBotReply(SearchCondition condition, List<ChatResponse.ChatProductItem> items, boolean usedFallback) {
        if (items.isEmpty()) {
            return "요청하신 조건에 부합하는 상품을 찾지 못했습니다. 다른 조건으로 다시 질문해 주세요.";
        }

        StringBuilder sb = new StringBuilder();
        if (condition.getSweetenerExclude() != null) {
            sb.append("[").append(condition.getSweetenerExclude()).append(" 제외] ");
        }
        if (condition.getCategory() != null) {
            sb.append(condition.getCategory()).append(" 카테고리에서 ");
        }
        sb.append("조건에 딱 맞는 상품 ").append(items.size()).append("개를 추천해 드립니다!");
        if (usedFallback) {
            sb.append(" (규칙 기반 추천 결과)");
        }

        return sb.toString();
    }
}
