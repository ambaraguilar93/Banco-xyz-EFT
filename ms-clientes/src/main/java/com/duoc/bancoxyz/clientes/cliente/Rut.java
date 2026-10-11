package com.duoc.bancoxyz.clientes.cliente;

/**
 * Validacion del RUT chileno (formato 12345678-9 y digito verificador modulo
 * 11).
 */
public final class Rut {

    private Rut() {
    }

    public static boolean esValido(String rut) {
        if (rut == null || !rut.matches("^\\d{7,8}-[\\dkK]$")) {
            return false;
        }
        String[] partes = rut.split("-");
        int suma = 0;
        int factor = 2;
        for (int i = partes[0].length() - 1; i >= 0; i--) {
            suma += Character.getNumericValue(partes[0].charAt(i)) * factor;
            factor = factor == 7 ? 2 : factor + 1;
        }
        int resto = 11 - (suma % 11);
        String esperado = resto == 11 ? "0" : resto == 10 ? "K" : String.valueOf(resto);
        return esperado.equalsIgnoreCase(partes[1]);
    }
}
