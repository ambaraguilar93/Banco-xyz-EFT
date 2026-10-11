package com.duoc.bancoxyz.bff.cajero.cajero;

/**
 * Reglas del canal CAJERO AUTOMATICO, en un solo lugar. Son constantes porque
 * las anotaciones de Bean Validation solo aceptan valores conocidos en tiempo
 * de compilacion.
 *
 * | Monto minimo | $5.000 por operacion
 * | Monto maximo | $200.000 por operacion
 * | Multiplo | $5.000
 * | Decimales | ninguno
 */
public final class ReglasCajero {

    public static final String MONTO_MINIMO = "5000";
    public static final String MONTO_MAXIMO = "200000";
    public static final long MULTIPLO_MONTO = 5000;
    public static final int DIGITOS_ENTEROS_MONTO = 6;

    public static final String PATRON_IDEMPOTENCY_KEY = "^[A-Za-z0-9_-]{8,100}$";
    public static final String MENSAJE_IDEMPOTENCY_KEY = "debe tener entre 8 y 100 caracteres y solo letras, numeros, guion o guion bajo";

    private ReglasCajero() {
    }
}
