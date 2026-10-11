package com.duoc.bancoxyz.cuentas.tarjeta;

/**
 * Resultado de validar tarjeta + PIN.
 */
public record ResultadoVerificacion(boolean valida, String motivo, Integer intentosRestantes, Long cuentaId,
        Long clienteId, String tipoCuenta) {

    static ResultadoVerificacion rechazo(String motivo, Integer intentosRestantes) {
        return new ResultadoVerificacion(false, motivo, intentosRestantes, null, null, null);
    }
}
