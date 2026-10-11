package com.duoc.bancoxyz.auth.service;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Los BFF registrados como clientes OAuth 2.0 del auth-server.
 *
 * Cada canal tiene sus propias credenciales y permisos: aunque el usuario
 * tenga todos los scopes, el token delegado del canal movil nunca trae
 * cuentas.write.
 *
 * @param clientes clave = client_id (bff-web, bff-movil, bff-cajero)
 */
@ConfigurationProperties(prefix = "auth")
public record ClientesBffProperties(Map<String, ClienteBff> bff) {

    public record ClienteBff(String secret, String canal, String scopes, String scopesServicio,
            Long expiracionSegundos, Boolean asegurarCliente) {

        public long expiracion() {
            return expiracionSegundos == null ? 300 : expiracionSegundos;
        }

        public boolean puedeAsegurarCliente() {
            return Boolean.TRUE.equals(asegurarCliente);
        }
    }

    public Map<String, ClienteBff> bff() {
        return bff == null ? Map.of() : bff;
    }
}
