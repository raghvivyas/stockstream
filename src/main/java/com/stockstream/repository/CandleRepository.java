package com.stockstream.repository;

import com.stockstream.entity.CandleEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CandleRepository extends JpaRepository<CandleEntity, Long> {

    List<CandleEntity> findBySymbolOrderByOpenTimeDesc(String symbol, Pageable pageable);

    @Query("SELECT c FROM CandleEntity c WHERE c.symbol = :symbol " +
           "AND c.openTime >= :from AND c.openTime <= :to ORDER BY c.openTime ASC")
    List<CandleEntity> findBySymbolAndTimeRange(
            @Param("symbol") String symbol,
            @Param("from") Instant from,
            @Param("to") Instant to);

    Optional<CandleEntity> findTopBySymbolOrderByOpenTimeDesc(String symbol);

    /**
     * Returns the latest candle for each distinct symbol — used for top-movers endpoint.
     */
    @Query("SELECT c FROM CandleEntity c WHERE c.openTime = " +
           "(SELECT MAX(c2.openTime) FROM CandleEntity c2 WHERE c2.symbol = c.symbol)")
    List<CandleEntity> findLatestCandlePerSymbol();

    @Query("SELECT c FROM CandleEntity c WHERE c.symbol = :symbol " +
           "ORDER BY c.openTime DESC")
    List<CandleEntity> findTopNBySymbol(@Param("symbol") String symbol, Pageable pageable);
}
