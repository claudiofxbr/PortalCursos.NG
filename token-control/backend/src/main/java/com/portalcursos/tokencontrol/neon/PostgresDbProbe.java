package com.portalcursos.tokencontrol.neon;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Consultas de catálogo do Postgres/Neon — somente leitura, sem dados de negócio. */
@Component
public class PostgresDbProbe implements DbProbe {

    private static final List<String> APP_TABLES = List.of("token_usage_entries", "token_plan_config");

    private static final long EXACT_COUNT_BELOW = 100_000;

    private final JdbcTemplate jdbc;

    private final String historyTable;

    @Autowired
    public PostgresDbProbe(JdbcTemplate jdbc,
            @Value("${spring.flyway.table:flyway_schema_history}") String historyTable) {
        // interpolado em SQL: só identificador simples (vem de configuração, nunca de requisição)
        if (!historyTable.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalArgumentException("spring.flyway.table inválido");
        }
        this.jdbc = jdbc;
        this.historyTable = historyTable;
    }

    @Override
    public DbStatus probe(DbEndpointInfo endpoint, NeonApiInfo api) {
        long t0 = System.nanoTime();
        jdbc.queryForObject("select 1", Integer.class);
        long latency = (System.nanoTime() - t0) / 1_000_000;

        String version = jdbc.queryForObject("select current_setting('server_version')", String.class);
        Long size = jdbc.queryForObject("select pg_database_size(current_database())", Long.class);
        DbStatus.Connections conns = jdbc.queryForObject("""
                select count(*), count(*) filter (where state = 'active'),
                       current_setting('max_connections')::bigint
                from pg_stat_activity where datname = current_database()""",
                (rs, i) -> new DbStatus.Connections(rs.getLong(1), rs.getLong(2), rs.getLong(3)));

        List<DbStatus.TableInfo> tables = jdbc.query("""
                select relname, n_live_tup, pg_total_relation_size(relid)
                from pg_stat_user_tables where relname = any (?) order by relname""",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("text", APP_TABLES.toArray())),
                (rs, i) -> new DbStatus.TableInfo(rs.getString(1), rs.getLong(2), rs.getLong(3)));
        // n_live_tup é estatística (defasada logo após inserts): em tabela pequena a contagem exata é barata
        tables = tables.stream().map(t -> t.rows() < EXACT_COUNT_BELOW && APP_TABLES.contains(t.name())
                ? new DbStatus.TableInfo(t.name(), exactCount(t.name()), t.sizeBytes()) : t).toList();

        return new DbStatus(true, latency, version, size == null ? 0 : size, conns, endpoint, migrations(), tables,
                api);
    }

    /** Nome vem da lista fixa APP_TABLES (nunca de entrada externa) — seguro contra injeção. */
    private long exactCount(String table) {
        Long n = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return n == null ? 0 : n;
    }

    private DbStatus.Migrations migrations() {
        try {
            Long failed = jdbc.queryForObject("select count(*) from " + historyTable + " where success = false",
                    Long.class);
            List<String> latest = jdbc.queryForList(
                    "select version from " + historyTable + " where success = true and version is not null "
                            + "order by installed_rank desc limit 1", String.class);
            long f = failed == null ? 0 : failed;
            return new DbStatus.Migrations(f > 0 ? "FAILED" : "OK", latest.isEmpty() ? null : latest.get(0), f);
        } catch (RuntimeException e) {
            return new DbStatus.Migrations("UNKNOWN", null, 0);
        }
    }
}
