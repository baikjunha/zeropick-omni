package com.zeropick.stockservice.repository;

import com.zeropick.stockservice.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockRepository extends JpaRepository<Stock, Long> {

    List<Stock> findByProductIdIn(List<Long> productIds);

    @Modifying
    @Query("UPDATE Stock s SET s.onlineStock = s.onlineStock - :qty, s.version = s.version + 1 "
            + "WHERE s.productId = :productId AND s.onlineStock >= :qty")
    int deductIfEnough(@Param("productId") Long productId, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE Stock s SET s.onlineStock = s.onlineStock + :qty, s.version = s.version + 1 "
            + "WHERE s.productId = :productId")
    int restore(@Param("productId") Long productId, @Param("qty") int qty);
}
