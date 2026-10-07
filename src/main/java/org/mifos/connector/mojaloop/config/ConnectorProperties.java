package org.mifos.connector.mojaloop.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * This connector's own settings: {@code connector.*}.
 *
 * @param ilpSecret
 *            the secret used to build and verify ILP packets; without it every transfer condition is wrong
 */

@Validated
@ConfigurationProperties(prefix = "connector")
public record ConnectorProperties(@NotNull String ilpSecret) {
}
