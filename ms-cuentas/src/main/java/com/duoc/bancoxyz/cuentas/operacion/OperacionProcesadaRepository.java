package com.duoc.bancoxyz.cuentas.operacion;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.duoc.bancoxyz.cuentas.mensajeria.eventos.OperacionProcesada;

/**
 * Registro de cada operacion que ms-cuentas ya aplico o rechazo (PK operacion_id).
 * Garantiza que una operacion nunca se aplique dos veces, aunque la solicitud llegue repetida.
 */
@Repository
public class OperacionProcesadaRepository {

    private final JdbcClient jdbcClient;

    public OperacionProcesadaRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<String> payloadDe(String operacionId) {
        return jdbcClient.sql("SELECT resultado_json FROM operaciones_procesadas WHERE operacion_id = :id")
                .param("id", operacionId)
                .query(String.class)
                .optional();
    }

    public void insertar(OperacionProcesada resultado, String json) {
        jdbcClient.sql("""
                INSERT INTO operaciones_procesadas (operacion_id, tipo_operacion, resultado, motivo, instancia, resultado_json)
                VALUES (:id, :tipoOperacion, :resultado, :motivo, :instancia, :json)
                """)
                .param("id", resultado.operacionId())
                .param("tipoOperacion", resultado.tipoOperacion())
                .param("resultado", resultado.resultado())
                .param("motivo", resultado.motivo())
                .param("instancia", resultado.instancia())
                .param("json", json)
                .update();
    }
}
