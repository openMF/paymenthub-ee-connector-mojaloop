package org.mifos.connector.mojaloop.party;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.mifos.connector.mojaloop.config.MojaloopProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * If the switch never calls back, a stale entry is discarded (and logged) the moment the next
 * caller touches that identifier - typically a BPMN-level retry re-registering the same
 * transaction - rather than waiting on the background sweep, so a retry always gets a fresh
 * outbound attempt instead of being told a (dead) lookup is already in flight. The sweep
 * remains as a backstop for identifiers nothing ever retries, so they don't leak forever.
 */
@Component
public class PartyLookupCorrelationCache {

    private static final Logger logger = LoggerFactory.getLogger(PartyLookupCorrelationCache.class);

    private final long pendingTimeoutMs;

    private final Map<String, PendingLookup> waitersByPartyId = new HashMap<>();

    private ScheduledExecutorService sweeper;

    public PartyLookupCorrelationCache(MojaloopProperties mojaloopProperties) {
        this.pendingTimeoutMs = mojaloopProperties.partyLookupTimeoutMs();
    }

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
     * Registering the same transaction again while its lookup is still pending (for example a
     * retry that arrives before the timeout) is a no-op, so the callback reaches it only once.
     *
     * @return true if this is the first waiter for partyId and the caller should send the
     *         outbound lookup; false if a lookup is already in flight for it.
     */
    public synchronized boolean registerWaiter(String partyId, String transactionId) {
        PendingLookup pending = waitersByPartyId.get(partyId);
        if (pending != null && isStale(pending)) {
            logAbandoned(partyId, pending, "a new lookup for the same identifier arrived");
            pending = null;
        }
        if (pending == null) {
            pending = new PendingLookup();
            waitersByPartyId.put(partyId, pending);
        }
        boolean isFirstWaiter = pending.transactionIds.isEmpty();
        pending.transactionIds.add(transactionId);
        return isFirstWaiter;
    }

    public synchronized List<String> resolveWaiters(String partyId) {
        PendingLookup pending = waitersByPartyId.remove(partyId);
        return pending == null ? List.of() : List.copyOf(pending.transactionIds);
    }

    private synchronized void evictStaleEntries() {
        waitersByPartyId.entrySet().removeIf(entry -> {
            PendingLookup pending = entry.getValue();
            boolean isStale = isStale(pending);
            if (isStale) {
                logAbandoned(entry.getKey(), pending, "the periodic sweep ran");
            }
            return isStale;
        });
    }

    private boolean isStale(PendingLookup pending) {
        return System.currentTimeMillis() - pending.registeredAtMs >= pendingTimeoutMs;
    }

    private void logAbandoned(String partyId, PendingLookup pending, String discoveredVia) {
        logger.warn("Party lookup for partyId '{}' got no callback from the switch after {}ms; " +
                        "abandoning {} waiting transaction(s), discovered because {}: {}",
                partyId, System.currentTimeMillis() - pending.registeredAtMs, pending.transactionIds.size(),
                discoveredVia, pending.transactionIds);
    }

    private static final class PendingLookup {
        private final long registeredAtMs = System.currentTimeMillis();
        private final Set<String> transactionIds = new LinkedHashSet<>();
    }
}
