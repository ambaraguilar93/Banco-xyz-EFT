# Banco XYZ - Migración de procesos batch con Spring Batch

Evaluación Final Transversal - Desarrollo Backend III

El Banco XYZ tiene tres procesos batch en un sistema legacy que genera archivos CSV con datos inconsistentes. En este proyecto los reescribí con Spring Batch: se validan y corrigen los datos antes de guardarlos en PostgreSQL, y si algo falla el proceso se recupera solo.

| Proceso | Job | Archivo de entrada | Resultado |
|---|---|---|---|
| Transacciones diarias | `reporteTransaccionesDiariasJob` | `movimientos_financieros_diarios.csv` | `resumen_transacciones_diarias` |
| Intereses mensuales | `calculoInteresesMensualesJob` | `intereses_trimestrales.csv` | `intereses_calculados` |
| Estados de cuenta anuales | `estadosCuentaAnualesJob` | `estados_financieros_anuales.csv` | `estados_cuenta_anuales` |

Los CSV vienen de [fin_legacy_data](https://github.com/KariVillagran/fin_legacy_data) (carpeta `data/semana_3`) y están copiados en `src/main/resources/data/`.

## Tecnologías

- Java 21
- Spring Boot 4.0.7 y Spring Batch 6.0
- PostgreSQL 16 o 17
- Maven (wrapper incluido)

## Cómo se cumple lo pedido

| Requisito | Dónde está |
|---|---|
| Jobs con lectura, proceso y escritura | `FlatFileItemReader` -> processor -> `JdbcBatchItemWriter` en las clases `*JobConfig` |
| Reintentos ante fallos temporales | `FabricaStepsCarga` (hasta 3 intentos). Se prueba con el perfil `retry` |
| Omitir registros inválidos | `PoliticaOmisionPersonalizada` y `RechazoSkipListener`, que guardan todo en `registros_rechazados` |
| Optimizar el procesamiento | Chunks y 3 hilos (`TaskExecutorConfig`). Se mide con el perfil `escalamiento` |
| Políticas de finalización | `ContinuidadDecider` (CONTINUAR, DEGRADADO o SIN_DATOS) |
| Reejecución automática | `EjecutorJobs` reinicia el job hasta 2 veces con `JobOperator.restart()`. Se prueba con el perfil `fallo-critico` |
| Integridad de los datos | `VerificacionIntegridadTasklet`: líneas = válidos + rechazados |

## Estructura

```
src/main/java/com/duoc/bancoxyz/
    config/        Configuración de los 3 jobs, steps de carga y pool de hilos
    ejecucion/     Reejecución automática y simulación de fallo
    integridad/    Verificación final de cada job
    decider/       Decide si el job continúa según el % descartado
    processor/     Reglas de validación de cada archivo
    policy/        Qué registros se omiten y cuáles no
    listener/      Métricas, rechazos y resumen por consola
    repository/    Guardado de rechazos
    escalamiento/  Experimento de hilos y chunk
```

## Reglas de validación

**Transacciones**
- Fecha inválida: se omite.
- Monto nulo, cero o negativo: se rechaza.
- Tipo distinto de `debito` o `credito`: se rechaza.
- Misma fecha, monto y tipo con otro id: se rechaza por duplicado.

**Intereses**
- Saldo nulo, cero o negativo: se rechaza.
- Edad fuera de 18 a 100: se rechaza.
- Tipo distinto de `ahorro` o `prestamo`: se rechaza.
- Cada cuenta recibe interés una sola vez, se deja la primera aparición válida (`depurarDuplicadosInteresesStep`).
- Tasas mensuales: ahorro 0,5 % y préstamo 1,5 % (configurables).

**Estados de cuenta**
- Fecha inválida: se omite.
- Monto nulo o cero: se rechaza.
- Movimientos válidos: `deposito`, `retiro`, `compra` y `pago` (sin importar las tildes).
- Descripción vacía: se completa con `SIN DESCRIPCION`.
- El depósito suma y los demás restan.

## Cómo ejecutar

1. Crear la base de datos en PostgreSQL: `CREATE DATABASE banco_xyz;` (las tablas se crean solas al iniciar).
2. Configurar la conexión con las variables `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` y `DB_PASSWORD`.
3. Ejecutar:

```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run                                              # los 3 jobs
./mvnw spring-boot:run "-Dspring-boot.run.profiles=retry"           # prueba de reintentos
./mvnw spring-boot:run "-Dspring-boot.run.profiles=fallo-critico"   # prueba de reejecución
./mvnw spring-boot:run "-Dspring-boot.run.profiles=escalamiento"    # comparación de hilos y chunk
```

Al terminar se imprime el `RESUMEN DE LA MIGRACION` con el estado de cada job.

## Resultados (1.000 registros por archivo)

| Archivo | Migrados | Rechazados |
|---|---|---|
| `movimientos_financieros_diarios.csv` | 378 | 622 |
| `intereses_trimestrales.csv` | 49 cuentas | 951 |
| `estados_financieros_anuales.csv` | 836 | 164 |

En los tres casos se cumple que líneas = migrados + rechazados. Como se usan 3 hilos, en intereses el reparto entre "duplicado" y "cuenta repetida" puede variar un poco entre corridas, pero el total siempre cuadra.

Para revisar los resultados:

```sql
SELECT * FROM auditoria_integridad ORDER BY id DESC;
SELECT * FROM registros_rechazados LIMIT 20;
```

## Escalamiento

Con el perfil `escalamiento` se corre varias veces la carga de transacciones con distintos hilos y tamaños de chunk:

| Combinación | Duración | Throughput |
|---|---|---|
| 1 hilo (serie), chunk 20 | 686 ms | 1.458 reg/s |
| 2 hilos, chunk 20 | 525 ms | 1.905 reg/s |
| 3 hilos, chunk 20 | 482 ms | 2.075 reg/s |
| **6 hilos, chunk 20** | **328 ms** | **3.049 reg/s** |
| 3 hilos, chunk 50 | 383 ms | 2.611 reg/s |
| 3 hilos, chunk 100 | 359 ms | 2.786 reg/s |
| 3 hilos, chunk 5 (la que usan los jobs) | 359 ms | 2.786 reg/s |

La mejor combinación fue 6 hilos, unas 2,1 veces más rápida que en serie. En todas se obtuvo el mismo resultado (378 escritos y 49 omitidos), así que el paralelismo no cambia los datos. Los tiempos son muy cortos (menos de un segundo), por lo que las diferencias pequeñas pueden ser ruido y varían entre corridas.

## Legacy vs Spring Batch

| Legacy | Spring Batch |
|---|---|
| Un registro dañado detenía o contaminaba la corrida | Se omite o rechaza con su motivo y el resto sigue |
| Si caía la conexión había que relanzar todo a mano | Reintentos y reejecución desde el step que falló |
| Procesamiento secuencial | Chunks en paralelo |
| Sin control de calidad | Decider y verificación de integridad |
| Sin trazabilidad | Tablas `registros_rechazados`, `auditoria_integridad` y `BATCH_*` |