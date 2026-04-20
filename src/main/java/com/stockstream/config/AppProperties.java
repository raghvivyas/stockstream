package com.stockstream.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "stockstream")
public class AppProperties {

    private String jwtSecret = "stockstream-default-secret-change-in-production-must-be-256-bits";
    private long jwtExpirationMs = 86400000L;
    private String openaiApiKey = "";
    private String openaiModel = "gpt-3.5-turbo";
    private String openaiBaseUrl = "https://api.openai.com/v1";
    private long tickIntervalMs = 500L;
    private int redisCacheSize = 50;
    private List<String> symbols = Arrays.asList(
            "RELIANCE", "TCS", "INFY", "HDFCBANK", "WIPRO",
            "ICICIBANK", "SBIN", "BAJFINANCE");

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String v) { this.jwtSecret = v; }
    public long getJwtExpirationMs() { return jwtExpirationMs; }
    public void setJwtExpirationMs(long v) { this.jwtExpirationMs = v; }
    public String getOpenaiApiKey() { return openaiApiKey; }
    public void setOpenaiApiKey(String v) { this.openaiApiKey = v; }
    public String getOpenaiModel() { return openaiModel; }
    public void setOpenaiModel(String v) { this.openaiModel = v; }
    public String getOpenaiBaseUrl() { return openaiBaseUrl; }
    public void setOpenaiBaseUrl(String v) { this.openaiBaseUrl = v; }
    public long getTickIntervalMs() { return tickIntervalMs; }
    public void setTickIntervalMs(long v) { this.tickIntervalMs = v; }
    public int getRedisCacheSize() { return redisCacheSize; }
    public void setRedisCacheSize(int v) { this.redisCacheSize = v; }
    public List<String> getSymbols() { return symbols; }
    public void setSymbols(List<String> v) { this.symbols = v; }
}
