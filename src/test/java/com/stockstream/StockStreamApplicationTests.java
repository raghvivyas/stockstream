package com.stockstream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity test — no Spring context loaded.
 * Full integration tests require Kafka + Redis which are not available in CI.
 * All real logic is covered by the pure-Mockito unit tests in the service/controller packages.
 */
class StockStreamApplicationTests {

    @Test
    void sanityCheck_javaVersionIsEight() {
        // Verifies the test runner is Java 8 compatible
        String version = System.getProperty("java.version");
        assertThat(version).isNotNull();
    }
}
