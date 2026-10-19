package com.portalcursos.ng02.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardCheckoutRateLimiterTest {

    private static final class MutableClock extends Clock {
        long millis = 1_000_000L;

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }

    @Test
    void permiteCincoEBloqueiaASexta() {
        CardCheckoutRateLimiter limiter = new CardCheckoutRateLimiter(new MutableClock());
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("u1"));
        }
        assertFalse(limiter.tryAcquire("u1"));
    }

    @Test
    void usuariosSaoContadosSeparadamente() {
        CardCheckoutRateLimiter limiter = new CardCheckoutRateLimiter(new MutableClock());
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("u1");
        }
        assertTrue(limiter.tryAcquire("u2"));
    }

    @Test
    void liberaDepoisDaJanela() {
        MutableClock clock = new MutableClock();
        CardCheckoutRateLimiter limiter = new CardCheckoutRateLimiter(clock);
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("u1");
        }
        assertFalse(limiter.tryAcquire("u1"));
        clock.millis += 10 * 60_000L + 1;
        assertTrue(limiter.tryAcquire("u1"));
    }

    @Test
    void usuarioSemIdNaoPassa() {
        assertFalse(new CardCheckoutRateLimiter(new MutableClock()).tryAcquire(null));
    }
}
