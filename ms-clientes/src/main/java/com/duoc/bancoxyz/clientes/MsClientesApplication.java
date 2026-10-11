package com.duoc.bancoxyz.clientes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Consume los eventos de cuentas, transacciones y alertas de seguridad para
 * armar el historial y las notificaciones de cada cliente.
 */
@SpringBootApplication
@EnableScheduling
public class MsClientesApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsClientesApplication.class, args);
    }
}
