package com.zeropick.recommendationservice.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeropick.recommendationservice.llm.dto.LlmParseResult;
import com.zeropick.recommendationservice.parser.RuleBasedQueryParser;
import com.zeropick.recommendationservice.parser.dto.SearchCondition;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmQueryService {

    private final RuleBasedQueryParser ruleBasedQueryParser;
    private final ObjectMapper objectMapper;

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.model:gpt-4o-mini}")
    private String model;

    @Value("${llm.api-url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @CircuitBreaker(name = "llmService", fallbackMethod = "fallbackParseQuery")
    public LlmParseResult extractCondition(String query) {
        log.info("[LLM 질의 파싱 요청] query='{}'", query);

        if ("dummy-key".equals(apiKey) || "dummy-key-for-test".equals(apiKey) || apiKey.isBlank()) {
            throw new IllegalStateException("LLM API Key가 설정되지 않았습니다. Fallback 파서로 전환합니다.");
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        RestClient restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        String systemPrompt = """
            당신은 저당/제로 식품 쇼핑몰의 질의 파서입니다. 사용자의 질문에서 검색 조건을 JSON 형식으로만 추출하세요.
            반드시 아래 JSON 구조로만 응답하세요:
            {
              "category": "음료" | "간식/디저트" | "조미료/소스" | "유제품" | "육가공품" | "주식/면류" | "즉석식품" | "건강기능식품" | "기타" | null,
              "sweetenerExclude": "제외할 감미료명" | null,
              "allergenExclude": "우유" | "대두" | "밀" | "땅콩" | null,
              "sugarMax": 최대 당류(숫자) | null,
              "kcalMin": 최소 칼로리(숫자) | null,
              "kcalMax": 최대 칼로리(숫자) | null,
              "maxPrice": 최대 가격(정수 원단위) | null,
              "query": "핵심 상품 키워드 한 단어" | null
            }
            query 규칙: 상품명 부분일치 검색에 쓰이므로 반드시 한 단어만 넣으세요.
            '제로/저당/무설탕' 같은 수식어는 sugarMax 로 표현되므로 query 에 포함하지 마세요.
            (예: "아스파탐 없는 제로 콜라" → query: "콜라")
            """;

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", query)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.0
        );

        String responseBody = restClient.post()
                .body(requestBody)
                .retrieve()
                .body(String.class);

        SearchCondition condition = parseJsonToCondition(responseBody);
        return LlmParseResult.of(condition, false);
    }

    public LlmParseResult fallbackParseQuery(String query, Throwable throwable) {
        log.warn("[LLM Fallback 발동] 원인: {}. 규칙 기반 파서(RuleBasedQueryParser)로 전환합니다.", throwable.getMessage());
        SearchCondition fallbackCondition = ruleBasedQueryParser.parse(query);
        return LlmParseResult.of(fallbackCondition, true);
    }

    private SearchCondition parseJsonToCondition(String rawJson) {
        try {
            JsonNode rootNode = objectMapper.readTree(rawJson);
            String content = rootNode.path("choices").get(0).path("message").path("content").asText();
            JsonNode conditionJson = objectMapper.readTree(content);

            String category = textOrNull(conditionJson, "category");
            String sweetenerExclude = textOrNull(conditionJson, "sweetenerExclude");
            String allergenExclude = textOrNull(conditionJson, "allergenExclude");
            BigDecimal sugarMax = decimalOrNull(conditionJson, "sugarMax");
            BigDecimal kcalMin = decimalOrNull(conditionJson, "kcalMin");
            BigDecimal kcalMax = decimalOrNull(conditionJson, "kcalMax");
            JsonNode priceNode = conditionJson.get("maxPrice");
            Integer maxPrice = (priceNode == null || priceNode.isNull()) ? null : priceNode.asInt();
            String q = textOrNull(conditionJson, "query");

            return SearchCondition.builder()
                    .category(category)
                    .sweetenerExclude(sweetenerExclude)
                    .allergenExclude(allergenExclude)
                    .sugarMax(sugarMax)
                    .kcalMin(kcalMin)
                    .kcalMax(kcalMax)
                    .maxPrice(maxPrice)
                    .query(q)
                    .build();
        } catch (Exception e) {
            log.error("[LLM 응답 JSON 파싱 실패] rawJson={}", rawJson, e);
            throw new RuntimeException("LLM JSON 응답 변환 실패", e);
        }
    }

    private static String textOrNull(JsonNode parent, String field) {
        JsonNode n = parent.get(field);
        return (n == null || n.isNull()) ? null : n.asText(null);
    }

    private static BigDecimal decimalOrNull(JsonNode parent, String field) {
        JsonNode n = parent.get(field);
        return (n == null || n.isNull() || !n.isNumber()) ? null : BigDecimal.valueOf(n.asDouble());
    }
}
