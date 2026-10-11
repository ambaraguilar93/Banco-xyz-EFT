package com.duoc.bancoxyz.integridad;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.duoc.bancoxyz.exception.FalloNoRecuperableException;

/**
 * EFT - VerificacionIntegridadTasklet
 *
 * Ultimo step de cada job. Comprueba que la migracion no perdio ni duplico
 * ningun registro del archivo legacy.
 *
 */
public class VerificacionIntegridadTasklet implements Tasklet {

    private static final String INSERT_AUDITORIA = """
            INSERT INTO auditoria_integridad
                (job_instance_id, job_execution_id, job_name, archivo_origen,
                 lineas_archivo, registros_validos, registros_rechazados, consistente)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transaccionPropia;
    private final Resource archivo;
    private final String archivoOrigen;
    private final String tablaDestino;

    public VerificacionIntegridadTasklet(JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            Resource archivo,
            String archivoOrigen,
            String tablaDestino) {

        this.jdbcTemplate = jdbcTemplate;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.archivo = archivo;
        this.archivoOrigen = archivoOrigen;
        this.tablaDestino = tablaDestino;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

        var jobExecution = chunkContext.getStepContext().getStepExecution().getJobExecution();
        long instancia = jobExecution.getJobInstance().getId();
        String job = jobExecution.getJobInstance().getJobName();

        int lineas = contarLineasDeDatos();
        int validos = contar("SELECT COUNT(*) FROM " + tablaDestino);
        int rechazados = contar(
                "SELECT COUNT(*) FROM registros_rechazados WHERE job_instance_id = ? AND archivo_origen = ?",
                instancia, archivoOrigen);
        boolean consistente = lineas == validos + rechazados;

        transaccionPropia.executeWithoutResult(estado -> jdbcTemplate.update(INSERT_AUDITORIA,
                instancia, jobExecution.getId(), job, archivoOrigen, lineas, validos, rechazados, consistente));

        System.out.println();
        System.out.println("[INTEGRIDAD] " + job + " (" + archivoOrigen + ")");
        System.out.printf("   lineas del archivo   : %d%n", lineas);
        System.out.printf("   validos en %-10s: %d%n", tablaDestino, validos);
        System.out.printf("   rechazados           : %d%n", rechazados);
        System.out.printf("   %d = %d + %d  ->  %s%n", lineas, validos, rechazados,
                consistente ? "CUADRA" : "NO CUADRA");

        if (!consistente) {
            throw new FalloNoRecuperableException("Integridad de " + archivoOrigen + ": " + lineas
                    + " lineas pero " + validos + " validos + " + rechazados + " rechazados");
        }

        return RepeatStatus.FINISHED;
    }

    // Cuenta las lineas de datos (todas menos el encabezado), igual que el reader.
    private int contarLineasDeDatos() throws IOException {

        try (BufferedReader lector = new BufferedReader(
                new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8))) {

            return (int) lector.lines().skip(1).count();
        }
    }

    private int contar(String sql, Object... parametros) {

        Integer total = jdbcTemplate.queryForObject(sql, Integer.class, parametros);
        return total == null ? 0 : total;
    }
}
