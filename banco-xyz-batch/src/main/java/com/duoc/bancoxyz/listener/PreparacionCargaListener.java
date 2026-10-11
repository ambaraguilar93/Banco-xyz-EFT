package com.duoc.bancoxyz.listener;

import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * EFT - PreparacionCargaListener
 *
 * Antes la tabla se borraba con DROP TABLE en schema.sql cada vez que partia
 * la aplicacion. En la EFT la base de datos es compartida con los
 * microservicios, asi que ya no se borra todo al iniciar.
 *
 * Esto hace que el step sea idempotente: si falla y se reejecuta, vuelve a
 * partir desde una tabla vacia y nunca duplica datos.
 *
 */
public class PreparacionCargaListener implements StepExecutionListener {

    private final JdbcTemplate jdbcTemplate;
    private final String tabla;
    private final String archivoOrigen;

    public PreparacionCargaListener(JdbcTemplate jdbcTemplate, String tabla, String archivoOrigen) {
        this.jdbcTemplate = jdbcTemplate;
        this.tabla = tabla;
        this.archivoOrigen = archivoOrigen;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {

        int filas = jdbcTemplate.update("DELETE FROM " + tabla);
        int rechazos = 0;

        if (archivoOrigen != null) {
            rechazos = jdbcTemplate.update(
                    "DELETE FROM registros_rechazados WHERE job_instance_id = ? AND archivo_origen = ?",
                    stepExecution.getJobExecution().getJobInstance().getId(), archivoOrigen);
        }

        System.out.printf(
                "[PREPARACION] %s: se limpio la tabla %s (%d filas) y %d rechazos previos de esta instancia%n",
                stepExecution.getStepName(), tabla, filas, rechazos);
    }
}
