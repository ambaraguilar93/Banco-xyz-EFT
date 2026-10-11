package com.duoc.bancoxyz.bff.cajero.comun;

import java.util.UUID;

import org.slf4j.MDC;

/**
 * ID de correlacion: identifica una operacion de punta a punta. Viaja en el header HTTP
 * X-Correlation-Id y en el header correlationId de cada mensaje Kafka, y aparece en todas
 * las lineas de log de todos los microservicios (monitoreo y trazabilidad).
 */
public final class CorrelationId {

    public static final String HEADER_HTTP = "X-Correlation-Id";
    public static final String HEADER_KAFKA = "correlationId";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    /** El ID del hilo actual, o uno nuevo si no hay. */
    public static String actual() {
        String id = MDC.get(MDC_KEY);
        return id != null ? id : UUID.randomUUID().toString();
    }

    public static void establecer(String id) {
        MDC.put(MDC_KEY, id != null && !id.isBlank() ? id : UUID.randomUUID().toString());
    }

    public static void limpiar() {
        MDC.remove(MDC_KEY);
    }
}
