package com.codesure.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String defaultUrl;

    @Value("${spring.datasource.username}")
    private String defaultUsername;

    @Value("${spring.datasource.password:}")
    private String defaultPassword;

    @Bean
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();

        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl != null && !databaseUrl.trim().isEmpty() && (System.getenv("SPRING_DATASOURCE_URL") == null)) {
            try {
                String cleanUrl = databaseUrl.trim();
                if (cleanUrl.startsWith("postgres://")) {
                    cleanUrl = "postgresql://" + cleanUrl.substring("postgres://".length());
                }

                URI uri = new URI(cleanUrl);
                String host = uri.getHost();
                int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                String path = uri.getPath();

                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;
                dataSource.setJdbcUrl(jdbcUrl);

                if (uri.getUserInfo() != null) {
                    String[] userInfo = uri.getUserInfo().split(":");
                    dataSource.setUsername(userInfo[0]);
                    if (userInfo.length > 1) {
                        dataSource.setPassword(userInfo[1]);
                    }
                }
                return dataSource;
            } catch (Exception ignored) {
            }
        }

        dataSource.setJdbcUrl(defaultUrl);
        dataSource.setUsername(defaultUsername);
        dataSource.setPassword(defaultPassword);
        return dataSource;
    }
}
