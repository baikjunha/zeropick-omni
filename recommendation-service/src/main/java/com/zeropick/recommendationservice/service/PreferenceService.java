package com.zeropick.recommendationservice.service;

import com.zeropick.recommendationservice.domain.*;
import com.zeropick.recommendationservice.dto.PreferenceRequest;
import com.zeropick.recommendationservice.dto.PreferenceResponse;
import com.zeropick.recommendationservice.repository.PreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreferenceService {

    private final PreferenceRepository preferenceRepository;

    @Transactional
    public PreferenceResponse saveOrUpdatePreference(PreferenceRequest request) {

        Preference preference = preferenceRepository.findById(request.getMemberId())
                .orElseGet(() -> Preference.builder()
                        .memberId(request.getMemberId())
                        .categories(new ArrayList<>())
                        .excludedSweeteners(new ArrayList<>())
                        .allergens(new ArrayList<>())
                        .build());

        int priceMin = request.getPriceMin() != null ? request.getPriceMin() : 0;
        int priceMax = request.getPriceMax() != null ? request.getPriceMax() : 100000;

        preference.getCategories().clear();
        if (request.getCategories() != null) {
            for (String category : request.getCategories()) {
                preference.getCategories().add(
                        PrefCategory.builder()
                                .id(new PrefCategoryId(preference.getMemberId(), category))
                                .preference(preference)
                                .build()
                );
            }
        }

        preference.getExcludedSweeteners().clear();

        if (request.getExcludedSweeteners() != null) {
            for (String sweetener : request.getExcludedSweeteners()) {
                preference.getExcludedSweeteners().add(
                        PrefExcludedSweetener.builder()
                                .id(new PrefExcludedSweetenerId(preference.getMemberId(), sweetener))
                                .preference(preference)
                                .build()
                );
            }
        }

        preference.getAllergens().clear();

        if (request.getAllergens() != null) {
            for (String allergen : request.getAllergens()) {
                preference.getAllergens().add(
                        PrefAllergen.builder()
                                .id(new PrefAllergenId(preference.getMemberId(), allergen))
                                .preference(preference)
                                .build()
                );
            }
        }

        Preference toSave = Preference.builder()
                .memberId(preference.getMemberId())
                .priceMin(priceMin)
                .priceMax(priceMax)
                .updatedAt(LocalDateTime.now())
                .categories(preference.getCategories())
                .excludedSweeteners(preference.getExcludedSweeteners())
                .allergens(preference.getAllergens())
                .build();

        Preference saved = preferenceRepository.saveAndFlush(toSave);
        return PreferenceResponse.from(saved);
    }

    public PreferenceResponse getPreference(Long memberId) {
        Preference preference = preferenceRepository.findById(memberId)
                .orElseGet(() -> Preference.builder()
                        .memberId(memberId)
                        .priceMin(0)
                        .priceMax(100000)
                        .updatedAt(LocalDateTime.now())
                        .categories(new ArrayList<>())
                        .excludedSweeteners(new ArrayList<>())
                        .allergens(new ArrayList<>())
                        .build());

        return PreferenceResponse.from(preference);
    }
}
