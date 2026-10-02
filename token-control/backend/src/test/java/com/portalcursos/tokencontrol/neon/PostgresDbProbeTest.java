package com.portalcursos.tokencontrol.neon;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Sonda contra Postgres REAL (H2 não tem pg_stat_activity/pg_database_size). Roda quando TC_TEST_PG_URL está
 * definida (CI com service postgres; local com qualquer Postgres). Aplica a migration V1 de verdade.
 */
@EnabledIfEnvironmentVariable(named = "TC_TEST_PG_URL", matches = ".+")
class PostgresDbProbeTest {

    @Test
    void sondaRetornaVersaoTamanhoConexoesTabelasEMigrationsContraPostgresReal() {
        String url = System.getenv("TC_TEST_PG_URL");
        String user = System.getenv().getOrDefault("TC_TEST_PG_USER", "postgres");
        String pass = System.getenv().getOrDefault("TC_TEST_PG_PASSWORD", "");
        Flyway.configure().dataSource(url, user, pass).cleanDisabled(false).load().migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, pass));
        DbStatus s = new PostgresDbProbe(jdbc).probe(DbEndpointInfo.parse(url), NeonApiInfo.disabled());

        assertThat(s.connected()).isTrue();
        assertThat(s.version()).matches("\\d+(\\.\\d+)?.*");
        assertThat(s.databaseSizeBytes()).isPositive();
        assertThat(s.connections().total()).isPositive();
        assertThat(s.connections().max()).isPositive();
        assertThat(s.migrations().status()).isEqualTo("OK");
        assertThat(s.migrations().latest()).isEqualTo("1");
        assertThat(s.tables()).extracting(DbStatus.TableInfo::name)
                .containsExactly("token_plan_config", "token_usage_entries");
        // contagem exata em tabela pequena: config tem a linha id=1 da migration, mesmo sem ANALYZE
        assertThat(s.tables().get(0).rows()).isEqualTo(1);
        assertThat(s.latencyMs()).isGreaterThanOrEqualTo(0);
    }
}
