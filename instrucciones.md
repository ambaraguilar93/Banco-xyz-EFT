# Instrucciones para ejecutar y probar - Banco XYZ

Cómo levantar el sistema y comprobar que cada parte funciona.

## 1. Requisitos

- Docker Desktop con **8 GB de RAM**, porque son 13 contenedores.
- Cuenta de GitHub (el login usa una OAuth App).
- `curl`.

## 2. Preparación

**OAuth App de GitHub:** en *Settings > Developer settings > OAuth Apps > New OAuth App* poner:
- Homepage URL: `http://localhost:9000`
- Callback URL: `http://localhost:9000/login/oauth2/code/github`

Guardar el Client ID y generar un Client Secret.

**Variables de entorno:**
```bash
cp .env.example .env
```
Completar `GITHUB_CLIENT_ID` y `GITHUB_CLIENT_SECRET`. El resto sirve tal cual para pruebas locales.

**Certificados:** ya vienen en el repositorio. Solo se regeneran si faltan, con `cd certificados && ./generar-certificados.sh`.

## 3. Levantar el sistema

```bash
docker compose up -d --build
docker compose ps
```
La primera vez demora varios minutos. Lo esperado:
- `banco-xyz-batch`: **Exited (0)** (migra los datos y termina).
- Todo lo demás: **healthy**.

| URL / puerto | Qué es |
|---|---|
| http://localhost:8761 | Eureka (usuario `eureka`, clave `eureka2026`) |
| http://localhost:8090 | Kafka UI |
| http://localhost:9000/oauth2/authorization/github | Login (entrega el `accessToken`) |
| `localhost:5433` | PostgreSQL `banco_xyz` |
| https://localhost:8443 / 8444 / 8445 | BFF web / móvil / cajero |
| http://localhost:8101 / 8201 / 8301 | ms-clientes / ms-cuentas / ms-pagos |

Para apagar: `docker compose down` (con `-v` también borra la base de datos).

---

## 4. Probar el batch

```bash
docker logs banco-xyz-batch | grep -E "INTEGRIDAD|CUADRA|RESUMEN|COMPLETED"
```
Debe aparecer `CUADRA` en los 3 jobs y los 3 en `COMPLETED`.

| Archivo | Migrados | Rechazados |
|---|---|---|
| `movimientos_financieros_diarios.csv` | 378 | 622 |
| `intereses_trimestrales.csv` | 49 cuentas | 951 |
| `estados_financieros_anuales.csv` | 836 | 164 |

Consultas en la base (`docker exec -it banco-postgres psql -U postgres -d banco_xyz`):
```sql
SELECT job_name, lineas_archivo, registros_validos, registros_rechazados, consistente
  FROM auditoria_integridad ORDER BY id DESC LIMIT 3;

SELECT archivo_origen, LEFT(motivo, 60) AS motivo, COUNT(*)
  FROM registros_rechazados GROUP BY 1, 2 ORDER BY 1, 3 DESC;
```

**Reintentos, reejecución y escalamiento** (se corren fuera de Docker, contra el PostgreSQL del compose):
```bash
docker compose up -d postgres
cd banco-xyz-batch
export DB_PORT=5433
./mvnw spring-boot:run "-Dspring-boot.run.profiles=retry"
./mvnw spring-boot:run "-Dspring-boot.run.profiles=fallo-critico"
./mvnw spring-boot:run "-Dspring-boot.run.profiles=escalamiento"
```

| Perfil | Qué buscar en la consola |
|---|---|
| `retry` | `[FALLO SIMULADO] ... intento 1/2` y luego el step termina `COMPLETED` |
| `fallo-critico` | `[FALLO CRITICO SIMULADO]`, luego `[REEJECUCION]` y por último `COMPLETED` |
| `escalamiento` | Tabla con duración y registros por segundo de cada combinación |

---

## 5. Obtener un token

1. Abrir `http://localhost:9000/oauth2/authorization/github` e iniciar sesión.
2. Copiar el `accessToken` de la respuesta.

```bash
TOKEN=<accessToken>
H="Authorization: Bearer $TOKEN"
CA=certificados/ca/banco-xyz-dev-ca.crt
```
El token dura 1 hora. Si caduca, se repite el login.

---

## 6. Probar los microservicios

**Datos migrados:**
```bash
curl -s -H "$H" localhost:8101/api/clientes
curl -s -H "$H" "localhost:8201/api/cuentas?clienteId=2"
```

**Pago y transferencia** (responden `202` con la operación `PENDIENTE`, que luego pasa a `COMPLETADA`):
```bash
curl -s -H "$H" -H "Idempotency-Key: pago-0001" -H "Content-Type: application/json" \
     -d '{"cuentaOrigenId":101,"monto":1500,"descripcion":"Luz"}' localhost:8301/api/pagos
curl -s -H "$H" -H "Idempotency-Key: transf-0001" -H "Content-Type: application/json" \
     -d '{"cuentaOrigenId":101,"cuentaDestinoId":108,"monto":2000}' localhost:8301/api/transferencias
curl -s -H "$H" localhost:8301/api/operaciones/<id>
```

**Alerta por depósito alto:**
```bash
curl -s -H "$H" -H "Idempotency-Key: dep-0001" -H "Content-Type: application/json" \
     -d '{"cuentaDestinoId":108,"monto":1500000}' localhost:8301/api/depositos
curl -s -H "$H" "localhost:8101/api/clientes/2/notificaciones?categoria=ALERTA"
```

**Desactivar un cliente bloquea sus cuentas:**
```bash
curl -s -X PATCH -H "$H" -H "Content-Type: application/json" \
     -d '{"motivo":"Solicitud del cliente"}' localhost:8101/api/clientes/7/desactivacion
curl -s -H "$H" "localhost:8201/api/cuentas?clienteId=7"
```

**Seguridad:** `curl -s -i localhost:8201/api/cuentas/101` sin token debe responder `401`.

---

## 7. Probar los BFF

Se necesita un token con `clienteId`: hacer el login de la sección 5.

**Web** (respuesta completa):
```bash
curl -s --cacert $CA -H "$H" https://localhost:8443/api/web/inicio
curl -s --cacert $CA -H "$H" https://localhost:8443/api/web/cuentas/108     # cuenta ajena: 403
```

**Móvil** (respuesta liviana, exige el header de dispositivo):
```bash
M="X-Device-Id: pixel7-ambar-0001"
curl -s --cacert $CA -H "$H" -H "$M" https://localhost:8444/api/movil/inicio
curl -s --cacert $CA -H "$H" https://localhost:8444/api/movil/inicio        # sin dispositivo: 400
curl -s --cacert $CA -H "$H" -H "$M" -H "Idempotency-Key: mov-transf-001" -H "Content-Type: application/json" \
     -d '{"cuentaOrigenId":101,"cuentaDestinoId":108,"monto":600000}' \
     https://localhost:8444/api/movil/transferencias                         # sobre el tope de $500.000: 409
```

**Cajero** (mTLS + tarjeta y PIN):
```bash
ATM="--cacert $CA --cert certificados/cajero-ATM-STGO-0001.crt --key certificados/cajero-ATM-STGO-0001.key"
curl -s --cacert $CA https://localhost:8445/api/cajero/saldo                # sin certificado: se corta el TLS

curl -s $ATM -H "Content-Type: application/json" \
     -d '{"numeroTarjeta":"4532000000000101","pin":"1234"}' https://localhost:8445/api/cajero/sesion
SESION=<token de la respuesta>                                               # dura 2 minutos

curl -s $ATM -H "Authorization: Bearer $SESION" https://localhost:8445/api/cajero/saldo
curl -s $ATM -H "Authorization: Bearer $SESION" -H "Idempotency-Key: atm-0001-000001" \
     -H "Content-Type: application/json" -d '{"monto":20000}' https://localhost:8445/api/cajero/retiros
```
- Tarjeta de prueba: `4532` + número de cuenta de 12 dígitos (cuenta 101 es `4532000000000101`). PIN `1234`.
- Con 3 PIN incorrectos la tarjeta se bloquea y se genera una alerta ALTA.
- Retiros: mínimo $5.000, máximo $200.000 y múltiplos de $5.000.

---

## 8. Tolerancia a fallos

| Prueba | Cómo | Resultado esperado |
|---|---|---|
| `ms-cuentas` caído | `docker compose stop ms-cuentas` y hacer un pago | Pago `202` con `validacion: DIFERIDA`. Al reiniciar el servicio, el pago se aplica |
| Kafka caído | `docker compose stop kafka`, crear un pago y luego `start kafka` | El evento queda en `outbox_pagos` y se publica solo al volver Kafka |
| Mensaje duplicado | Repetir el POST con la misma `Idempotency-Key` | Devuelve la misma operación y el saldo se mueve una sola vez |
| Saldo insuficiente | Pago mayor al saldo | Operación `RECHAZADA`. Con 3 rechazos en 10 min hay alerta ALTA |
| Mensaje inválido | Publicar texto no JSON en `banco.operaciones.solicitadas` desde Kafka UI | Pasa al tópico `-dlt` sin detener el consumidor |
| Circuit Breaker | `curl localhost:8301/actuator/circuitbreakers` | Estado de cada circuito |

## 9. Escalabilidad

```bash
docker compose up -d --scale ms-cuentas=3 --scale ms-pagos=2
```
- Las nuevas réplicas aparecen en Eureka.
- Las llamadas se reparten entre ellas y las 3 particiones de Kafka se distribuyen entre las réplicas.
- En los logs, `[ms-cuentas:<contenedor>]` indica qué réplica atendió cada request.
- Máximo 3 réplicas por servicio.

## 10. Pruebas unitarias

```bash
cd auth-server && ./mvnw test
```

## 11. Problemas comunes

| Síntoma | Solución |
|---|---|
| Contenedores que se reinician o quedan `unhealthy` | Asignar 8 GB o más a Docker |
| El login no funciona (`sin-configurar`) | Completar `GITHUB_CLIENT_ID` y `GITHUB_CLIENT_SECRET` en `.env` y ejecutar `docker compose up -d auth-server` |
| Los microservicios no inician | Revisar `docker logs banco-xyz-batch` |
| `401` en un BFF | El token venció o no trae `clienteId`: repetir el login |
| Error de certificado en `curl` | Usar `--cacert certificados/ca/banco-xyz-dev-ca.crt` o `-k` |