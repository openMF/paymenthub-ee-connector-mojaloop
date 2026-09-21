package org.mifos.connector.mojaloop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Switches that change how this connector behaves: {@code mojaloop.*}.
 *
 * @param enabled
 *            when false the connector answers from canned data instead of calling the switch; defaults to the
 *            value application.yml ships, true
 * @param perfMode
 *            performance mode, which short-circuits parts of the flow
 * @param perfRespDelay
 *            artificial delay in milliseconds used by performance mode
 */

@ConfigurationProperties(prefix = "mojaloop")
public record MojaloopProperties(@DefaultValue("true") boolean enabled, @DefaultValue("false") boolean perfMode,
        @DefaultValue("0") int perfRespDelay) {
}
