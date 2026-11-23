package org.mifos.connector.mojaloop.transfer;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.mifos.connector.common.util.ContextUtil;
import org.mifos.connector.mojaloop.config.ExpiryProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Works out the expiry times this connector puts on quotes and transfers (see {@link ExpiryProperties}), and until when
 * the payer waits for the switch's answer to a prepare. Times are UTC, as everywhere in this connector.
 */
@Component
public class TransferExpiryPolicy {

    /** Format of the times handed to the BPMN: readable by FEEL's {@code date and time()}. */
    static final DateTimeFormatter BPMN_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSxxx");

    /** The behaviour before PHEE-476, kept when no transfer expiry is configured. */
    static final Duration AFTER_QUOTE_EXPIRY = Duration.ofHours(1);

    private final ExpiryProperties properties;

    private final Clock clock;

    @Autowired
    public TransferExpiryPolicy(ExpiryProperties properties) {
        this(properties, Clock.systemUTC());
    }

    TransferExpiryPolicy(ExpiryProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }

    /** Expiry for a quote response this connector sends as payee. */
    public LocalDateTime quoteExpiration() {
        return now().plus(properties.quote());
    }

    /**
     * Expiry for a transfer this connector prepares as payer.
     *
     * @param quoteExpiration
     *            the payee's quote expiry as FSPIOP sends it, or null
     */
    public LocalDateTime transferExpiration(String quoteExpiration) {
        if (properties.transfer() != null) {
            return now().plus(properties.transfer());
        }
        LocalDateTime base = quoteExpiration == null ? now() : ContextUtil.parseDate(quoteExpiration);
        return base.plus(AFTER_QUOTE_EXPIRY);
    }

    /**
     * Until when the payer waits for the switch's answer: the transfer's expiry plus the grace period. By then a switch
     * that keeps the FSPIOP rules has either committed the transfer or rolled it back and said so.
     */
    public LocalDateTime finalQueryAt(LocalDateTime transferExpiration) {
        return transferExpiration.plus(properties.finalQueryGrace());
    }

    public static String toBpmn(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).format(BPMN_TIME);
    }
}
