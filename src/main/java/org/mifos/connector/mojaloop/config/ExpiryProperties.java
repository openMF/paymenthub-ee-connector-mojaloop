package org.mifos.connector.mojaloop.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Expiry times this connector puts on FSPIOP requests: {@code mojaloop.expiry.*}. FSPIOP leaves them to the scheme
 * rules, so set them per deployment.
 *
 * @param transfer
 *            the expiry of a transfer this connector prepares as payer, from the moment it is sent. If the payee
 *            hasn't fulfilled by then, the switch rolls the transfer back. Unset: the quote's expiry plus one hour
 *            (the behaviour before PHEE-476). A short expiry lets the payer learn a stuck transfer's outcome soon:
 *            at expiry the switch rolls back a transfer that wasn't committed and tells the payer (error 3303).
 * @param finalQueryGrace
 *            how long after the transfer's expiry the payer keeps waiting for the switch's answer, for clock skew
 *            and callbacks in flight
 * @param quote
 *            the expiry this connector puts on its quote responses as payee
 */
@Validated
@ConfigurationProperties(prefix = "mojaloop.expiry")
public record ExpiryProperties(Duration transfer, @NotNull Duration finalQueryGrace, @NotNull Duration quote) {
}
