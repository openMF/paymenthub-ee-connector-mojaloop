package org.mifos.connector.mojaloop.zeebe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mifos.connector.mojaloop.camel.config.CamelProperties.HEADER_DATE;
import static org.mifos.connector.mojaloop.camel.config.CamelProperties.HEADER_TRACEPARENT;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * A missing traceparent is normal (the flow wasn't started with one) and must not be logged at ERROR. Any other
 * missing name still is.
 */
class ZeebeProcessStarterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(ZeebeProcessStarter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
    }

    @Test
    void missingTraceparentVariableIsNotAnError() {
        Exchange exchange = exchange();
        ZeebeProcessStarter.zeebeVariablesToCamelHeaders(Map.of(), exchange, HEADER_TRACEPARENT);

        assertThat(levels()).containsExactly(Level.DEBUG);
        assertThat(exchange.getIn().getHeader(HEADER_TRACEPARENT)).isNull();
    }

    @Test
    void missingTraceparentHeaderIsNotAnError() {
        Map<String, Object> variables = new HashMap<>();
        ZeebeProcessStarter.camelHeadersToZeebeVariables(exchange(), variables, HEADER_TRACEPARENT);

        assertThat(levels()).containsExactly(Level.DEBUG);
        assertThat(variables).containsEntry(HEADER_TRACEPARENT, null);
    }

    @Test
    void missingRequiredNameIsStillAnError() {
        ZeebeProcessStarter.zeebeVariablesToCamelHeaders(Map.of(), exchange(), HEADER_DATE);
        ZeebeProcessStarter.camelHeadersToZeebeVariables(exchange(), new HashMap<>(), HEADER_DATE);

        assertThat(levels()).containsExactly(Level.ERROR, Level.ERROR);
    }

    @Test
    void presentNamesAreCopiedWithoutLogging() {
        Exchange exchange = exchange();
        ZeebeProcessStarter.zeebeVariablesToCamelHeaders(Map.of(HEADER_TRACEPARENT, "00-abc-def-01"), exchange,
                HEADER_TRACEPARENT);

        assertThat(levels()).isEmpty();
        assertThat(exchange.getIn().getHeader(HEADER_TRACEPARENT)).isEqualTo("00-abc-def-01");
    }

    private static Exchange exchange() {
        return new DefaultExchange(new DefaultCamelContext());
    }

    private List<Level> levels() {
        return appender.list.stream().map(ILoggingEvent::getLevel).toList();
    }
}
