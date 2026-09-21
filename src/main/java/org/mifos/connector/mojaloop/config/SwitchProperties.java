package org.mifos.connector.mojaloop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

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
 * Binding them here changes nothing about when a value is accepted: the names, the defaults and the behaviour are the ones the
 * connector already had.
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

@ConfigurationProperties(prefix = "switch")
public record SwitchProperties(
        String alsHost,
        @DefaultValue("") String accountLookupService,
        String quotesHost,
        @DefaultValue("") String quoteService,
        String transfersHost,
        @DefaultValue("") String transferService,
        String transactionsHost,
        @DefaultValue("") String transactionRequestService, @DefaultValue("") String oracleHost) {
}
