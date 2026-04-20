package com.stockstream.websocket;

import com.stockstream.model.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Broadcasts real-time ticks to WebSocket subscribers via STOMP.
 *
 * Clients subscribe to:
 *   /topic/ticks           — all symbols
 *   /topic/ticks/{SYMBOL}  — a single symbol
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastTick(Tick tick) {
        // Broadcast to all-symbols channel
        messagingTemplate.convertAndSend("/topic/ticks", tick);

        // Broadcast to per-symbol channel
        messagingTemplate.convertAndSend("/topic/ticks/" + tick.getSymbol(), tick);

        log.trace("Broadcast tick: symbol={} price={}", tick.getSymbol(), tick.getPrice());
    }
}
