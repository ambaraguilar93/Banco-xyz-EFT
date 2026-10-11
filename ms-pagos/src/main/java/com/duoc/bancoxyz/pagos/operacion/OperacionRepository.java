package com.duoc.bancoxyz.pagos.operacion;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OperacionRepository {

    private static final String COLUMNAS = """
            id, tipo, cuenta_origen_id, cuenta_destino_id, monto, descripcion, estado, motivo, validacion,
            cliente_origen_id, cliente_destino_id, idempotency_key, reenvios, correlation_id, creada_en, actualizada_en""";

    private final JdbcClient jdbcClient;

    public OperacionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Operacion> buscar(String id) {
        return jdbcClient.sql("SELECT " + COLUMNAS + " FROM operaciones WHERE id = :id")
                .param("id", id)
                .query(Operacion.class)
                .optional();
    }

    public Optional<Operacion> buscarPorIdempotencyKey(String clave) {
        return jdbcClient.sql("SELECT " + COLUMNAS + " FROM operaciones WHERE idempotency_key = :clave")
                .param("clave", clave)
                .query(Operacion.class)
                .optional();
    }

    public List<Operacion> listarPorCuenta(Long cuentaId, int limite) {
        return jdbcClient.sql("SELECT " + COLUMNAS + """
                 FROM operaciones WHERE cuenta_origen_id = :cuenta OR cuenta_destino_id = :cuenta
                ORDER BY creada_en DESC LIMIT :limite""")
                .param("cuenta", cuentaId)
                .param("limite", limite)
                .query(Operacion.class)
                .list();
    }

    public void insertar(Operacion op) {
        jdbcClient.sql("""
                INSERT INTO operaciones (id, tipo, cuenta_origen_id, cuenta_destino_id, monto, descripcion, estado,
                    validacion, cliente_origen_id, cliente_destino_id, idempotency_key, correlation_id)
                VALUES (:id, :tipo, :origen, :destino, :monto, :descripcion, 'PENDIENTE', :validacion,
                    :clienteOrigen, :clienteDestino, :clave, :correlationId)
                """)
                .param("id", op.id())
                .param("tipo", op.tipo())
                .param("origen", op.cuentaOrigenId())
                .param("destino", op.cuentaDestinoId())
                .param("monto", op.monto())
                .param("descripcion", op.descripcion())
                .param("validacion", op.validacion())
                .param("clienteOrigen", op.clienteOrigenId())
                .param("clienteDestino", op.clienteDestinoId())
                .param("clave", op.idempotencyKey())
                .param("correlationId", op.correlationId())
                .update();
    }

    /**
     * Cierra la operacion SOLO si sigue PENDIENTE (transicion condicional): si el resultado
     * llega dos veces, la segunda no cambia nada. Devuelve la operacion actualizada.
     */
    public Optional<Operacion> cerrar(String id, String estado, String motivo, Long clienteOrigen,
            Long clienteDestino) {
        return jdbcClient.sql("""
                UPDATE operaciones SET estado = :estado, motivo = :motivo, actualizada_en = now(),
                       cliente_origen_id = COALESCE(:clienteOrigen, cliente_origen_id),
                       cliente_destino_id = COALESCE(:clienteDestino, cliente_destino_id)
                WHERE id = :id AND estado = 'PENDIENTE'
                RETURNING """ + " " + COLUMNAS)
                .param("id", id)
                .param("estado", estado)
                .param("motivo", motivo)
                .param("clienteOrigen", clienteOrigen)
                .param("clienteDestino", clienteDestino)
                .query(Operacion.class)
                .optional();
    }

    /** Rechazos de una cuenta en los ultimos minutos (para detectar intentos repetidos). */
    public int rechazosRecientes(Long cuentaOrigenId, int minutos) {
        Integer total = jdbcClient.sql("""
                SELECT COUNT(*) FROM operaciones
                WHERE cuenta_origen_id = :cuenta AND estado = 'RECHAZADA'
                  AND actualizada_en > now() - make_interval(mins => :minutos)
                """)
                .param("cuenta", cuentaOrigenId)
                .param("minutos", minutos)
                .query(Integer.class)
                .single();
        return total == null ? 0 : total;
    }

    /** Pendientes sin respuesta hace mas de N segundos (las toma una sola instancia: SKIP LOCKED). */
    public List<Operacion> pendientesSinRespuesta(int segundos, int maxReenvios) {
        return jdbcClient.sql("SELECT " + COLUMNAS + """
                 FROM operaciones
                WHERE estado = 'PENDIENTE' AND reenvios < :max
                  AND actualizada_en < now() - make_interval(secs => :segundos)
                ORDER BY creada_en LIMIT 20 FOR UPDATE SKIP LOCKED""")
                .param("segundos", segundos)
                .param("max", maxReenvios)
                .query(Operacion.class)
                .list();
    }

    public void marcarReenvio(String id) {
        jdbcClient.sql("UPDATE operaciones SET reenvios = reenvios + 1, actualizada_en = now() WHERE id = :id")
                .param("id", id)
                .update();
    }

}
