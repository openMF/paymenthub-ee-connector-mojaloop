package org.mifos.connector.mojaloop.transfer;

import io.camunda.zeebe.client.ZeebeClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.mifos.connector.common.mojaloop.dto.TransferSwitchResponseDTO;
import org.mifos.connector.mojaloop.config.MojaloopProperties;
import org.mifos.connector.mojaloop.config.ZeebeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.mifos.connector.common.mojaloop.type.TransferState.COMMITTED;
import static org.mifos.connector.mojaloop.camel.config.CamelProperties.*;
import static org.mifos.connector.mojaloop.zeebe.ZeebeProcessStarter.zeebeVariablesToCamelHeaders;
import static org.mifos.connector.mojaloop.zeebe.ZeebeVariables.CHANNEL_REQUEST;
import static org.mifos.connector.mojaloop.zeebe.ZeebeVariables.ERROR_INFORMATION;
import static org.mifos.connector.mojaloop.zeebe.ZeebeVariables.ORIGIN_DATE;
import static org.mifos.connector.mojaloop.zeebe.ZeebeVariables.PAYEE_QUOTE_RESPONSE;
import static org.mifos.connector.mojaloop.zeebe.ZeebeVariables.SWITCH_TRANSFER_REQUEST;
import static org.mifos.connector.mojaloop.zeebe.ZeebeVariables.TIMEOUT_TRANSFER_RETRY_COUNT;
import static org.mifos.connector.mojaloop.zeebe.ZeebeVariables.TRANSACTION_ID;
import static org.mifos.connector.mojaloop.zeebe.ZeebeVariables.TRANSFER_EXPIRATION;
import static org.mifos.connector.mojaloop.zeebe.ZeebeVariables.TRANSFER_FINAL_QUERY_AT;
import static org.mifos.connector.mojaloop.zeebe.ZeebeWorkers.WORKER_PAYEE_TRANSFER_RESPONSE;
import static org.mifos.connector.mojaloop.zeebe.ZeebeWorkers.WORKER_SEND_TRANSFER_REQUEST;

@Component
@ConditionalOnExpression("!${mojaloop.perf-mode:false}")
public class TransferWorkers {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ZeebeClient zeebeClient;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private TransferExpiryPolicy expiryPolicy;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("#{'${dfspids}'.split(',')}")
    private List<String> dfspids;

    private final int workerMaxJobs;

    private final boolean isMojaloopEnabled;

    public TransferWorkers(ZeebeProperties zeebeProperties, MojaloopProperties mojaloopProperties) {
        this.workerMaxJobs = zeebeProperties.client().evenlyAllocatedMaxJobs();
        this.isMojaloopEnabled = mojaloopProperties.enabled();
    }

    @PostConstruct
    public void setupWorkers() {
        for (String dfspid : dfspids) {
            logger.info("## generating " + WORKER_PAYEE_TRANSFER_RESPONSE + "{} zeebe worker", dfspid);
            zeebeClient.newWorker()
                    .jobType(WORKER_PAYEE_TRANSFER_RESPONSE + dfspid)
                    .handler((client, job) -> {
                        logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
                        Map<String, Object> existingVariables = job.getVariablesAsMap();

                        Exchange exchange = new DefaultExchange(camelContext);
                        exchange.getIn().setBody(existingVariables.get(SWITCH_TRANSFER_REQUEST));
                        Object errorInformation = existingVariables.get(ERROR_INFORMATION);
                        if (errorInformation != null) {
                            zeebeVariablesToCamelHeaders(existingVariables, exchange,
                                    HEADER_DATE,
                                    HEADER_TRACEPARENT
                            );

                            exchange.setProperty(ERROR_INFORMATION, errorInformation);
                            exchange.setProperty(TRANSACTION_ID, existingVariables.get(TRANSACTION_ID));
                            producerTemplate.send("direct:send-transfer-error-to-switch", exchange);
                        } else {
                            zeebeVariablesToCamelHeaders(existingVariables, exchange,
                                    HEADER_DATE,
                                    HEADER_TRACEPARENT
                            );

                            exchange.setProperty(TRANSACTION_ID, existingVariables.get(TRANSACTION_ID));
                            exchange.setProperty(HOST, existingVariables.get("X-Transfer-Callback-Url"));
                            producerTemplate.send("direct:send-transfer-to-switch", exchange);
                        }
                        client.newCompleteCommand(job.getKey())
                                .send()
                                ;
                    })
                    .name(WORKER_PAYEE_TRANSFER_RESPONSE + dfspid)
                    .maxJobsActive(workerMaxJobs)
                    .open();

            logger.info("## generating " + WORKER_SEND_TRANSFER_REQUEST + "{} zeebe worker", dfspid);
            zeebeClient.newWorker()
                    .jobType(WORKER_SEND_TRANSFER_REQUEST + dfspid)
                    .handler((client, job) -> {
                        logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
                        Map<String, Object> variables = job.getVariablesAsMap();
                        // PHEE-476: the payer flow no longer resends the prepare, so nothing new reads this count. It is
                        // still kept up to date for instances started on the earlier BPMN, whose resend loop stops on it.
                        variables.put(TIMEOUT_TRANSFER_RETRY_COUNT, 1 + (Integer) variables.getOrDefault(TIMEOUT_TRANSFER_RETRY_COUNT, -1));

                        Exchange exchange = new DefaultExchange(camelContext);
                        Object transactionId = variables.get(TRANSACTION_ID);
                        // The prepare's expiry, and until when the BPMN waits for the switch's answer (PHEE-476)
                        LocalDateTime expiration = expiryPolicy.transferExpiration(quoteExpiration(variables.get(PAYEE_QUOTE_RESPONSE)));
                        exchange.setProperty(TransferRoutes.TRANSFER_EXPIRATION_UTC, expiration);
                        variables.put(TRANSFER_EXPIRATION, TransferExpiryPolicy.toBpmn(expiration));
                        variables.put(TRANSFER_FINAL_QUERY_AT, TransferExpiryPolicy.toBpmn(expiryPolicy.finalQueryAt(expiration)));
                        if(isMojaloopEnabled) {
                            exchange.setProperty(TRANSACTION_ID, transactionId);
                            exchange.setProperty(ORIGIN_DATE, variables.get(ORIGIN_DATE));
                            exchange.setProperty(CHANNEL_REQUEST, variables.get(CHANNEL_REQUEST));
                            exchange.getIn().setBody(variables.get(PAYEE_QUOTE_RESPONSE));
                            producerTemplate.send("direct:send-transfer", exchange);
                        } else {
                            TransferSwitchResponseDTO response = new TransferSwitchResponseDTO();
                            response.setTransferState(COMMITTED);
                            exchange.getIn().setBody(response);
                            exchange.getIn().setHeader(TRANSACTION_ID, transactionId);
                            exchange.setProperty(CACHED_TRANSACTION_ID, transactionId);
                            producerTemplate.send("direct:transfers-step4", exchange);
                        }

                        client.newCompleteCommand(job.getKey())
                                .variables(variables)
                                .send()
                                ;
                    })
                    .name(WORKER_SEND_TRANSFER_REQUEST + dfspid)
                    .maxJobsActive(workerMaxJobs)
                    .open();
        }
    }

    /** The payee's quote expiry from the payeeQuoteResponse variable, or null if it can't be read. */
    private String quoteExpiration(Object payeeQuoteResponse) {
        if (payeeQuoteResponse == null) {
            return null;
        }
        try {
            return objectMapper.readTree(payeeQuoteResponse.toString()).path("expiration").asText(null);
        } catch (Exception e) {
            logger.warn("Could not read the quote's expiration: {}", e.getMessage());
            return null;
        }
    }
}
