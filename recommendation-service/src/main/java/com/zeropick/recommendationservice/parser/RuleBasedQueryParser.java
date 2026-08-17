package com.zeropick.recommendationservice.parser;

import com.zeropick.recommendationservice.parser.dto.SearchCondition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleBasedQueryParser {

    private static final List<String> SWEETENERS = List.of(
            "효소처리스테비아", "스테비올 배당체", "나한과추출분말", "아세설팜칼륨",
            "D-말티톨", "D-소비톨액", "아라비아검", "수크랄로스", "에리스리톨",
            "아스파탐", "알룰로스", "자일리톨", "이소말트", "스테비아",
            "말티톨", "나한과", "사카린", "소르비톨"
    );

    private static final List<String> ALLERGENS = List.of("우유", "대두", "밀", "땅콩");

    private static final Map<String, String> CATEGORY_MAP = new LinkedHashMap<>();
    static {

        CATEGORY_MAP.put("프로틴바", "간식/디저트");
        CATEGORY_MAP.put("프로틴 드링크", "음료");
        CATEGORY_MAP.put("프로틴", "건강기능식품");
        CATEGORY_MAP.put("단백질", "건강기능식품");
        CATEGORY_MAP.put("유산균", "건강기능식품");
        CATEGORY_MAP.put("영양제", "건강기능식품");
        CATEGORY_MAP.put("건강기능식품", "건강기능식품");

        CATEGORY_MAP.put("아이스크림", "간식/디저트");
        CATEGORY_MAP.put("초콜릿", "간식/디저트");
        CATEGORY_MAP.put("초코", "간식/디저트");
        CATEGORY_MAP.put("팝콘", "간식/디저트");
        CATEGORY_MAP.put("웨하스", "간식/디저트");
        CATEGORY_MAP.put("캔디", "간식/디저트");
        CATEGORY_MAP.put("사탕", "간식/디저트");
        CATEGORY_MAP.put("쿠키", "간식/디저트");
        CATEGORY_MAP.put("과자", "간식/디저트");
        CATEGORY_MAP.put("젤리", "간식/디저트");
        CATEGORY_MAP.put("베이커리", "간식/디저트");
        CATEGORY_MAP.put("디저트", "간식/디저트");

        CATEGORY_MAP.put("스파클링", "음료");
        CATEGORY_MAP.put("탄산수", "음료");
        CATEGORY_MAP.put("에이드", "음료");
        CATEGORY_MAP.put("콜라", "음료");
        CATEGORY_MAP.put("사이다", "음료");
        CATEGORY_MAP.put("탄산", "음료");

        CATEGORY_MAP.put("커피", "음료");
        CATEGORY_MAP.put("라떼", "음료");
        CATEGORY_MAP.put("주스", "음료");
        CATEGORY_MAP.put("차", "음료");
        CATEGORY_MAP.put("음료", "음료");

        CATEGORY_MAP.put("시럽", "조미료/소스");
        CATEGORY_MAP.put("소스", "조미료/소스");
        CATEGORY_MAP.put("조미료", "조미료/소스");
    }

    private static final Set<String> GENERIC_CATEGORY_WORDS =
            Set.of("음료", "디저트", "베이커리", "조미료", "건강기능식품", "영양제");

    private static final Pattern SUGAR_ZERO_PATTERN = Pattern.compile("(무당|무당류|제로|(?<!\\d)0g|당류\\s*0(?![.\\d])|무가당|무설탕|슈가프리)");
    private static final Pattern SUGAR_LOW_PATTERN = Pattern.compile("(저당|저당류)");

    private static final Pattern KCAL_PATTERN = Pattern.compile("(\\d+)\\s*(kcal|칼로리)\\s*(이하|미만|이상|넘는)?", Pattern.CASE_INSENSITIVE);

    private static final Pattern PRICE_PATTERN = Pattern.compile("(\\d+)\\s*([천만])?\\s*원\\s*(이하|미만|까지)?");

    public SearchCondition parse(String text) {
        if (text == null || text.isBlank()) {
            return SearchCondition.builder().build();
        }

        String remainingText = text.trim();

        String sweetenerExclude = null;
        for (String sweetener : SWEETENERS) {
            Pattern excludePattern = Pattern.compile(Pattern.quote(sweetener) + "[이가은는]?\\s*(없는|제외|빼고|안들어간|안 들어간|무)");
            Matcher matcher = excludePattern.matcher(remainingText);

            if (matcher.find()) {
                sweetenerExclude = sweetener;
                remainingText = remainingText.replace(matcher.group(), " ");
                break;
            }
        }

        String allergenExclude = null;
        for (String allergen : ALLERGENS) {
            Pattern allergenPattern = Pattern.compile(Pattern.quote(allergen) + "[이가은는]?\\s*(없는|제외|빼고|안들어간|안 들어간|무)");
            Matcher matcher = allergenPattern.matcher(remainingText);
            if (matcher.find()) {
                allergenExclude = allergen;
                remainingText = remainingText.replace(matcher.group(), " ");
                break;
            }
        }

        BigDecimal kcalMax = null;
        BigDecimal kcalMin = null;
        Matcher kcalMatcher = KCAL_PATTERN.matcher(remainingText);
        if (kcalMatcher.find()) {
            try {
                BigDecimal value = new BigDecimal(kcalMatcher.group(1));
                String bound = kcalMatcher.group(3);
                if ("이상".equals(bound) || "넘는".equals(bound)) {
                    kcalMin = value;
                } else {
                    kcalMax = value;
                }
                remainingText = remainingText.replace(kcalMatcher.group(), " ");
            } catch (NumberFormatException ignored) {}
        }

        Integer maxPrice = extractPrice(remainingText);

        BigDecimal sugarMax = null;
        if (SUGAR_ZERO_PATTERN.matcher(remainingText).find()) {
            sugarMax = BigDecimal.ZERO;
        } else if (SUGAR_LOW_PATTERN.matcher(remainingText).find()) {
            sugarMax = BigDecimal.valueOf(5.0);
        }

        String category = null;
        String query = null;
        for (Map.Entry<String, String> entry : CATEGORY_MAP.entrySet()) {
            String keyword = entry.getKey();
            if (remainingText.contains(keyword)) {
                category = entry.getValue();
                if (!GENERIC_CATEGORY_WORDS.contains(keyword)) {
                    query = keyword;
                }
                break;
            }
        }

        return SearchCondition.builder()
                .category(category)
                .sweetenerExclude(sweetenerExclude)
                .allergenExclude(allergenExclude)
                .sugarMax(sugarMax)
                .kcalMin(kcalMin)
                .kcalMax(kcalMax)
                .maxPrice(maxPrice)
                .query(query)
                .build();
    }

    private Integer extractPrice(String text) {
        Matcher m = PRICE_PATTERN.matcher(text.replace(",", ""));

        while (m.find()) {
            String numStr = m.group(1);
            String unit = m.group(2);

            try {
                int price = Integer.parseInt(numStr);
                if ("천".equals(unit)) {
                    price *= 1000;
                } else if ("만".equals(unit)) {
                    price *= 10000;
                }
                if (price >= 100) {
                    return price;
                }
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
