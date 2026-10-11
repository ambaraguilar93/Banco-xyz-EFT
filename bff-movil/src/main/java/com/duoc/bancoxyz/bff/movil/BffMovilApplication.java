package com.duoc.bancoxyz.bff.movil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BFF del canal Movil: respuestas livianas, dispositivo identificado y token
 * delegado con permisos reducidos.
 */
@SpringBootApplication
public class BffMovilApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffMovilApplication.class, args);
    }
}
