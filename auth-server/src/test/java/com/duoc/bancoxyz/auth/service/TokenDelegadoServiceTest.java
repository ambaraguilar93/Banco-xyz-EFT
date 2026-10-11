package com.duoc.bancoxyz.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.duoc.bancoxyz.auth.config.JwtKeyConfig;
import com.duoc.bancoxyz.auth.service.ClientesBffProperties.ClienteBff;
import com.duoc.bancoxyz.auth.service.TokenDelegadoService.ErrorOAuth;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * EFT Parte 2: tokens delegados por canal. Sin Spring ni GitHub: se emite un token de
 * usuario con todos los permisos y se comprueba lo que recibe cada BFF.
 */
@DisplayName("auth-server - tokens delegados por canal")
class TokenDelegadoServiceTest {

    private static final String ISSUER = "http://localhost:9000";
    private static final String TODOS = "clientes.read cuentas.read pagos.read";
    private static final String ESCRITURA = "clientes.write cuentas.write pagos.write";

    private TokenDelegadoService servicio;
    private JwtTokenService login;
    private NimbusJwtDecoder decoder;
    private ClienteBff movil;
    private ClienteBff cajero;

    @BeforeEach
    void preparar() throws Exception {
        JwtKeyConfig keyConfig = new JwtKeyConfig();
        RSAKey rsaKey = keyConfig.rsaKey();
        JwtEncoder encoder = keyConfig.jwtEncoder(keyConfig.jwkSource(rsaKey));
        decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        movil = new ClienteBff("secreto-movil", "movil", "clientes.read cuentas.read pagos.read pagos.write", null,
                300L, false);
        cajero = new ClienteBff("secreto-cajero", "cajero", "cuentas.read pagos.read pagos.write", "tarjetas.verify",
                120L, true);
        ClientesBffProperties propiedades = new ClientesBffProperties(Map.of("bff-movil", movil, "bff-cajero", cajero));
        servicio = new TokenDelegadoService(encoder, rsaKey, propiedades, ISSUER, "banco-xyz");
        login = new JwtTokenService(encoder, ISSUER, "banco-xyz", 3600, TODOS, ESCRITURA, "");
    }

    @Test
    void elCanalMovilRecibeLosPermisosRecortados() {
        String usuario = login.emitir("ambar", null, null, 1L).accessToken();

        Jwt delegado = decoder.decode(servicio.intercambiar("bff-movil", movil, usuario).accessToken());

        assertThat(delegado.getSubject()).isEqualTo("ambar");
        assertThat(delegado.getClaimAsString("canal")).isEqualTo("movil");
        assertThat(((Number) delegado.getClaim("clienteId")).longValue()).isEqualTo(1L);
        assertThat(delegado.getClaimAsString("scope")).contains("pagos.write").doesNotContain("cuentas.write")
                .doesNotContain("clientes.write");
        assertThat(delegado.getClaimAsMap("act")).containsEntry("sub", "bff-movil");
        assertThat(delegado.getExpiresAt()).isBefore(delegado.getIssuedAt().plusSeconds(301));
    }

    @Test
    void unTokenDelegadoNoSePuedeVolverADelegar() {
        String usuario = login.emitir("ambar", null, null, 1L).accessToken();
        String delegado = servicio.intercambiar("bff-movil", movil, usuario).accessToken();

        assertThatThrownBy(() -> servicio.intercambiar("bff-movil", movil, delegado))
                .isInstanceOf(ErrorOAuth.class).hasMessageContaining("ya es un token delegado");
    }

    @Test
    void unUsuarioSinClienteNoObtieneTokenDelegado() {
        String usuario = login.emitir("visitante", null, null, null).accessToken();

        assertThatThrownBy(() -> servicio.intercambiar("bff-movil", movil, usuario))
                .isInstanceOf(ErrorOAuth.class).hasMessageContaining("no esta asociado");
    }

    @Test
    void conSecretoIncorrectoElBffNoSeAutentica() {
        assertThatThrownBy(() -> servicio.autenticar("bff-movil", "otro-secreto"))
                .isInstanceOfSatisfying(ErrorOAuth.class, e -> assertThat(e.status()).isEqualTo(401));
        assertThat(servicio.autenticar("bff-cajero", "secreto-cajero")).isSameAs(cajero);
    }

    @Test
    void laSesionDelCajeroQuedaLigadaACuentaYTerminal() {
        Jwt sesion = decoder.decode(
                servicio.credencialesCliente("bff-cajero", cajero, 2L, 108L, "ATM-STGO-0001").accessToken());

        assertThat(sesion.getSubject()).isEqualTo("cliente:2");
        assertThat(sesion.getClaimAsString("canal")).isEqualTo("cajero");
        assertThat(((Number) sesion.getClaim("cuentaId")).longValue()).isEqualTo(108L);
        assertThat(sesion.getClaimAsString("terminal")).isEqualTo("ATM-STGO-0001");
        assertThat(sesion.getClaimAsString("scope")).isEqualTo("cuentas.read pagos.read pagos.write");
    }

    @Test
    void soloElCajeroPuedePedirTokensANombreDeUnCliente() {
        assertThatThrownBy(() -> servicio.credencialesCliente("bff-movil", movil, 2L, 108L, null))
                .isInstanceOf(ErrorOAuth.class).hasMessageContaining("no puede pedir tokens");
        Jwt servicioCajero = decoder.decode(servicio.credencialesCliente("bff-cajero", cajero, null, null, null)
                .accessToken());
        assertThat(servicioCajero.getClaimAsString("scope")).isEqualTo("tarjetas.verify");
    }
}
