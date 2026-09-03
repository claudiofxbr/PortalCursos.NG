package com.portalcursos.ng02.config;

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    private static final String POSTGRES_PREFIX = "postgres://";
    private static final String JDBC_PREFIX = "jdbc:postgresql://";

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties properties) {
        String url = properties.getUrl();
        
        // Se a URL do Spring estiver vazia, tenta pegar da variável DATABASE_URL (padrão cloud/Render)
        if (url == null || url.isEmpty()) {
            url = System.getenv("DATABASE_URL");
        }

        // Inteligência de Auto-Correção:
        // Converte o formato Linux/Cloud (postgres://) para o formato JDBC Java (jdbc:postgresql://)
        if (url != null && url.startsWith(POSTGRES_PREFIX)) {
            url = JDBC_PREFIX + url.substring(POSTGRES_PREFIX.length());
            
            // Garante que o modo SSL esteja ativado para bancos cloud se não especificado
            if (!url.contains("sslmode=")) {
                url += (url.contains("?") ? "&" : "?") + "sslmode=require";
            }
        }
        
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .url(url)
                .build();
    }
}
