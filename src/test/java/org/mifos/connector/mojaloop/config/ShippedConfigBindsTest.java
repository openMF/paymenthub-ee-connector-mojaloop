package org.mifos.connector.mojaloop.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Every property these records ask for has to be there, or the connector must refuse to start and say which one is
 * missing. That is what the plain {@code @Value} declarations and Camel {@code {{...}}} placeholders did before they
 * were replaced, so these tests hold the replacement to the same promise, and they read the real application.yml
 * rather than a copy of it.
 *
 * <p>
 * The missing-section check runs with no configuration file at all. Loading only this module's application.yml is not
 * enough to remove a section, because paymenthub-ee-core puts its own application.yaml on the classpath and that one
 * also sets {@code zeebe.*}.
 * </p>
 */
class ShippedConfigBindsTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ BpmnFlowProperties.class, ConnectorCamelProperties.class, ConnectorProperties.class,
            MojaloopProperties.class, SwitchProperties.class, ZeebeProperties.class })
    static class AllRecords {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BpmnFlowProperties.class)
    static class OnlyBpmnFlows {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ConnectorCamelProperties.class)
    static class OnlyCamel {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ConnectorProperties.class)
    static class OnlyConnector {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MojaloopProperties.class)
    static class OnlyMojaloop {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SwitchProperties.class)
    static class OnlySwitch {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ZeebeProperties.class)
    static class OnlyZeebe {}

    /** One configuration class per record, keyed by the prefix that record binds. */
    private static final Map<String, Class<?>> ONE_RECORD_EACH = Map.of("bpmn.flows", OnlyBpmnFlows.class, "camel", OnlyCamel.class,
            "connector", OnlyConnector.class, "mojaloop", OnlyMojaloop.class, "switch", OnlySwitch.class, "zeebe", OnlyZeebe.class);

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withConfiguration(
                AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class));
    }

    private ApplicationContextRunner withShippedYaml() {
        return runner().withInitializer(new ConfigDataApplicationContextInitializer());
    }

    @Test
    void theShippedApplicationYamlFillsEveryField() {
        withShippedYaml().withUserConfiguration(AllRecords.class).run(context -> {
            assertThat(context).hasNotFailed();
            BpmnFlowProperties bpmn = context.getBean(BpmnFlowProperties.class);
            assertThat(bpmn.partyLookup()).isEqualTo("PayeePartyLookup-{tenant}");
            assertThat(bpmn.quote()).isEqualTo("PayeeQuoteTransfer-{tenant}");
            assertThat(bpmn.transactionRequest()).isEqualTo("PayerTransactionRequest-{tenant}");
            assertThat(context.getBean(ConnectorCamelProperties.class).serverPort()).isEqualTo(5000);
            assertThat(context.getBean(ConnectorProperties.class).ilpSecret()).isNotEmpty();
            MojaloopProperties mojaloop = context.getBean(MojaloopProperties.class);
            assertThat(mojaloop.enabled()).isTrue();
            assertThat(mojaloop.perfMode()).isFalse();
            assertThat(mojaloop.perfRespDelay()).isEqualTo(100);
            SwitchProperties switchProperties = context.getBean(SwitchProperties.class);
            assertThat(switchProperties.alsHost()).isEqualTo("http://fspiop-api-svc.vnext.svc.cluster.local:4000");
            assertThat(switchProperties.transactionsHost()).isEqualTo("http://fspiop-api-svc.vnext.svc.cluster.local:4000");
            // application.yml ships oracle-host empty, and an empty value is accepted
            assertThat(switchProperties.oracleHost()).isEmpty();
            ZeebeProperties zeebe = context.getBean(ZeebeProperties.class);
            assertThat(zeebe.client().maxExecutionThreads()).isEqualTo(50);
            assertThat(zeebe.client().evenlyAllocatedMaxJobs()).isEqualTo(1000);
            assertThat(zeebe.client().pollInterval()).isEqualTo(10);
        });
    }

    @Test
    void everyRecordRefusesToStartWhenItsSectionIsMissing() {
        ONE_RECORD_EACH.forEach((prefix, configuration) -> runner().withUserConfiguration(configuration).run(context -> {
            assertThat(context).as("context with nothing configured under '%s'", prefix).hasFailed();
            assertThat(context.getStartupFailure()).as("failure for '%s'", prefix).hasStackTraceContaining("BindValidationException")
                    .hasStackTraceContaining("Binding validation errors on " + prefix);
        }));
    }

    @Test
    void aValueSetToNothingOnANumberFieldStopsStartup() {
        withShippedYaml().withUserConfiguration(OnlyCamel.class).withPropertyValues("camel.server-port=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("Binding validation errors on camel");
        });
    }

    @Test
    void aValueSetToNothingOnABooleanFieldStopsStartup() {
        withShippedYaml().withUserConfiguration(OnlyMojaloop.class).withPropertyValues("mojaloop.enabled=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("Binding validation errors on mojaloop");
        });
    }

    @Test
    void aValueSetToNothingOnAStringFieldIsAcceptedJustAsItWasBefore() {
        withShippedYaml().withUserConfiguration(OnlySwitch.class).withPropertyValues("switch.quote-service=").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SwitchProperties.class).quoteService()).isEmpty();
        });
    }
}
