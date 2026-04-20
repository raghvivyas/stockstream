package com.stockstream.repository;

import com.stockstream.entity.SentimentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SentimentRepository extends JpaRepository<SentimentEntity, Long> {

    Optional<SentimentEntity> findTopBySymbolOrderByAnalyzedAtDesc(String symbol);

    List<SentimentEntity> findBySymbolOrderByAnalyzedAtDesc(String symbol, Pageable pageable);

    List<SentimentEntity> findAllByOrderByAnalyzedAtDesc(Pageable pageable);
}
