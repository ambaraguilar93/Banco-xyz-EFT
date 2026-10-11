package com.duoc.bancoxyz.cuentas.cliente;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ClienteReplicaRepository {

    private final JdbcClient jdbcClient;

    public ClienteReplicaRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<ClienteReplica> buscar(Long clienteId) {
        return jdbcClient.sql("SELECT cliente_id, nombre, estado FROM clientes_replica WHERE cliente_id = :id")
                .param("id", clienteId)
                .query(ClienteReplica.class)
                .optional();
    }

    public void guardar(Long clienteId, String nombre, String estado) {
        jdbcClient.sql("""
                INSERT INTO clientes_replica (cliente_id, nombre, estado) VALUES (:id, :nombre, :estado)
                ON CONFLICT (cliente_id) DO UPDATE
                   SET nombre = COALESCE(EXCLUDED.nombre, clientes_replica.nombre), estado = EXCLUDED.estado,
                       actualizado_en = now()
                """)
                .param("id", clienteId)
                .param("nombre", nombre)
                .param("estado", estado)
                .update();
    }
}
