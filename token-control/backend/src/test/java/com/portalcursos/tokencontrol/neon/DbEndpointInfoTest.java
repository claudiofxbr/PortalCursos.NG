package com.portalcursos.tokencontrol.neon;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DbEndpointInfoTest {

    @Test
    void neonPooledExtraiEndpointERegiaoSemVazarCredenciais() {
        DbEndpointInfo i = DbEndpointInfo.parse(
                "jdbc:postgresql://ep-cool-sky-123456-pooler.sa-east-1.aws.neon.tech/neondb?user=u&password=SEGREDO&sslmode=require");
        assertThat(i.neon()).isTrue();
        assertThat(i.pooled()).isTrue();
        assertThat(i.endpointId()).isEqualTo("ep-cool-sky-123456");
        assertThat(i.region()).isEqualTo("sa-east-1");
        assertThat(i.toString()).doesNotContain("SEGREDO");
    }

    @Test
    void neonDiretoNaoEPooled() {
        DbEndpointInfo i = DbEndpointInfo.parse("jdbc:postgresql://ep-a-b-1.us-east-2.aws.neon.tech/neondb?sslmode=require");
        assertThat(i.neon()).isTrue();
        assertThat(i.pooled()).isFalse();
        assertThat(i.endpointId()).isEqualTo("ep-a-b-1");
    }

    @Test
    void hostComSegmentoExtraCdoisAindaAcharARegiao() {
        DbEndpointInfo i = DbEndpointInfo.parse(
                "jdbc:postgresql://ep-gentle-wild-abc123-pooler.c-2.sa-east-1.aws.neon.tech/neondb?sslmode=require");
        assertThat(i.pooled()).isTrue();
        assertThat(i.endpointId()).isEqualTo("ep-gentle-wild-abc123");
        assertThat(i.region()).isEqualTo("sa-east-1");
    }

    @Test
    void naoNeonOuInvalidoRetornaVazio() {
        assertThat(DbEndpointInfo.parse("jdbc:postgresql://localhost:5432/x").neon()).isFalse();
        assertThat(DbEndpointInfo.parse("jdbc:h2:mem:tc").neon()).isFalse();
        assertThat(DbEndpointInfo.parse(null).neon()).isFalse();
        assertThat(DbEndpointInfo.parse("jdbc:postgresql://exa mple/x").neon()).isFalse();
    }
}
