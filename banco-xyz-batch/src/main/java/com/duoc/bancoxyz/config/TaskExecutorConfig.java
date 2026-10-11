package com.duoc.bancoxyz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import jakarta.annotation.PreDestroy;

/**
 * Esta clase arma el pool de hilos que va a procesar los
 * datos en paralelo. Sin esto, un solo hilo tendria que procesar cada
 * registro uno por uno.
 * Con este pool, varios hilos pueden trabajar al mismo
 * tiempo, cada uno con un chunk, lo que hace que todo termine mas rapido.
 */
@Configuration
public class TaskExecutorConfig {

    // Nombre con el que las otras clases (FabricaStepsCarga, MetricasStepListener)
    // van a pedir este mismo pool de hilos para que todos usen el mismo.
    public static final String EXECUTOR = "batchTaskExecutor";

    private ThreadPoolTaskExecutor executor;

    // Crea el pool de hilos con la cantidad configurada en application.properties
    @Bean(EXECUTOR)
    public ThreadPoolTaskExecutor taskExecutor(
            @Value("${banco.paralelismo.hilos:3}") int hilos,
            @Value("${banco.paralelismo.cola:25}") int capacidadCola,
            @Value("${banco.paralelismo.prefijo:Batch-Thread-}") String prefijo) {

        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(hilos);
        executor.setMaxPoolSize(hilos);
        executor.setQueueCapacity(capacidadCola);
        executor.setThreadNamePrefix(prefijo);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();

        System.out.printf(
                "[POOL] TaskExecutor iniciado -> corePoolSize=%d | maxPoolSize=%d | queueCapacity=%d | prefijo='%s'%n",
                hilos, hilos, capacidadCola, prefijo);

        return executor;
    }

    /**
     * Se ejecuta automaticamente cuando la aplicacion se esta cerrando,
     * para apagar el pool de hilos de forma ordenada. Sin esto, los hilos
     * quedarian esperando trabajo para siempre y el programa nunca terminaria.
     */
    @PreDestroy
    public void cerrarPool() {

        if (executor != null) {
            System.out.println("[POOL] Cerrando TaskExecutor y liberando hilos.");
            executor.shutdown();
        }
    }

    /**
     * Arma un resumen de cuantos hilos activos y tareas en cola para
     * poder mostrarlo en los logs.
     */
    public static String resumen(ThreadPoolTaskExecutor executor) {

        return String.format(
                "hilos activos=%d | tamano pool=%d | tareas en cola=%d",
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getThreadPoolExecutor().getQueue().size());
    }
}