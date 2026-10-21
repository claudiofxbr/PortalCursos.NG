package com.portalcursos.ng02.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Regra única do ambiente de pagamentos: fora de produção o cartão de teste do PagBank é público,
 * então só administrador ou testador listado em {@code pagbank.sandbox-testers} paga. Em produção
 * a restrição não se aplica. Produção exige o valor exato {@code production}.
 */
@Component
public class PagBankEnvironmentPolicy {

    private final boolean production;
    private final Set<String> sandboxTesters;

    public PagBankEnvironmentPolicy(
            @Value("${pagbank.env:sandbox}") String env,
            @Value("${pagbank.sandbox-testers:}") String sandboxTesters) {
        this.production = "production".equals(env);
        this.sandboxTesters = Arrays.stream(sandboxTesters.split(","))
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isProduction() {
        return production;
    }

    public boolean canPay(boolean admin, String email) {
        if (production || admin) {
            return true;
        }
        return email != null && sandboxTesters.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
