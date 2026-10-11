package com.duoc.bancoxyz.clientes.notificacion;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class NotificacionRepository {

    private static final String COLUMNAS = "id, cliente_id, categoria, nivel, titulo, detalle, referencia, origen, fecha";

    private final JdbcClient jdbcClient;

    public NotificacionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Idempotente por (evento, cliente): un evento repetido no duplica la notificacion. */
    public void insertar(String eventId, Long clienteId, String categoria, String nivel, String titulo, String detalle,
            String referencia, String origen) {
        jdbcClient.sql("""
                INSERT INTO notificaciones_cliente (event_id, cliente_id, categoria, nivel, titulo, detalle, referencia, origen)
                VALUES (:eventId, :clienteId, :categoria, :nivel, :titulo, :detalle, :referencia, :origen)
                ON CONFLICT (event_id, cliente_id) DO NOTHING
                """)
                .param("eventId", UUID.fromString(eventId))
                .param("clienteId", clienteId)
                .param("categoria", categoria)
                .param("nivel", nivel)
                .param("titulo", titulo)
                .param("detalle", detalle)
                .param("referencia", referencia)
                .param("origen", origen)
                .update();
    }

    public List<Notificacion> listar(Long clienteId, String categoria, int limite) {
        return jdbcClient.sql("SELECT " + COLUMNAS + """
                 FROM notificaciones_cliente
                WHERE cliente_id = :clienteId AND (CAST(:categoria AS VARCHAR) IS NULL OR categoria = :categoria)
                ORDER BY id DESC LIMIT :limite""")
                .param("clienteId", clienteId)
                .param("categoria", categoria)
                .param("limite", limite)
                .query(Notificacion.class)
                .list();
    }

    public int alertasUltimos30Dias(Long clienteId) {
        Integer total = jdbcClient.sql("""
                SELECT COUNT(*) FROM notificaciones_cliente
                WHERE cliente_id = :clienteId AND categoria = 'ALERTA' AND fecha > now() - INTERVAL '30 days'
                """)
                .param("clienteId", clienteId)
                .query(Integer.class)
                .single();
        return total == null ? 0 : total;
    }
}
