package com.duoc.bancoxyz.escalamiento;

/**
 * ResultadoEscalamiento
 *
 * Guarda lo que paso al correr la combinacion de hilos y chunk.
 * (Cuanto demoro, cuantos registros se leyeron/escribieron/omitieron, y el
 * rendimiento.)
 */
public record ResultadoEscalamiento(
        ParametroEscalamiento parametro,
        long registrosLeidos,
        long registrosEscritos,
        long registrosOmitidos,
        long duracionMs,
        int hilosActivosDetectados,
        double throughputRegPorSeg) {

    public String comoFilaTabla() {
        return String.format(
                "%-28s | hilos=%-2d | chunk=%-4d | %8d ms | %9.1f reg/seg | hilos activos: %d | leidos=%d escritos=%d omitidos=%d",
                parametro.etiqueta(), parametro.hilos(), parametro.tamanoChunk(),
                duracionMs, throughputRegPorSeg, hilosActivosDetectados,
                registrosLeidos, registrosEscritos, registrosOmitidos);
    }
}
