package org.mifos.connector.mojaloop.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Switches that change how this connector behaves: {@code mojaloop.*}.
 *
 * @param enabled
 *            when false the connector answers from canned data instead of calling the switch
 * @param perfMode
 *            performance mode, which short-circuits parts of the flow
 * @param perfRespDelay
 *            artificial delay in milliseconds used by performance mode
 */

@Validated
@ConfigurationProperties(prefix = "mojaloop")
public record MojaloopProperties(@NotNull Boolean enabled, @NotNull Boolean perfMode, @NotNull Integer perfRespDelay) {
}
