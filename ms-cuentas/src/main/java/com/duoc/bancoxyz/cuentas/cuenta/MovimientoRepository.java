package com.duoc.bancoxyz.cuentas.cuenta;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MovimientoRepository {

    private final JdbcClient jdbcClient;

    public MovimientoRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insertar(String operacionId, Long cuentaId, String tipoMovimiento, String tipoOperacion,
            BigDecimal monto, BigDecimal saldoResultante, String descripcion) {
        jdbcClient.sql("""
                INSERT INTO movimientos_cuenta
                    (operacion_id, cuenta_id, tipo_movimiento, tipo_operacion, monto, saldo_resultante, descripcion)
                VALUES (:operacionId, :cuentaId, :tipoMovimiento, :tipoOperacion, :monto, :saldo, :descripcion)
                """)
                .param("operacionId", operacionId)
                .param("cuentaId", cuentaId)
                .param("tipoMovimiento", tipoMovimiento)
                .param("tipoOperacion", tipoOperacion)
                .param("monto", monto)
                .param("saldo", saldoResultante)
                .param("descripcion", descripcion)
                .update();
    }

    public List<Movimiento> listarPorCuenta(Long cuentaId, int limite) {
        return jdbcClient.sql("""
                SELECT id, operacion_id, cuenta_id, tipo_movimiento, tipo_operacion, monto, saldo_resultante,
                       descripcion, fecha
                FROM movimientos_cuenta WHERE cuenta_id = :cuentaId ORDER BY id DESC LIMIT :limite
                """)
                .param("cuentaId", cuentaId)
                .param("limite", limite)
                .query(Movimiento.class)
                .list();
    }
}
