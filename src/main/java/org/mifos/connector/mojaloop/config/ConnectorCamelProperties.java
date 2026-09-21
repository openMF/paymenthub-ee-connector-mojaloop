package org.mifos.connector.mojaloop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code camel.*} setting this connector owns.
 *
 * <p>
 * Only {@code camel.server-port} is bound. Everything else under {@code camel.} belongs to camel-spring-boot and is left alone.
 * </p>
 *
 * @param serverPort
 *            port the Camel REST configuration is given
 */

@ConfigurationProperties(prefix = "camel")
public record ConnectorCamelProperties(int serverPort) {
}
