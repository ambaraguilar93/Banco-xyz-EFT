package com.duoc.bancoxyz.bff.movil.comun;

import java.time.OffsetDateTime;
import java.util.List;

/** Formato unico de error de la API (igual en los 3 microservicios). */
public record ErrorResponse(int codigo, String estado, String mensaje, List<String> detalles, String ruta,
        OffsetDateTime fechaHora) {
}
