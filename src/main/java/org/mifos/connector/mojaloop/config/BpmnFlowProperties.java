package org.mifos.connector.mojaloop.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The BPMN process ids this connector starts: {@code bpmn.flows.*}.
 *
 * @param partyLookup
 *            the payee party lookup flow
 * @param quote
 *            the payee quote transfer flow
 * @param transactionRequest
 *            the payer transaction request flow
 */

@Validated
@ConfigurationProperties(prefix = "bpmn.flows")
public record BpmnFlowProperties(@NotNull String partyLookup,
        @NotNull String quote,
        @NotNull String transactionRequest) {
}
