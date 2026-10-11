package com.duoc.bancoxyz.bff.cajero.comun;

/** 409: la solicitud es valida pero viola una regla del negocio (ej. cerrar una cuenta con saldo). */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
