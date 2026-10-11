package com.duoc.bancoxyz.bff.web.comun;

/** 404: el recurso pedido no existe. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
