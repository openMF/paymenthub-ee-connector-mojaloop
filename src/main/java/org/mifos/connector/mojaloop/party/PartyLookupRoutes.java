package org.mifos.connector.mojaloop.party;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.mifos.connector.common.camel.ErrorHandlerRouteBuilder;
import org.mifos.connector.common.channel.dto.TransactionChannelRequestDTO;
import org.mifos.connector.common.mojaloop.dto.Party;
import org.mifos.connector.common.mojaloop.dto.PartyIdInfo;
import org.mifos.connector.common.mojaloop.dto.PartySwitchResponseDTO;
import org.mifos.connector.common.mojaloop.type.IdentifierType;
import org.mifos.connector.mojaloop.camel.trace.AddTraceHeaderProcessor;
import org.mifos.connector.mojaloop.config.BpmnFlowProperties;
import org.mifos.connector.mojaloop.config.MojaloopProperties;
import org.mifos.connector.mojaloop.config.SwitchProperties;
import org.mifos.connector.mojaloop.properties.PartyProperties;
import org.mifos.connector.mojaloop.util.MojaloopUtil;
import org.mifos.connector.mojaloop.zeebe.ZeebeProcessStarter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import static org.mifos.connector.common.ams.dto.InteropIdentifierType.MSISDN;
import static org.mifos.connector.common.mojaloop.type.MojaloopHeaders.FSPIOP_DESTINATION;
import static org.mifos.connector.common.mojaloop.type.MojaloopHeaders.FSPIOP_SOURCE;
import static org.mifos.connector.mojaloop.camel.config.CamelProperties.*;
import static org.mifos.connector.mojaloop.zeebe.ZeebeVariables.*;

@Component
public class PartyLookupRoutes extends ErrorHandlerRouteBuilder {

    private final String partyLookupFlow;

    private final boolean mojaPerfMode;

    private final int mojaPerfRespDelay;

    private final String alsHost;

    @Autowired
    private Processor pojoToString;

    @Autowired
    private ZeebeProcessStarter zeebeProcessStarter;

    @Autowired
    private PartyProperties partyProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MojaloopUtil mojaloopUtil;

    @Autowired
    private AddTraceHeaderProcessor addTraceHeaderProcessor;

    @Autowired
    private PartyLookupCorrelationCache partyLookupCorrelationCache;

    @Autowired
    private PartiesResponseProcessor partiesResponseProcessor;

    public PartyLookupRoutes(BpmnFlowProperties bpmnFlows, MojaloopProperties mojaloopProperties, SwitchProperties switchProperties) {
        super.configure();
        this.partyLookupFlow = bpmnFlows.partyLookup();
        this.mojaPerfMode = mojaloopProperties.perfMode();
        this.mojaPerfRespDelay = mojaloopProperties.perfRespDelay();
        this.alsHost = switchProperties.alsHost();
    }

    @Override
    public void configure() {
        //@formatter:off
        from("rest:GET:/switch/parties/{" + PARTY_ID_TYPE + "}/{" + PARTY_ID + "}")
                .log(LoggingLevel.INFO, "## SWITCH -> PAYER/PAYEE inbound GET parties - STEP 2")
                .choice()
                    .when(e -> mojaPerfMode)
                        .wireTap("direct:send-delayed-party-dummy-response")
                    .endChoice()
                    .otherwise()
                        .process(e -> {
                            String payeeFsp = e.getIn().getHeader(FSPIOP_DESTINATION.headerName(), String.class);
                            String tenantId = "bluebank"; // TODO: remove this when we have a proper way to set the tenantId
                                    zeebeProcessStarter.startZeebeWorkflow(partyLookupFlow.replace("{tenant}", tenantId),
                                            variables -> {
                                                variables.put(HEADER_DATE, e.getIn().getHeader(HEADER_DATE));
                                                variables.put(HEADER_TRACEPARENT, e.getIn().getHeader(HEADER_TRACEPARENT));
                                                variables.put(FSPIOP_SOURCE.headerName(), e.getIn().getHeader(FSPIOP_SOURCE.headerName()));
                                                variables.put(PAYEE_TENANT_ID, partyProperties.getPartyByDfsp(payeeFsp).getTenantId());
                                                variables.put(PARTY_ID_TYPE, e.getIn().getHeader(PARTY_ID_TYPE));
                                                variables.put(PARTY_ID, e.getIn().getHeader(PARTY_ID));
                                                variables.put(TENANT_ID, tenantId);
                                                if(e.getIn().getHeader("X-Lookup-Callback-Url")!=null) {
                                                    variables.put("X-Lookup-Callback-Url", e.getIn().getHeader("X-Lookup-Callback-Url"));
                                                }
                                                else {
                                                    variables.put("X-Lookup-Callback-Url", alsHost);
                                                }
                                            });
                                }
                        )
                    .endChoice()
                .end()
                .setBody(constant("Party lookup workflow started successfully"))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(202));
        //@formatter:on

        from("rest:PUT:/switch/parties/" + MSISDN + "/{partyId}")
                .setProperty(CLASS_TYPE, constant(PartySwitchResponseDTO.class))
                .to("direct:body-unmarshling")
                .process(e -> e.setProperty(RESOLVED_PARTY_LOOKUP_WAITERS,
                        partyLookupCorrelationCache.resolveWaiters(e.getIn().getHeader(PARTY_ID, String.class))))
                .to("direct:parties-step4");

        from("direct:parties-step4")
                .log(LoggingLevel.DEBUG, "######## SWITCH -> PAYER - response for parties request  - STEP 4")
                .process(e -> {
                    // the mojaloop-disabled dummy-response path (PartyLookupWorkers) sends here
                    // directly with a single CACHED_TRANSACTION_ID and no waiter list
                    List<String> transactionIds = e.getProperty(RESOLVED_PARTY_LOOKUP_WAITERS, List.class);
                    if (transactionIds == null) {
                        transactionIds = List.of(e.getProperty(CACHED_TRANSACTION_ID, String.class));
                    }
                    for (String transactionId : transactionIds) {
                        e.setProperty(CACHED_TRANSACTION_ID, transactionId);
                        partiesResponseProcessor.process(e);
                    }
                })
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200));

        from("rest:PUT:/switch/parties/" + MSISDN + "/{partyId}/error")
                .log(LoggingLevel.ERROR, "######## SWITCH -> PAYER - parties error")
                .setProperty(PARTY_LOOKUP_FAILED, constant(true))
                .process(e -> {
                    List<String> transactionIds = partyLookupCorrelationCache.resolveWaiters(e.getIn().getHeader(PARTY_ID, String.class));
                    for (String transactionId : transactionIds) {
                        e.setProperty(CACHED_TRANSACTION_ID, transactionId);
                        partiesResponseProcessor.process(e);
                    }
                })
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200));

        from("direct:send-delayed-party-dummy-response")
                .delay(mojaPerfRespDelay)
                .process(e -> {
                    String host = e.getIn().getHeader("Host", String.class).split(":")[0];

                    Party party = new Party( // only return fspId from configuration
                            new PartyIdInfo(IdentifierType.valueOf(e.getIn().getHeader(PARTY_ID_TYPE, String.class)),
                                    e.getIn().getHeader(PARTY_ID, String.class),
                                    null,
                                    partyProperties.getPartyByDomainAndFspId(host, null).getFspId()),
                            null,
                            null,
                            null);
                    e.setProperty(PAYEE_PARTY_RESPONSE, objectMapper.writeValueAsString(party));
                })
                .to("direct:send-parties-response");

        from("direct:send-parties-response")
                .log(LoggingLevel.INFO, "######## PAYEE -> SWITCH - party lookup response - STEP 3")
                .id("send-parties-response")
                .process(exchange -> {
                    Party party = objectMapper.readValue(exchange.getProperty(PAYEE_PARTY_RESPONSE, String.class), Party.class);
                    exchange.setProperty(PARTY_ID, party.getPartyIdInfo().getPartyIdentifier());
                    exchange.setProperty(PARTY_ID_TYPE, party.getPartyIdInfo().getPartyIdType().name());
                    exchange.getIn().setBody(new PartySwitchResponseDTO(party));
                    mojaloopUtil.setPartyHeadersResponse(exchange);
                })
                .process(pojoToString)
                .setHeader(Exchange.HTTP_METHOD, constant("PUT"))
                .setProperty(ENDPOINT, simple("/parties/${exchangeProperty." + PARTY_ID_TYPE + "}/${exchangeProperty." + PARTY_ID + "}"))
                .to("direct:external-api-call");

        from("direct:send-parties-error-response")
                .log(LoggingLevel.INFO, "######## PAYEE -> SWITCH - party lookup error response - STEP 3")
                .id("send-parties-error-response")
                .process(exchange -> {
                    exchange.getIn().setBody(exchange.getProperty(ERROR_INFORMATION));
                    mojaloopUtil.setPartyHeadersResponse(exchange);
                })
                .setHeader(Exchange.HTTP_METHOD, constant("PUT"))
                .setProperty(HOST, simple("{{switch.als-host}}"))
                .setProperty(ENDPOINT, simple("/parties/${exchangeProperty." + PARTY_ID_TYPE + "}/${exchangeProperty." + PARTY_ID + "}/error"))
                .to("direct:external-api-call");

        from("direct:send-party-lookup")
                .id("send-party-lookup")
                .log(LoggingLevel.INFO, "######## PAYER -> SWITCH - party lookup request - STEP 1")
                .process(e -> {

                    TransactionChannelRequestDTO channelRequest = objectMapper.readValue(e.getProperty(CHANNEL_REQUEST, String.class), TransactionChannelRequestDTO.class);
                    PartyIdInfo requestedParty = e.getProperty(IS_RTP_REQUEST, Boolean.class) ? channelRequest.getPayer().getPartyIdInfo() : channelRequest.getPayee().getPartyIdInfo();
                    e.setProperty(PARTY_ID_TYPE, requestedParty.getPartyIdType());
                    e.setProperty(PARTY_ID, requestedParty.getPartyIdentifier());
                    e.getIn().setHeader(FSPIOP_SOURCE.headerName(), partyProperties.getPartyByTenant(e.getProperty(TENANT_ID, String.class)).getFspId());
                    e.getIn().setHeader(FSPIOP_SOURCE.headerName(), "greenbank");
                    mojaloopUtil.setPartyHeadersRequest(e);

                    boolean isFirstWaiter = partyLookupCorrelationCache.registerWaiter(
                            requestedParty.getPartyIdentifier(), e.getProperty(TRANSACTION_ID, String.class));
                    e.setProperty(PARTY_LOOKUP_ALREADY_PENDING, !isFirstWaiter);
                })
                .choice()
                    .when(e -> Boolean.TRUE.equals(e.getProperty(PARTY_LOOKUP_ALREADY_PENDING, Boolean.class)))
                        .log(LoggingLevel.INFO, "######## PAYER -> SWITCH - party lookup already in flight for ${exchangeProperty." + PARTY_ID + "}, not resending")
                    .otherwise()
                        .process(addTraceHeaderProcessor)
                        .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                        .setProperty(HOST, simple("{{switch.als-host}}"))
                        .setProperty(ENDPOINT, simple("/parties/${exchangeProperty." + PARTY_ID_TYPE + "}/${exchangeProperty." + PARTY_ID + "}"))
                        .to("direct:external-api-call")
                .end();
    }
}
