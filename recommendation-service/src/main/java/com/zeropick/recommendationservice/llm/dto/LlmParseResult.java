package com.zeropick.recommendationservice.llm.dto;

import com.zeropick.recommendationservice.parser.dto.SearchCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmParseResult {
    private SearchCondition condition;
    private boolean usedFallback;

    public static LlmParseResult of(SearchCondition condition, boolean usedFallback) {
        return LlmParseResult.builder()
                .condition(condition)
                .usedFallback(usedFallback)
                .build();
    }
}
