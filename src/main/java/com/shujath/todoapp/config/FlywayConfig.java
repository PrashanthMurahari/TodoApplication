package com.shujath.todoapp.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
    }

    // Run migrations before Hibernate validates the schema (ddl-auto: validate)
    @Configuration
    static class FlywayJpaDependencyConfiguration extends EntityManagerFactoryDependsOnPostProcessor {

        FlywayJpaDependencyConfiguration() {
            super(Flyway.class);
        }
    }
}
