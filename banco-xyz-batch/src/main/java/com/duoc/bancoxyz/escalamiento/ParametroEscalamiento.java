package com.duoc.bancoxyz.escalamiento;

/**
 * EscalamientoRunner corre el mismo step de
 * carga una vez por cada combinacion de esta lista, para poder comparar
 * el rendimiento entre ellas y elegir la configuracion optima con datos
 * reales.
 */
public record ParametroEscalamiento(String etiqueta, int hilos, int tamanoChunk) {

    @Override
    public String toString() {
        return etiqueta + " (hilos=" + hilos + ", chunk=" + tamanoChunk + ")";
    }
}
