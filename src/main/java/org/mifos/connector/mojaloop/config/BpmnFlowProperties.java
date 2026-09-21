package org.mifos.connector.mojaloop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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

@ConfigurationProperties(prefix = "bpmn.flows")
public record BpmnFlowProperties(String partyLookup,
        String quote,
        String transactionRequest) {
}
