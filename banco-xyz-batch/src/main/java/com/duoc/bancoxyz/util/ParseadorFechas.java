package com.duoc.bancoxyz.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/**
 * Esta herramienta convierte un texto de fecha en una fecha de verdad que Java
 * pueda entender. Aca se prueban varios formatos hasta que alguno funcione.
 * 
 * Si ninguno funciona devuelve null en vez de reventar el programa.
 * Asi el processor puede decidir si rechazar.
 * 
 */
public final class ParseadorFechas {

    private static final DateTimeFormatter[] FORMATOS = {
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu/MM/dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
    };

    private ParseadorFechas() {
    }

    public static LocalDate parsear(String valor) {

        if (valor == null || valor.isBlank()) {
            return null;
        }

        String limpio = valor.trim();

        for (DateTimeFormatter formato : FORMATOS) {
            try {
                return LocalDate.parse(limpio, formato);
            } catch (Exception e) {

            }
        }

        return null;
    }
}
