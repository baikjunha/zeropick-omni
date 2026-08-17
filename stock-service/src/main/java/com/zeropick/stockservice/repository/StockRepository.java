package com.zeropick.stockservice.repository;

import com.zeropick.stockservice.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockRepository extends JpaRepository<Stock, Long> {

    List<Stock> findByProductIdIn(List<Long> productIds);

    /**
     * 조건부 UPDATE 차감 — 잔여 재고가 충분할 때만 갱신된다(반환 0 = 재고 부족).
     * 단일 문장이 원자적으로 실행되어 동시 주문 초과 차감을 막는다.
     */
    @Modifying
    @Query("UPDATE Stock s SET s.onlineStock = s.onlineStock - :qty, s.version = s.version + 1 "
            + "WHERE s.productId = :productId AND s.onlineStock >= :qty")
    int deductIfEnough(@Param("productId") Long productId, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE Stock s SET s.onlineStock = s.onlineStock + :qty, s.version = s.version + 1 "
            + "WHERE s.productId = :productId")
    int restore(@Param("productId") Long productId, @Param("qty") int qty);
}
