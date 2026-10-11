package com.duoc.bancoxyz.exception;

/**
 * EFT - FalloNoRecuperableException
 *
 * Fallo que no se arregla volviendo a ejecutar el job (por ejemplo, los
 * conteos de integridad no cuadran). EjecutorJobs NO reejecuta un job que
 * termino con esta excepcion: necesita que alguien lo revise.
 */
public class FalloNoRecuperableException extends RuntimeException {

    public FalloNoRecuperableException(String mensaje) {
        super(mensaje);
    }
}
