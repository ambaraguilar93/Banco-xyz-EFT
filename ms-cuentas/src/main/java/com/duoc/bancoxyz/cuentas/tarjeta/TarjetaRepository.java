package com.duoc.bancoxyz.cuentas.tarjeta;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TarjetaRepository {

    /** Tarjeta de debito asociada a una cuenta. */
    public record Tarjeta(String numero, Long cuentaId, String pinHash, String estado, int intentosFallidos) {
    }

    private final JdbcClient jdbcClient;

    public TarjetaRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Bloquea la fila: dos intentos simultaneos no pueden saltarse el contador. */
    public Optional<Tarjeta> buscarParaActualizar(String numero) {
        return jdbcClient.sql("""
                SELECT numero, cuenta_id, pin_hash, estado, intentos_fallidos
                FROM tarjetas WHERE numero = :numero FOR UPDATE
                """)
                .param("numero", numero)
                .query(Tarjeta.class)
                .optional();
    }

    public void registrarIntento(String numero, int intentosFallidos, String estado) {
        jdbcClient.sql("""
                UPDATE tarjetas SET intentos_fallidos = :intentos, estado = :estado,
                       bloqueada_en = CASE WHEN :estado = 'BLOQUEADA' THEN now() ELSE bloqueada_en END,
                       ultimo_uso = now()
                WHERE numero = :numero
                """)
                .param("intentos", intentosFallidos)
                .param("estado", estado)
                .param("numero", numero)
                .update();
    }
}
