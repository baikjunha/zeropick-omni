package com.zeropick.recommendationservice.repository;

import com.zeropick.recommendationservice.domain.BehaviorLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BehaviorLogRepository extends JpaRepository<BehaviorLog, Long> {
    List<BehaviorLog> findByMemberIdOrderByOccurredAtDesc(Long memberId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT b.productId AS productId, SUM(COALESCE(b.qty, 1)) AS totalQty, MIN(b.occurredAt) AS firstAt "
            + "FROM BehaviorLog b WHERE b.eventType = 'ORDER_COMPLETED' AND b.productId IS NOT NULL "
            + "GROUP BY b.productId")
    List<Object[]> aggregateOrderQty();
}
