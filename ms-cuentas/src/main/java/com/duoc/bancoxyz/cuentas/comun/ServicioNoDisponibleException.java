package com.duoc.bancoxyz.cuentas.comun;

/** 503: una dependencia (BD, otro microservicio, Kafka) no responde y no hay alternativa. */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
