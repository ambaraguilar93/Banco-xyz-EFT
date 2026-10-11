package com.duoc.bancoxyz.cuentas.tarjeta;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verificacion de tarjeta + PIN. Solo la puede llamar el BFF del cajero con su
 * token de servicio. Responde siempre 200 con el veredicto:
 * un PIN incorrecto no es un error del servicio, es un resultado de la
 * verificacion.
 */
@RestController
@RequestMapping("/api/tarjetas")
public class TarjetaController {

    private final TarjetaService servicio;

    public TarjetaController(TarjetaService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/verificacion")
    public ResultadoVerificacion verificar(@Valid @RequestBody VerificacionTarjetaRequest solicitud) {
        return servicio.verificar(solicitud.numeroTarjeta(), solicitud.pin());
    }
}
