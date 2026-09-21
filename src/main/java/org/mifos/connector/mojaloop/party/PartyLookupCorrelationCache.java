package org.mifos.connector.mojaloop.party;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The switch's party-lookup callback (PUT /parties/{type}/{id}) carries only the party
 * identifier, not the transaction that requested it. Correlating N outbound requests to N
 * callbacks by send order breaks if the switch answers out of order or drops one, so instead
 * this coalesces concurrent lookups for the same identifier into a single outbound request
 * and fans its one answer out to every transaction waiting on it.
 * <p>
 * If the switch never calls back at all for an identifier, its waiter entry would otherwise
 * sit here forever, so a background sweep evicts and logs any entry older than the configured
 * timeout.
 */
@Component
public class PartyLookupCorrelationCache {

    private static final Logger logger = LoggerFactory.getLogger(PartyLookupCorrelationCache.class);

    @Value("${mojaloop.party-lookup-timeout-ms:30000}")
    private long pendingTimeoutMs;

    private final Map<String, PendingLookup> waitersByPartyId = new HashMap<>();

    private ScheduledExecutorService sweeper;

    @PostConstruct
    void start() {
        sweeper = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "party-lookup-correlation-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        sweeper.scheduleWithFixedDelay(this::evictStaleEntries, pendingTimeoutMs, pendingTimeoutMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        sweeper.shutdownNow();
    }

    /**
     * @return true if this is the first waiter for partyId and the caller should send the
     *         outbound lookup; false if a lookup is already in flight for it.
     */
    public synchronized boolean registerWaiter(String partyId, String transactionId) {
        PendingLookup pending = waitersByPartyId.computeIfAbsent(partyId, key -> new PendingLookup());
        boolean isFirstWaiter = pending.transactionIds.isEmpty();
        pending.transactionIds.add(transactionId);
        return isFirstWaiter;
    }

    public synchronized List<String> resolveWaiters(String partyId) {
        PendingLookup pending = waitersByPartyId.remove(partyId);
        return pending == null ? List.of() : pending.transactionIds;
    }

    private synchronized void evictStaleEntries() {
        long now = System.currentTimeMillis();
        waitersByPartyId.entrySet().removeIf(entry -> {
            PendingLookup pending = entry.getValue();
            long ageMs = now - pending.registeredAtMs;
            boolean isStale = ageMs >= pendingTimeoutMs;
            if (isStale) {
                logger.warn("Party lookup for partyId '{}' got no callback from the switch after {}ms; " +
                                "evicting and abandoning {} waiting transaction(s), which will never be " +
                                "unblocked by this lookup: {}",
                        entry.getKey(), ageMs, pending.transactionIds.size(), pending.transactionIds);
            }
            return isStale;
        });
    }

    private static final class PendingLookup {
        private final long registeredAtMs = System.currentTimeMillis();
        private final List<String> transactionIds = new ArrayList<>();
    }
}
