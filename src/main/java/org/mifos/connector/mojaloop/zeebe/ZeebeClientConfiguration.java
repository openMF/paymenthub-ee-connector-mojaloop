package org.mifos.connector.mojaloop.zeebe;

import io.camunda.zeebe.client.ZeebeClient;
import org.mifos.connector.mojaloop.config.ZeebeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConditionalOnExpression("!${mojaloop.perf-mode:false}")
public class ZeebeClientConfiguration {

    private final String zeebeBrokerContactpoint;

    private final int zeebeClientMaxThreads;

    private final int zeebeClientPollInterval;

    public ZeebeClientConfiguration(ZeebeProperties zeebeProperties) {
        this.zeebeBrokerContactpoint = zeebeProperties.broker().contactpoint();
        this.zeebeClientMaxThreads = zeebeProperties.client().maxExecutionThreads();
        this.zeebeClientPollInterval = zeebeProperties.client().pollInterval();
    }

    @Bean
    public ZeebeClient setup() {
        return ZeebeClient.newClientBuilder()
                .gatewayAddress(zeebeBrokerContactpoint)
                .usePlaintext()
                .defaultJobPollInterval(Duration.ofMillis(zeebeClientPollInterval))
                .defaultJobWorkerMaxJobsActive(2000)
                .numJobWorkerExecutionThreads(zeebeClientMaxThreads)
                .build();
    }
}
