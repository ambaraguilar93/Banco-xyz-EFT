package com.duoc.bancoxyz.ejecucion;

import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * EFT - Registro de jobs.
 *
 * JobOperator.restart() busca el job por su nombre en un JobRegistry.
 * MapJobRegistry se llena solo con todos los beans de tipo Job.
 */
@Configuration
public class ReejecucionConfig {

    @Bean
    public MapJobRegistry jobRegistry() {
        return new MapJobRegistry();
    }
}
