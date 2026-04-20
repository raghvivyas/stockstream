package com.stockstream.controller;

import com.stockstream.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final AppProperties props;

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("app",       "StockStream");
        info.put("version",   "1.0.0");
        info.put("symbols",   props.getSymbols());
        info.put("aiEnabled", props.getOpenaiApiKey() != null && !props.getOpenaiApiKey().trim().isEmpty());
        info.put("timestamp", Instant.now());
        return ResponseEntity.ok(info);
    }
}
