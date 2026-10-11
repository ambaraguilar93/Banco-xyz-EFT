package com.duoc.bancoxyz.bff.web.comun;

/** 403: el recurso existe pero no pertenece al cliente autenticado (proteccion BOLA/IDOR). */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
