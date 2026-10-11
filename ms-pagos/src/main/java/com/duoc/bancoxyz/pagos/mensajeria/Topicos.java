package com.duoc.bancoxyz.pagos.mensajeria;

import java.util.List;

/**
 * Catalogo de topicos Kafka del Banco XYZ (el mismo en los 3 microservicios).
 * Cada topico tiene su Dead Letter Topic.
 */
public final class Topicos {

    /**
     * ms-clientes -> ms-cuentas: ClienteRegistrado, ClienteActualizado,
     * ClienteDesactivado, ClienteReactivado.
     */
    public static final String CLIENTES_EVENTOS = "banco.clientes.eventos";

    /**
     * ms-cuentas -> ms-clientes: CuentaAbierta, CuentaBloqueada,
     * CuentaDesbloqueada, CuentaCerrada.
     */
    public static final String CUENTAS_EVENTOS = "banco.cuentas.eventos";

    /**
     * ms-pagos -> ms-cuentas: OperacionSolicitada (pago, transferencia o deposito).
     */
    public static final String OPERACIONES_SOLICITADAS = "banco.operaciones.solicitadas";

    /** ms-cuentas -> ms-pagos: OperacionProcesada (APLICADA o RECHAZADA). */
    public static final String OPERACIONES_RESULTADOS = "banco.operaciones.resultados";

    /** ms-pagos -> ms-clientes: TransaccionCompletada. */
    public static final String TRANSACCIONES_COMPLETADAS = "banco.transacciones.completadas";

    /** ms-pagos y ms-cuentas -> ms-clientes: AlertaSeguridad. */
    public static final String ALERTAS_SEGURIDAD = "banco.alertas.seguridad";

    public static final List<String> TODOS = List.of(CLIENTES_EVENTOS, CUENTAS_EVENTOS, OPERACIONES_SOLICITADAS,
            OPERACIONES_RESULTADOS, TRANSACCIONES_COMPLETADAS, ALERTAS_SEGURIDAD);

    private Topicos() {
    }
}
