package com.duoc.bancoxyz.bff.web.backend;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import com.duoc.bancoxyz.bff.web.backend.Modelos.Cliente;
import com.duoc.bancoxyz.bff.web.backend.Modelos.Notificacion;

/** ms-clientes (balanceado por Eureka). Circuit Breaker "msClientes". */
@Component
public class ClientesApi {

    private final RestClient restClient;

    public ClientesApi(@LoadBalanced RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://ms-clientes").build();
    }

    @CircuitBreaker(name = "msClientes")
    public Cliente cliente(String token, Long id) {
        return restClient.get().uri("/api/clientes/{id}", id).headers(h -> h.setBearerAuth(token))
                .retrieve().body(Cliente.class);
    }

    @CircuitBreaker(name = "msClientes")
    public Cliente actualizar(String token, Long id, Map<String, Object> datos) {
        return restClient.put().uri("/api/clientes/{id}", id).headers(h -> h.setBearerAuth(token)).body(datos)
                .retrieve().body(Cliente.class);
    }

    @CircuitBreaker(name = "msClientes")
    public List<Notificacion> notificaciones(String token, Long id, String categoria, int limite) {
        Notificacion[] lista = restClient.get()
                .uri(u -> {
                    u.path("/api/clientes/{id}/notificaciones").queryParam("limite", limite);
                    if (categoria != null) {
                        u.queryParam("categoria", categoria);
                    }
                    return u.build(id);
                })
                .headers(h -> h.setBearerAuth(token)).retrieve().body(Notificacion[].class);
        return lista == null ? List.of() : Arrays.asList(lista);
    }
}
