package org.mifos.connector.mojaloop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * This connector's own settings: {@code connector.*}.
 *
 * @param ilpSecret
 *            the secret used to build and verify ILP packets; without it every transfer condition is wrong
 */

@ConfigurationProperties(prefix = "connector")
public record ConnectorProperties(String ilpSecret) {
}
