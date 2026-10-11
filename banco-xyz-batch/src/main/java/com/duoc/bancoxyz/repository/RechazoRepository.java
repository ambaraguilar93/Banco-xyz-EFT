package com.duoc.bancoxyz.repository;

import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 
 * RechazoRepository
 * Con esta clase se creara un historial del motivo por el
 * que el registro fue rechazado.
 * Con esta tabla el rechazo no se pierde, sino que queda
 * guardado para un posible proceso posterior.
 *
 */
@Repository
public class RechazoRepository {

    private static final String SQL = """
            INSERT INTO registros_rechazados
                (job_instance_id, job_name, archivo_origen, identificador, motivo, dato_original)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transaccionPropia;

    public RechazoRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void registrar(String jobName,
            String archivoOrigen,
            String identificador,
            String motivo,
            String datoOriginal) {

        Long instancia = instanciaActual();

        transaccionPropia.executeWithoutResult(estado -> jdbcTemplate.update(SQL, instancia, jobName,
                archivoOrigen, recortar(identificador, 60), recortar(motivo, 250), recortar(datoOriginal, 500)));

        System.out.printf("  [RECHAZADO] %s -> %s (%s)%n", identificador, motivo, archivoOrigen);
    }

    /**
     * EFT: instancia del job que esta corriendo en este hilo. Spring Batch
     * registra el step en cada hilo del pool, por eso funciona tambien en los
     * steps con varios hilos.
     */
    private Long instanciaActual() {

        StepContext contexto = StepSynchronizationManager.getContext();
        return contexto == null ? null
                : contexto.getStepExecution().getJobExecution().getJobInstance().getId();
    }

    private static String recortar(String texto, int maximo) {
        return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
