package org.mifos.connector.mojaloop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How this connector reaches the Zeebe broker: {@code zeebe.broker.*} and {@code zeebe.client.*}.
 *
 * @param broker
 *            the broker to connect to
 * @param client
 *            client settings
 */

@ConfigurationProperties(prefix = "zeebe")
public record ZeebeProperties(@DefaultValue Broker broker, @DefaultValue Client client) {

    /**
     * The broker to connect to: {@code zeebe.broker.*}.
     *
     * @param contactpoint
     *            gateway address, as host:port
     */
    public record Broker(String contactpoint) {
    }

    /**
     * Client settings: {@code zeebe.client.*}.
     *
     * @param maxExecutionThreads
     *            size of the job worker execution thread pool
     * @param evenlyAllocatedMaxJobs
     *            how many jobs a single worker keeps active
     * @param pollInterval
     *            job poll interval in milliseconds
     */
    public record Client(int maxExecutionThreads,
            int evenlyAllocatedMaxJobs,
            int pollInterval) {
    }
}
