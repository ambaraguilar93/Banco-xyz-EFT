package com.duoc.bancoxyz.clientes.mensajeria;

/**
 * El mensaje no se puede procesar por su contenido (JSON mal formado, campos faltantes).
 * Reintentarlo no lo arregla: el manejador de errores lo envia directo al topico -dlt.
 */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
