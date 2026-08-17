package com.zeropick.recommendationservice.controller;

import com.zeropick.recommendationservice.dto.PreferenceRequest;
import com.zeropick.recommendationservice.dto.PreferenceResponse;
import com.zeropick.recommendationservice.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/recommendation-service/preferences")
@RequiredArgsConstructor
public class PreferenceController {

    private final PreferenceService preferenceService;

    @PostMapping
    public ResponseEntity<PreferenceResponse> savePreference(@RequestBody PreferenceRequest request) {
        PreferenceResponse response = preferenceService.saveOrUpdatePreference(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{memberId}")
    public ResponseEntity<PreferenceResponse> getPreference(@PathVariable("memberId") Long memberId) {
        PreferenceResponse response = preferenceService.getPreference(memberId);
        return ResponseEntity.ok(response);
    }
}
