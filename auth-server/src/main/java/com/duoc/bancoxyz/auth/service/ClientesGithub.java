package com.duoc.bancoxyz.auth.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Asociar la cuenta de GitHub con un cliente del banco (claim "clienteId").
 *
 * El cliente vincula su identidad digital con su RUT.
 * Se configura en auth-server.properties.
 */
@Component
public class ClientesGithub {

    private final Map<String, Long> asociaciones = new HashMap<>();
    private final Long porDefecto;

    public ClientesGithub(@Value("${auth.clientes-github:}") String asociaciones,
            @Value("${auth.cliente-por-defecto:}") String porDefecto) {
        if (StringUtils.hasText(asociaciones)) {
            for (String par : asociaciones.split(",")) {
                String[] partes = par.trim().split(":");
                if (partes.length == 2) {
                    this.asociaciones.put(partes[0].trim().toLowerCase(Locale.ROOT), Long.valueOf(partes[1].trim()));
                }
            }
        }
        this.porDefecto = StringUtils.hasText(porDefecto) ? Long.valueOf(porDefecto.trim()) : null;
    }

    public Long clienteDe(String githubLogin) {
        if (githubLogin == null) {
            return porDefecto;
        }
        return asociaciones.getOrDefault(githubLogin.toLowerCase(Locale.ROOT), porDefecto);
    }
}
