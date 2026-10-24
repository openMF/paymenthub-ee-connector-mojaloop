package org.mifos.connector.mojaloop.zeebe;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.mifos.connector.mojaloop.camel.config.CamelProperties.HEADER_TRACEPARENT;

@Component
public class ZeebeProcessStarter {

    private static Logger logger = LoggerFactory.getLogger(ZeebeProcessStarter.class);

    // Names that are legitimately absent, e.g. traceparent when the flow wasn't started with one
    private static final Set<String> OPTIONAL_NAMES = Set.of(HEADER_TRACEPARENT);

    @Autowired(required = false)
    private ZeebeClient zeebeClient;

    public void startZeebeWorkflow(String workflowId, Consumer<Map<String, Object>> variablesLambda) {
        Map<String, Object> variables = new HashMap<>();
        variables.put(ZeebeVariables.ORIGIN_DATE, Instant.now().toEpochMilli());
        variablesLambda.accept(variables);



        ProcessInstanceEvent instance = zeebeClient.newCreateInstanceCommand()
                .bpmnProcessId(workflowId)
                .latestVersion()
                .variables(variables)
                .send().join();

        logger.info("zeebee workflow instance from process {} started with key {}", workflowId, instance.getProcessInstanceKey());
    }

    public static void zeebeVariablesToCamelHeaders(Map<String, Object> variables, Exchange exchange, String... names) {
        for (String name : names) {
            Object value = variables.get(name);
            if (value == null) {
                logMissing("failed to find Zeebe variable name {}", name);
            }
            exchange.getIn().setHeader(name, value);
        }
    }

    public static void camelHeadersToZeebeVariables(Exchange exchange, Map<String, Object> variables, String... names) {
        for (String name : names) {
            String header = exchange.getIn().getHeader(name, String.class);
            if (header == null) {
                logMissing("failed to find Camel Exchange header {}", name);
            }
            variables.put(name, header);
        }
    }

    private static void logMissing(String message, String name) {
        if (OPTIONAL_NAMES.contains(name)) {
            logger.debug(message, name);
        } else {
            logger.error(message, name);
        }
    }
}
