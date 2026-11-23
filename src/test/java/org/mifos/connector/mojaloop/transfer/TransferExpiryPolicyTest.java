package org.mifos.connector.mojaloop.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mifos.connector.mojaloop.config.ExpiryProperties;

class TransferExpiryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-21T07:40:00Z");

    private static TransferExpiryPolicy policy(Duration transfer, Clock clock) {
        return new TransferExpiryPolicy(new ExpiryProperties(transfer, Duration.ofSeconds(30), Duration.ofHours(1)), clock);
    }

    private static TransferExpiryPolicy policy(Duration transfer) {
        return policy(transfer, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void configuredTransferExpiryCountsFromNow() {
        assertThat(policy(Duration.ofSeconds(60)).transferExpiration("2026-11-21T08:40:00.000Z"))
                .isEqualTo(LocalDateTime.parse("2026-11-21T07:41:00"));
    }

    @Test
    void unsetTransferExpiryKeepsTheQuoteExpiryPlusOneHour() {
        assertThat(policy(null).transferExpiration("2026-11-21T08:40:00.000Z")).isEqualTo(LocalDateTime.parse("2026-11-21T09:40:00"));
        assertThat(policy(null).transferExpiration(null)).isEqualTo(LocalDateTime.parse("2026-11-21T08:40:00"));
    }

    @Test
    void quoteExpiryAndFinalQuery() {
        TransferExpiryPolicy policy = policy(Duration.ofSeconds(60));
        assertThat(policy.quoteExpiration()).isEqualTo(LocalDateTime.parse("2026-11-21T08:40:00"));
        assertThat(policy.finalQueryAt(LocalDateTime.parse("2026-11-21T07:41:00"))).isEqualTo(LocalDateTime.parse("2026-11-21T07:41:30"));
    }

    @Test
    void bpmnTimesAreReadableByFeel() {
        assertThat(TransferExpiryPolicy.toBpmn(LocalDateTime.parse("2026-11-21T07:41:00"))).isEqualTo("2026-11-21T07:41:00.000+00:00");
    }
}
