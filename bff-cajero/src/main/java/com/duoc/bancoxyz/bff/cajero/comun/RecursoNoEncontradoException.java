package com.duoc.bancoxyz.bff.cajero.comun;

/** 404: el recurso pedido no existe. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
