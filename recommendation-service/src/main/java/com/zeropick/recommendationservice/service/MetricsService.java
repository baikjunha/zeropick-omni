package com.zeropick.recommendationservice.service;

import com.zeropick.recommendationservice.domain.RecoClick;
import com.zeropick.recommendationservice.dto.ClickRequest;
import com.zeropick.recommendationservice.dto.MetricsResponse;
import com.zeropick.recommendationservice.repository.RecoClickRepository;
import com.zeropick.recommendationservice.repository.RecoResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final RecoClickRepository recoClickRepository;
    private final RecoResultRepository recoResultRepository;

    private final AtomicLong totalChatRequests = new AtomicLong(0);
    private final AtomicLong fallbackRequests = new AtomicLong(0);

    @Transactional
    public void recordClick(ClickRequest request) {
        if (request == null || request.getMemberId() == null || request.getProductId() == null) {
            return;
        }
        RecoClick click = RecoClick.builder()
                .memberId(request.getMemberId())
                .productId(request.getProductId())
                .build();
        recoClickRepository.save(click);
        log.info("[추천 클릭 수집] memberId={}, productId={}", request.getMemberId(), request.getProductId());
    }

    public void incrementChatRequest(boolean usedFallback) {
        totalChatRequests.incrementAndGet();
        if (usedFallback) {
            fallbackRequests.incrementAndGet();
        }
    }

    @Transactional(readOnly = true)
    public MetricsResponse getMetrics() {
        long impressions = recoResultRepository.count();
        long clicks = recoClickRepository.count();

        double ctr = impressions > 0 ? ((double) clicks / impressions) * 100.0 : 0.0;

        long totalReq = totalChatRequests.get();
        long fallbacks = fallbackRequests.get();
        double fallbackRate = totalReq > 0 ? ((double) fallbacks / totalReq) * 100.0 : 0.0;

        return MetricsResponse.builder()
                .totalImpressions(impressions)
                .totalClicks(clicks)
                .clickThroughRate(Math.round(ctr * 100.0) / 100.0)
                .totalChatRequests(totalReq)
                .fallbackCount(fallbacks)
                .fallbackRate(Math.round(fallbackRate * 100.0) / 100.0)
                .build();
    }
}
