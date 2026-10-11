package com.duoc.bancoxyz.cuentas.tarjeta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import com.duoc.bancoxyz.cuentas.cuenta.Cuenta;
import com.duoc.bancoxyz.cuentas.cuenta.CuentaRepository;
import com.duoc.bancoxyz.cuentas.mensajeria.Topicos;
import com.duoc.bancoxyz.cuentas.mensajeria.eventos.AlertaSeguridad;
import com.duoc.bancoxyz.cuentas.outbox.Outbox;

/**
 * EValidacion de tarjeta + PIN para el BFF del cajero automatico.
 *
 * - El PIN se compara con su hash BCrypt.
 * - Tras N intentos fallidos la tarjeta se BLOQUEA y se publica una
 * AlertaSeguridad ALTA.
 * - Tarjeta inexistente y PIN incorrecto responden el mismo mensaje.
 * El resultado se devuelve para que el contador de intentos se
 * guarde aunque la verificacion falle.
 */
@Service
public class TarjetaService {

    private static final Logger log = LoggerFactory.getLogger(TarjetaService.class);
    private static final String INCORRECTA = "Tarjeta o PIN incorrectos";

    private final TarjetaRepository tarjetas;
    private final CuentaRepository cuentas;
    private final Outbox outbox;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final int maximoIntentos;

    public TarjetaService(TarjetaRepository tarjetas, CuentaRepository cuentas, Outbox outbox,
            @Value("${cuentas.tarjetas.maximo-intentos:3}") int maximoIntentos) {
        this.tarjetas = tarjetas;
        this.cuentas = cuentas;
        this.outbox = outbox;
        this.maximoIntentos = maximoIntentos;
    }

    @Transactional
    @CircuitBreaker(name = "cuentasDb")
    public ResultadoVerificacion verificar(String numero, String pin) {
        String enmascarada = "****" + numero.substring(numero.length() - 4);
        TarjetaRepository.Tarjeta tarjeta = tarjetas.buscarParaActualizar(numero).orElse(null);
        if (tarjeta == null) {
            log.warn("[TARJETA] Intento con tarjeta inexistente {}", enmascarada);
            return ResultadoVerificacion.rechazo(INCORRECTA, null);
        }
        if ("BLOQUEADA".equals(tarjeta.estado())) {
            log.warn("[TARJETA] Intento con tarjeta BLOQUEADA {}", enmascarada);
            return ResultadoVerificacion.rechazo("Tarjeta bloqueada. Contacte a su banco", 0);
        }
        Cuenta cuenta = cuentas.buscar(tarjeta.cuentaId()).orElse(null);
        if (!encoder.matches(pin, tarjeta.pinHash())) {
            int intentos = tarjeta.intentosFallidos() + 1;
            boolean bloquear = intentos >= maximoIntentos;
            tarjetas.registrarIntento(numero, intentos, bloquear ? "BLOQUEADA" : "ACTIVA");
            if (bloquear) {
                Long clienteId = cuenta == null ? null : cuenta.clienteId();
                outbox.registrar(Topicos.ALERTAS_SEGURIDAD, String.valueOf(clienteId),
                        AlertaSeguridad.de("ALTA", "TARJETA_BLOQUEADA", "Tarjeta " + enmascarada + " bloqueada por "
                                + intentos + " intentos de PIN incorrectos en cajero automatico", clienteId,
                                tarjeta.cuentaId(), null));
                log.warn("[TARJETA] Tarjeta {} BLOQUEADA tras {} intentos fallidos", enmascarada, intentos);
                return ResultadoVerificacion.rechazo("PIN incorrecto. La tarjeta fue bloqueada", 0);
            }
            log.warn("[TARJETA] PIN incorrecto para {} (intento {} de {})", enmascarada, intentos, maximoIntentos);
            return ResultadoVerificacion.rechazo(INCORRECTA, maximoIntentos - intentos);
        }
        if (tarjeta.intentosFallidos() > 0) {
            tarjetas.registrarIntento(numero, 0, "ACTIVA");
        } else {
            tarjetas.registrarIntento(numero, 0, tarjeta.estado());
        }
        if (cuenta == null || !cuenta.activa()) {
            return ResultadoVerificacion.rechazo("La cuenta asociada a la tarjeta no esta activa", null);
        }
        log.info("[TARJETA] Tarjeta {} verificada (cuenta {}, cliente {})", enmascarada, cuenta.id(),
                cuenta.clienteId());
        return new ResultadoVerificacion(true, null, null, cuenta.id(), cuenta.clienteId(), cuenta.tipo());
    }
}
