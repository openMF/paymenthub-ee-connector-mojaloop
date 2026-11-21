package org.mifos.connector.mojaloop.ilp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.mifos.connector.mojaloop.config.ConnectorProperties;

/**
 * PHEE-476: as payee, the connector committed transfers without a fulfilment, which FSPIOP requires on a committed
 * transfer. The payer now treats COMMITTED without a fulfilment as PENDING, so the payee must send it.
 */
class IlpBuilderFulfilmentTest {

    private static final String PACKET = "AYIDKgAAAAAAAAPoHGcuYmx1ZWJhbmsubXNpc2RuLjA0OTU4MjI0MTKCAwVleUowY21GdWMyRmpkR2x2Ymtsaw";

    private static IlpBuilder builder(String secret) throws Exception {
        IlpBuilder builder = new IlpBuilder(new ConnectorProperties(secret));
        Field handler = IlpBuilder.class.getDeclaredField("ilpConditionHandlerImpl");
        handler.setAccessible(true);
        handler.set(builder, new IlpConditionHandlerImpl());
        return builder;
    }

    @Test
    void fulfilmentMatchesTheConditionThisConnectorIssued() throws Exception {
        IlpConditionHandlerImpl handler = new IlpConditionHandlerImpl();
        String condition = handler.generateCondition(PACKET, "s3cret".getBytes());

        String fulfilment = builder("s3cret").fulfilmentFor(PACKET, condition);

        assertThat(fulfilment).isNotBlank();
        assertThat(handler.validateFulfillmentAgainstCondition(fulfilment, condition)).isTrue();
    }

    @Test
    void conditionIssuedBySomeoneElseIsNotFulfilled() throws Exception {
        String foreignCondition = new IlpConditionHandlerImpl().generateCondition(PACKET, "other-secret".getBytes());

        assertThatThrownBy(() -> builder("s3cret").fulfilmentFor(PACKET, foreignCondition))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> builder("s3cret").fulfilmentFor(PACKET, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
