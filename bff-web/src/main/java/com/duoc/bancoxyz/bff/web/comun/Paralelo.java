package com.duoc.bancoxyz.bff.web.comun;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.slf4j.MDC;

/**
 * Llamadas en paralelo a varios microservicios (hilos virtuales de Java 21). Copia el MDC
 * para que el X-Correlation-Id siga apareciendo en los logs y viaje a los servicios.
 */
public final class Paralelo {

    private static final ExecutorService HILOS = Executors.newVirtualThreadPerTaskExecutor();

    private Paralelo() {
    }

    public static <T> CompletableFuture<T> ejecutar(Supplier<T> tarea) {
        Map<String, String> contexto = MDC.getCopyOfContextMap();
        return CompletableFuture.supplyAsync(() -> {
            if (contexto != null) {
                MDC.setContextMap(contexto);
            }
            try {
                return tarea.get();
            }
            finally {
                MDC.clear();
            }
        }, HILOS);
    }
}
