package com.duoc.bancoxyz.clientes.mensajeria;

/**
 * Datos comunes de todo evento publicado en Kafka. Cada microservicio tiene sus
 * propias
 * clases de evento.
 */
public interface Evento {

    /**
     * Identificador unico del evento: los consumidores lo usan para descartar
     * duplicados.
     */
    String eventId();

    /**
     * Nombre logico del evento (ej. PagoSolicitado). Tambien viaja en el header
     * "tipo".
     */
    String tipo();

    String correlationId();
}
