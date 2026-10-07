package org.mifos.connector.mojaloop.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The Mojaloop switch settings: {@code switch.*}.
 *
 * <p>
 * Two kinds of value live under this prefix. The {@code *-host} values are callback URLs: the connector hands them to the switch
 * as {@code X-Lookup-Callback-Url}, {@code X-Quote-Callback-Url} and {@code X-Transfer-Callback-Url}, and the switch calls back
 * on them. The {@code *-service} values are the {@code Host} header the connector puts on its outgoing calls, set in
 * {@code MojaloopUtil}; the gazelle deployment sets all four to empty, because a Host header is only needed when something in
 * front of the switch routes by name.
 * </p>
 *
 * <p>
 * Every value is required, as it was when each one was read through a bare {@code @Value} or a Camel {@code {{...}}}
 * placeholder: a missing key stops startup. An empty value is accepted, which is what lets the deployment leave the
 * {@code *-service} values and {@code oracle-host} empty.
 * </p>
 *
 * @param alsHost
 *            account lookup callback URL
 * @param accountLookupService
 *            Host header for account lookup calls, empty when none is needed
 * @param quotesHost
 *            quotes callback URL
 * @param quoteService
 *            Host header for quote calls, empty when none is needed
 * @param transfersHost
 *            transfers callback URL
 * @param transferService
 *            Host header for transfer calls, empty when none is needed
 * @param transactionsHost
 *            transaction requests callback URL
 * @param transactionRequestService
 *            Host header for transaction request calls, empty when none is needed
 * @param oracleHost
 *            oracle host, empty when no oracle is configured
 */

@Validated
@ConfigurationProperties(prefix = "switch")
public record SwitchProperties(
        @NotNull String alsHost,
        @NotNull String accountLookupService,
        @NotNull String quotesHost,
        @NotNull String quoteService,
        @NotNull String transfersHost,
        @NotNull String transferService,
        @NotNull String transactionsHost,
        @NotNull String transactionRequestService, @NotNull String oracleHost) {
}
