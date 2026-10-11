package com.duoc.bancoxyz.cuentas.cuenta;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CuentaRepository {

    private static final String COLUMNAS = "id, cliente_id, tipo, saldo, estado, motivo_estado, abierta_en, cerrada_en, actualizada_en";

    private final JdbcClient jdbcClient;

    public CuentaRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Cuenta> buscar(Long id) {
        return jdbcClient.sql("SELECT " + COLUMNAS + " FROM cuentas WHERE id = :id")
                .param("id", id)
                .query(Cuenta.class)
                .optional();
    }

    /** Bloquea la fila hasta el fin de la transaccion: dos operaciones sobre la misma cuenta se serializan. */
    public Optional<Cuenta> buscarParaActualizar(Long id) {
        return jdbcClient.sql("SELECT " + COLUMNAS + " FROM cuentas WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(Cuenta.class)
                .optional();
    }

    public List<Cuenta> listarPorCliente(Long clienteId) {
        return jdbcClient.sql("SELECT " + COLUMNAS + " FROM cuentas WHERE cliente_id = :clienteId ORDER BY id")
                .param("clienteId", clienteId)
                .query(Cuenta.class)
                .list();
    }

    public Cuenta insertar(Long clienteId, String tipo, BigDecimal saldo) {
        return jdbcClient.sql("""
                INSERT INTO cuentas (id, cliente_id, tipo, saldo, estado)
                VALUES (nextval('cuentas_id_seq'), :clienteId, :tipo, :saldo, 'ACTIVA')
                RETURNING """ + " " + COLUMNAS)
                .param("clienteId", clienteId)
                .param("tipo", tipo)
                .param("saldo", saldo)
                .query(Cuenta.class)
                .single();
    }

    public Cuenta cambiarEstado(Long id, String estado, String motivo) {
        return jdbcClient.sql("""
                UPDATE cuentas SET estado = :estado, motivo_estado = :motivo, actualizada_en = now(),
                       cerrada_en = CASE WHEN :estado = 'CERRADA' THEN now() ELSE cerrada_en END
                WHERE id = :id
                RETURNING """ + " " + COLUMNAS)
                .param("id", id)
                .param("estado", estado)
                .param("motivo", motivo)
                .query(Cuenta.class)
                .single();
    }

    /**
     * Debito atomico: descuenta solo si alcanza el saldo, en una sola sentencia. Aunque
     * varias instancias procesen operaciones de la misma cuenta, el saldo nunca queda negativo.
     */
    public Optional<BigDecimal> debitarSiAlcanza(Long id, BigDecimal monto) {
        return jdbcClient.sql("""
                UPDATE cuentas SET saldo = saldo - :monto, actualizada_en = now()
                WHERE id = :id AND saldo >= :monto
                RETURNING saldo
                """)
                .param("id", id)
                .param("monto", monto)
                .query(BigDecimal.class)
                .optional();
    }

    public BigDecimal acreditar(Long id, BigDecimal monto) {
        return jdbcClient.sql("""
                UPDATE cuentas SET saldo = saldo + :monto, actualizada_en = now()
                WHERE id = :id
                RETURNING saldo
                """)
                .param("id", id)
                .param("monto", monto)
                .query(BigDecimal.class)
                .single();
    }

    /** Bloquea todas las cuentas activas de un cliente (cuando ms-clientes lo desactiva). */
    public List<Cuenta> bloquearActivasDeCliente(Long clienteId, String motivo) {
        return jdbcClient.sql("""
                UPDATE cuentas SET estado = 'BLOQUEADA', motivo_estado = :motivo, actualizada_en = now()
                WHERE cliente_id = :clienteId AND estado = 'ACTIVA'
                RETURNING """ + " " + COLUMNAS)
                .param("clienteId", clienteId)
                .param("motivo", motivo)
                .query(Cuenta.class)
                .list();
    }
}
