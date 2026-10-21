package com.portalcursos.ng02.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de criações de checkout de cartão por usuário (5 a cada 10 minutos), para um aluno não
 * disparar uma enxurrada de chamadas ao PagBank. Em memória e por instância: durante a troca
 * blue-green duas instâncias coexistem por instantes e cada uma conta separado, o que é aceitável
 * para este limite (a baixa é idempotente e o PagBank é a barreira real).
 */
@Component
public class CardCheckoutRateLimiter {

    static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(10);

    private final Clock clock;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    @Autowired
    public CardCheckoutRateLimiter() {
        this(Clock.systemUTC());
    }

    CardCheckoutRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Registra a tentativa e devolve false se o usuário já esgotou o limite na janela. */
    public boolean tryAcquire(String userKey) {
        if (userKey == null || userKey.isBlank()) {
            return false;
        }
        long now = clock.millis();
        long cutoff = now - WINDOW.toMillis();
        Deque<Long> deque = attempts.computeIfAbsent(userKey, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() <= cutoff) {
                deque.pollFirst();
            }
            if (deque.size() >= MAX_ATTEMPTS) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}
