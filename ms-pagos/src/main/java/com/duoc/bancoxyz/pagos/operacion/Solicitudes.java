package com.duoc.bancoxyz.pagos.operacion;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Cuerpos de las solicitudes. Montos en pesos, sin decimales, entre $1 y
 * $50.000.000.
 */
public final class Solicitudes {

        private Solicitudes() {
        }

        /** Pago de un servicio o comercio desde una cuenta del cliente. */
        public record PagoRequest(
                        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Long cuentaOrigenId,
                        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") @DecimalMax(value = "50000000", message = "no puede superar $50.000.000") @Digits(integer = 12, fraction = 0, message = "debe ser un monto en pesos sin decimales") BigDecimal monto,
                        @NotBlank(message = "indique a quien se paga (ej. Luz, Agua, comercio)") @Size(max = 200, message = "maximo 200 caracteres") String descripcion) {
        }

        /**
         * Retiro de efectivo (lo usa el BFF del cajero). Las reglas propias del cajero
         * (multiplos de $5.000, maximo por operacion) las aplica el BFF: aca solo las
         * generales.
         */
        public record RetiroRequest(
                        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Long cuentaOrigenId,
                        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") @DecimalMax(value = "50000000", message = "no puede superar $50.000.000") @Digits(integer = 12, fraction = 0, message = "debe ser un monto en pesos sin decimales") BigDecimal monto,
                        @Size(max = 200, message = "maximo 200 caracteres") String descripcion) {
        }

        /** Transferencia entre dos cuentas del banco. */
        public record TransferenciaRequest(
                        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Long cuentaOrigenId,
                        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Long cuentaDestinoId,
                        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") @DecimalMax(value = "50000000", message = "no puede superar $50.000.000") @Digits(integer = 12, fraction = 0, message = "debe ser un monto en pesos sin decimales") BigDecimal monto,
                        @Size(max = 200, message = "maximo 200 caracteres") String descripcion) {

                @AssertTrue(message = "la cuenta de origen y la de destino deben ser distintas")
                public boolean isCuentasDistintas() {
                        return cuentaOrigenId == null || !cuentaOrigenId.equals(cuentaDestinoId);
                }
        }

        /** Deposito en una cuenta (ej. efectivo en caja o cajero). */
        public record DepositoRequest(
                        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Long cuentaDestinoId,
                        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") @DecimalMax(value = "50000000", message = "no puede superar $50.000.000") @Digits(integer = 12, fraction = 0, message = "debe ser un monto en pesos sin decimales") BigDecimal monto,
                        @Size(max = 200, message = "maximo 200 caracteres") String descripcion) {
        }
}
