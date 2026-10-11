package com.duoc.bancoxyz.bff.web.delegacion;

import java.time.Instant;

/** Token emitido por el auth-server para este canal, con su vencimiento. */
public record TokenDelegado(String valor, Instant expira, String scope) {

    /** Se renueva 20 s antes de vencer para no usar un token que expire en el camino. */
    public boolean vigente() {
        return Instant.now().isBefore(expira.minusSeconds(20));
    }
}
