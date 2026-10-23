package com.portalcursos.ng02.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Confere se a configuração do Pix (PagBank) está pronta e coerente, e diz EXATAMENTE qual variável do servidor
 * precisa de ajuste. Nunca devolve nem registra valores de token ou segredo: só o nome da variável e o problema.
 *
 * <p>Serve para ligar o Pix de verdade em produção (token de produção, URL da API de produção, segredo do webhook,
 * URL pública em HTTPS) sem descobrir os erros só quando uma cobrança falha.
 */
@Service
public class PixConfigCheck {

    public static final String OK = "OK";
    public static final String ATENCAO = "ATENCAO";
    public static final String NAO_CONFIGURADO = "NAO_CONFIGURADO";

    private static final Logger logger = LoggerFactory.getLogger(PixConfigCheck.class);
    private static final int MIN_WEBHOOK_SECRET_LENGTH = 32;
    private static final int MIN_TOKEN_LENGTH = 20;

    /** Um ponto a ajustar: variável de ambiente (nome, nunca o valor) e o que está errado. */
    public record Finding(String variable, String problem) {
    }

    /** Resultado: situação geral, ambiente em uso (SANDBOX, PRODUCAO ou DESCONHECIDO) e o que ajustar. */
    public record Report(String status, String environment, List<Finding> findings) {
    }

    private final String token;
    private final String baseUrl;
    private final String webhookSecret;
    private final String publicApiUrl;
    private final String publicAppUrl;
    private final String pagbankEnv;

    public PixConfigCheck(@Value("${pagseguro.api.token:}") String token,
                          @Value("${pagseguro.api.base-url:}") String baseUrl,
                          @Value("${pagseguro.webhook.secret:}") String webhookSecret,
                          @Value("${portalcursos.public-api-url:}") String publicApiUrl,
                          @Value("${portalcursos.public-app-url:}") String publicAppUrl,
                          @Value("${pagbank.env:sandbox}") String pagbankEnv) {
        this.token = token;
        this.baseUrl = baseUrl;
        this.webhookSecret = webhookSecret;
        this.publicApiUrl = publicApiUrl;
        this.publicAppUrl = publicAppUrl;
        this.pagbankEnv = pagbankEnv;
    }

    public Report check() {
        List<Finding> findings = new ArrayList<>();
        boolean missingEssential = false;

        if (isBlank(token)) {
            findings.add(new Finding("PAGSEGURO_API_TOKEN", "não definido: sem ele o Pix fica indisponível"));
            missingEssential = true;
        } else if (token.trim().length() < MIN_TOKEN_LENGTH || looksLikePlaceholder(token)) {
            findings.add(new Finding("PAGSEGURO_API_TOKEN", "parece um valor de exemplo ou incompleto: gere o token no painel do PagBank"));
        }

        if (isBlank(webhookSecret)) {
            findings.add(new Finding("PAGSEGURO_WEBHOOK_SECRET", "não definido: sem ele nenhum Pix pode ser gerado"));
            missingEssential = true;
        } else if (webhookSecret.trim().length() < MIN_WEBHOOK_SECRET_LENGTH) {
            findings.add(new Finding("PAGSEGURO_WEBHOOK_SECRET",
                    "curto demais: use ao menos " + MIN_WEBHOOK_SECRET_LENGTH + " caracteres aleatórios"));
        }

        String environment = environmentOf(baseUrl);
        if ("DESCONHECIDO".equals(environment)) {
            findings.add(new Finding("PAGSEGURO_API_BASE_URL",
                    "URL desconhecida: use https://api.pagseguro.com (produção) ou https://sandbox.api.pagseguro.com (testes)"));
        }
        boolean envIsProduction = "production".equals(pagbankEnv);
        if (envIsProduction && "SANDBOX".equals(environment)) {
            findings.add(new Finding("PAGBANK_ENV", "está \"production\", mas PAGSEGURO_API_BASE_URL aponta para o sandbox"));
        }
        if (!envIsProduction && "PRODUCAO".equals(environment)) {
            findings.add(new Finding("PAGBANK_ENV",
                    "a API é a de PRODUÇÃO mas PAGBANK_ENV não é \"production\": o pagamento por cartão fica restrito a testadores; "
                            + "defina PAGBANK_ENV=production quando for para valer"));
        }

        if (!isPublicHttps(publicApiUrl)) {
            findings.add(new Finding("PORTALCURSOS_PUBLIC_API_URL",
                    "precisa ser uma URL pública em HTTPS (sem localhost): o PagBank recusa a URL de notificação do Pix"));
        }
        if (!isBlank(publicAppUrl) && !isPublicHttps(publicAppUrl)) {
            findings.add(new Finding("PORTALCURSOS_PUBLIC_APP_URL",
                    "deveria ser uma URL pública em HTTPS: é para onde o aluno volta depois de pagar no PagBank"));
        }

        String status = missingEssential ? NAO_CONFIGURADO : findings.isEmpty() ? OK : ATENCAO;
        return new Report(status, environment, List.copyOf(findings));
    }

    /** Registra o resultado na inicialização, para quem opera ver de cara o que ajustar no servidor. */
    @EventListener(ApplicationReadyEvent.class)
    public void logAtStartup() {
        Report report = check();
        logger.info("[PIX-CONFIG] situação={} ambiente={} pendências={}", report.status(), report.environment(), report.findings().size());
        for (Finding finding : report.findings()) {
            logger.warn("[PIX-CONFIG] {}: {}", finding.variable(), finding.problem());
        }
    }

    private static String environmentOf(String url) {
        String host = hostOf(url);
        if (host == null) {
            return "DESCONHECIDO";
        }
        if (host.contains("sandbox")) {
            return "SANDBOX";
        }
        return host.equals("api.pagseguro.com") ? "PRODUCAO" : "DESCONHECIDO";
    }

    private static boolean isPublicHttps(String url) {
        if (isBlank(url) || !url.trim().toLowerCase(Locale.ROOT).startsWith("https://")) {
            return false;
        }
        String host = hostOf(url);
        return host != null && !host.equals("localhost") && !host.startsWith("127.") && !host.equals("0.0.0.0")
                && !host.equals("::1") && !host.endsWith(".local");
    }

    private static String hostOf(String url) {
        if (isBlank(url)) {
            return null;
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean looksLikePlaceholder(String value) {
        String v = value.trim().toUpperCase(Locale.ROOT);
        return v.contains("SEU_TOKEN") || v.contains("AQUI") || v.contains("CHANGE") || v.contains("EXEMPLO") || v.contains("XXXX");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
