package org.mifos.connector.mojaloop.party;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mifos.connector.mojaloop.config.MojaloopProperties;

class PartyLookupCorrelationCacheTest {

    private static PartyLookupCorrelationCache cache(long timeoutMs) {
        return new PartyLookupCorrelationCache(new MojaloopProperties(true, false, 100, timeoutMs));
    }

    @Test
    void onlyTheFirstWaiterSendsTheLookupAndTheAnswerReachesEveryWaiter() {
        PartyLookupCorrelationCache cache = cache(60_000);

        assertThat(cache.registerWaiter("123", "tx-1")).isTrue();
        assertThat(cache.registerWaiter("123", "tx-2")).isFalse();

        assertThat(cache.resolveWaiters("123")).containsExactly("tx-1", "tx-2");
        assertThat(cache.resolveWaiters("123")).isEmpty();
    }

    @Test
    void aTransactionRegisteredTwiceIsAnsweredOnce() {
        PartyLookupCorrelationCache cache = cache(60_000);

        assertThat(cache.registerWaiter("123", "tx-1")).isTrue();
        assertThat(cache.registerWaiter("123", "tx-1")).isFalse();

        assertThat(cache.resolveWaiters("123")).containsExactly("tx-1");
    }

    @Test
    void aStaleLookupIsDiscardedSoTheNextCallerSendsAFreshOne() {
        PartyLookupCorrelationCache cache = cache(0);

        assertThat(cache.registerWaiter("123", "tx-1")).isTrue();
        assertThat(cache.registerWaiter("123", "tx-2")).isTrue();

        assertThat(cache.resolveWaiters("123")).containsExactly("tx-2");
    }
}
