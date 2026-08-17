package com.zeropick.recommendationservice.controller;

import com.zeropick.recommendationservice.dto.RecoResponse;
import com.zeropick.recommendationservice.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/recommendation-service/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/{memberId}")
    public ResponseEntity<List<RecoResponse>> getRecommendations(@PathVariable("memberId") Long memberId) {
        List<RecoResponse> response = recommendationService.calculateAndGetRecommendations(memberId);
        return ResponseEntity.ok(response);
    }
}
