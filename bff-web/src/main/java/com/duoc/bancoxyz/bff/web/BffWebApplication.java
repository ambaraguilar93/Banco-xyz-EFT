package com.duoc.bancoxyz.bff.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BFF del canal Web: respuestas completas para el navegador, login OAuth2 y
 * token delegado con todos los permisos del cliente.
 */
@SpringBootApplication
public class BffWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffWebApplication.class, args);
    }
}
