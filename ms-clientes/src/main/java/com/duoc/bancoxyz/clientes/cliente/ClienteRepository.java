package com.duoc.bancoxyz.clientes.cliente;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ClienteRepository {

    private static final String COLUMNAS = "id, rut, nombre, email, telefono, direccion, estado, motivo_estado, creado_en, actualizado_en";

    private final JdbcClient jdbcClient;

    public ClienteRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Cliente> buscar(Long id) {
        return jdbcClient.sql("SELECT " + COLUMNAS + " FROM clientes WHERE id = :id")
                .param("id", id)
                .query(Cliente.class)
                .optional();
    }

    public boolean existeRut(String rut) {
        return jdbcClient.sql("SELECT COUNT(*) FROM clientes WHERE upper(rut) = upper(:rut)")
                .param("rut", rut)
                .query(Integer.class)
                .single() > 0;
    }

    public List<Cliente> listar(int limite) {
        return jdbcClient.sql("SELECT " + COLUMNAS + " FROM clientes ORDER BY id LIMIT :limite")
                .param("limite", limite)
                .query(Cliente.class)
                .list();
    }

    public Cliente insertar(ClienteRequest c) {
        return jdbcClient.sql("""
                INSERT INTO clientes (rut, nombre, email, telefono, direccion, estado)
                VALUES (upper(:rut), :nombre, lower(:email), :telefono, :direccion, 'ACTIVO')
                RETURNING """ + " " + COLUMNAS)
                .param("rut", c.rut())
                .param("nombre", c.nombre().trim())
                .param("email", c.email())
                .param("telefono", c.telefono())
                .param("direccion", c.direccion())
                .query(Cliente.class)
                .single();
    }

    public Optional<Cliente> actualizar(Long id, ActualizacionClienteRequest c) {
        return jdbcClient.sql("""
                UPDATE clientes SET nombre = :nombre, email = lower(:email), telefono = :telefono,
                       direccion = :direccion, actualizado_en = now()
                WHERE id = :id
                RETURNING """ + " " + COLUMNAS)
                .param("id", id)
                .param("nombre", c.nombre().trim())
                .param("email", c.email())
                .param("telefono", c.telefono())
                .param("direccion", c.direccion())
                .query(Cliente.class)
                .optional();
    }

    public Cliente cambiarEstado(Long id, String estado, String motivo) {
        return jdbcClient.sql("""
                UPDATE clientes SET estado = :estado, motivo_estado = :motivo, actualizado_en = now()
                WHERE id = :id
                RETURNING """ + " " + COLUMNAS)
                .param("id", id)
                .param("estado", estado)
                .param("motivo", motivo)
                .query(Cliente.class)
                .single();
    }
}
