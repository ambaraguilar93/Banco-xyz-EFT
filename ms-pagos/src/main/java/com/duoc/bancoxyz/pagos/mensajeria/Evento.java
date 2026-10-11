package com.duoc.bancoxyz.pagos.mensajeria;

/**
 * Datos comunes de todo evento publicado en Kafka. Cada microservicio tiene sus propias
 * clases de evento (no se comparte codigo entre servicios): el contrato es el JSON.
 */
public interface Evento {

    /** Identificador unico del evento: los consumidores lo usan para descartar duplicados. */
    String eventId();

    /** Nombre logico del evento (ej. PagoSolicitado). Tambien viaja en el header "tipo". */
    String tipo();

    String correlationId();
}
