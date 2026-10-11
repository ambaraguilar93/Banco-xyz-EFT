package com.duoc.bancoxyz.bff.cajero.cajero;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.duoc.bancoxyz.bff.cajero.backend.CuentasApi;
import com.duoc.bancoxyz.bff.cajero.backend.Modelos.Cuenta;
import com.duoc.bancoxyz.bff.cajero.backend.Modelos.Operacion;
import com.duoc.bancoxyz.bff.cajero.backend.PagosApi;
import com.duoc.bancoxyz.bff.cajero.backend.TarjetasApi;
import com.duoc.bancoxyz.bff.cajero.comun.AccesoDenegadoException;
import com.duoc.bancoxyz.bff.cajero.comun.ReglaNegocioException;
import com.duoc.bancoxyz.bff.cajero.delegacion.DelegacionTokens;
import com.duoc.bancoxyz.bff.cajero.delegacion.TokenDelegado;
import com.duoc.bancoxyz.bff.cajero.security.UsuarioCanal;

/**
 * Logica del canal cajero.
 *
 * El backend procesa los movimientos de forma asincrona (saga por Kafka), pero
 * un cajero necesita saber si entrega el dinero. Por eso el BFF espera el
 * resultado unos segundos: APROBADO entrega, RECHAZADO no, y si no hay
 * respuesta a tiempo responde EN_PROCESO y NO entrega dinero.
 */
@Service
public class CajeroService {

    private static final Logger log = LoggerFactory.getLogger(CajeroService.class);

    private final DelegacionTokens delegacion;
    private final TarjetasApi tarjetas;
    private final CuentasApi cuentas;
    private final PagosApi pagos;
    private final Duration esperaMaxima;

    public CajeroService(DelegacionTokens delegacion, TarjetasApi tarjetas, CuentasApi cuentas, PagosApi pagos,
            @Value("${bff.cajero.espera-resultado-ms:6000}") long esperaMaximaMs) {
        this.delegacion = delegacion;
        this.tarjetas = tarjetas;
        this.cuentas = cuentas;
        this.pagos = pagos;
        this.esperaMaxima = Duration.ofMillis(esperaMaximaMs);
    }

    /** Tarjeta + PIN -> sesion de 2 minutos (o el motivo del rechazo). */
    public Object iniciarSesion(String numeroTarjeta, String pin, String terminal) {
        String tokenServicio = delegacion.deServicio().valor();
        TarjetasApi.Verificacion verificacion = tarjetas.verificar(tokenServicio, numeroTarjeta, pin);
        if (verificacion == null || !Boolean.TRUE.equals(verificacion.valida())) {
            String motivo = verificacion == null ? "No fue posible validar la tarjeta" : verificacion.motivo();
            log.warn("[CAJERO] Sesion rechazada en {}: {}", terminal, motivo);
            return new VistasCajero.SesionRechazada(motivo,
                    verificacion == null ? null : verificacion.intentosRestantes());
        }
        TokenDelegado sesion = delegacion.aNombreDe(verificacion.clienteId(), verificacion.cuentaId(), terminal);
        long segundos = Math.max(0, Duration.between(Instant.now(), sesion.expira()).toSeconds());
        log.info("[CAJERO] Sesion iniciada en {} para la cuenta {}", terminal, enmascarar(verificacion.cuentaId()));
        return new VistasCajero.Sesion(sesion.valor(), segundos, enmascarar(verificacion.cuentaId()),
                verificacion.tipoCuenta());
    }

    public VistasCajero.Saldo saldo(UsuarioCanal sesion) {
        Cuenta cuenta = cuentaDeLaSesion(sesion);
        return new VistasCajero.Saldo(enmascarar(cuenta.id()), cuenta.saldo(), OffsetDateTime.now());
    }

    public VistasCajero.ResultadoRetiro retirar(UsuarioCanal sesion, BigDecimal monto, String idempotencyKey,
            String terminal) {
        Long cuentaId = cuentaId(sesion);
        Map<String, Object> solicitud = new LinkedHashMap<>();
        solicitud.put("cuentaOrigenId", cuentaId); // siempre la cuenta de la tarjeta, nunca una del request
        solicitud.put("monto", monto);
        solicitud.put("descripcion", "Retiro cajero " + terminal);
        Operacion operacion = pagos.operar(sesion.token(), "retiros", idempotencyKey, solicitud);
        // Idempotency-Key repetido: ms-pagos devuelve la operacion ya registrada. Solo
        // se acepta si es ESTE mismo retiro (misma cuenta y monto).
        if (!"RETIRO".equals(operacion.tipo()) || !cuentaId.equals(operacion.cuentaOrigenId())
                || operacion.monto() == null || operacion.monto().compareTo(monto) != 0) {
            throw new ReglaNegocioException("El identificador de la transaccion ya fue usado en otra operacion");
        }

        Operacion actual = esperarResultado(sesion.token(), operacion);
        OffsetDateTime ahora = OffsetDateTime.now();
        return switch (actual.estado()) {
            case "COMPLETADA" -> {
                BigDecimal saldo = saldoSiEsPosible(sesion);
                log.info("[CAJERO] Retiro APROBADO de ${} en {} (operacion {})", monto, terminal, actual.id());
                yield new VistasCajero.ResultadoRetiro("APROBADO", actual.monto(), saldo, actual.id(),
                        "Retire su dinero y su tarjeta", terminal, ahora);
            }
            case "RECHAZADA" -> {
                log.info("[CAJERO] Retiro RECHAZADO en {}: {}", terminal, actual.motivo());
                yield new VistasCajero.ResultadoRetiro("RECHAZADO", monto, null, actual.id(),
                        mensajeRechazo(actual.motivo()), terminal, ahora);
            }
            default -> {
                log.warn("[CAJERO] Retiro {} sin respuesta en {} ms: NO se entrega dinero", actual.id(),
                        esperaMaxima.toMillis());
                yield new VistasCajero.ResultadoRetiro("EN_PROCESO", monto, null, actual.id(),
                        "No se entrego dinero. La operacion esta en proceso: consulte su saldo en unos minutos",
                        terminal, ahora);
            }
        };
    }

    private Operacion esperarResultado(String token, Operacion operacion) {
        Instant limite = Instant.now().plus(esperaMaxima);
        Operacion actual = operacion;
        while ("PENDIENTE".equals(actual.estado()) && Instant.now().isBefore(limite)) {
            try {
                Thread.sleep(300);
                actual = pagos.operacion(token, operacion.id());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                log.warn("[CAJERO] No se pudo consultar la operacion {}: {}", operacion.id(), e.toString());
            }
        }
        return actual;
    }

    private BigDecimal saldoSiEsPosible(UsuarioCanal sesion) {
        try {
            return cuentaDeLaSesion(sesion).saldo();
        } catch (RuntimeException e) {
            return null; // el comprobante sale igual, sin saldo
        }
    }

    private Cuenta cuentaDeLaSesion(UsuarioCanal sesion) {
        Cuenta cuenta = cuentas.cuenta(sesion.token(), cuentaId(sesion));
        if (cuenta == null || !cuenta.clienteId().equals(sesion.clienteId())) {
            throw new AccesoDenegadoException("La cuenta de la sesion no corresponde al cliente");
        }
        return cuenta;
    }

    private static Long cuentaId(UsuarioCanal sesion) {
        Object cuenta = sesion.jwt().getClaim("cuentaId");
        return ((Number) cuenta).longValue();
    }

    /**
     * El cajero no muestra montos internos ni el saldo disponible en un rechazo.
     */
    private static String mensajeRechazo(String motivo) {
        if (motivo == null) {
            return "Operacion rechazada";
        }
        if (motivo.startsWith("Saldo insuficiente")) {
            return "Saldo insuficiente";
        }
        return motivo;
    }

    static String enmascarar(Long id) {
        String numero = String.valueOf(id);
        return "****" + numero.substring(Math.max(0, numero.length() - 4));
    }
}
