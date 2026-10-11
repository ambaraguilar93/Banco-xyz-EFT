package com.duoc.bancoxyz;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import com.duoc.bancoxyz.ejecucion.EjecutorJobs;

/**
 *
 * BancoXyzBatchApplication
 * Punto de entrada de la aplicacion.
 *
 * Segun el perfil activo:
 * - Sin perfil: corre los 3 Jobs de negocio, uno detras del otro.
 * - Con el perfil "escalamiento": no hace nada aca. Quien corre el
 * experimento de escalamiento es EscalamientoRunner, que tiene su propio.
 *
 */
@SpringBootApplication
public class BancoXyzBatchApplication implements CommandLineRunner, ExitCodeGenerator {

    private final EjecutorJobs ejecutorJobs;
    private final Job reporteTransaccionesDiariasJob;
    private final Job calculoInteresesMensualesJob;
    private final Job estadosCuentaAnualesJob;
    private final Environment environment;

    private int codigoSalida = 0;

    public BancoXyzBatchApplication(
            EjecutorJobs ejecutorJobs,
            @Qualifier("reporteTransaccionesDiariasJob") Job reporteTransaccionesDiariasJob,
            @Qualifier("calculoInteresesMensualesJob") Job calculoInteresesMensualesJob,
            @Qualifier("estadosCuentaAnualesJob") Job estadosCuentaAnualesJob,
            Environment environment) {

        this.ejecutorJobs = ejecutorJobs;
        this.reporteTransaccionesDiariasJob = reporteTransaccionesDiariasJob;
        this.calculoInteresesMensualesJob = calculoInteresesMensualesJob;
        this.estadosCuentaAnualesJob = estadosCuentaAnualesJob;
        this.environment = environment;
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext contexto = SpringApplication.run(
                BancoXyzBatchApplication.class, args);
        System.exit(SpringApplication.exit(contexto));
    }

    /**
     * Se ejecuta automaticamente al arrancar la app.
     *
     * Si el perfil "escalamiento" esta activo, no se hace nada
     * aca: se deja que EscalamientoRunner corra el experimento por su cuenta.
     * Si no, se lanzan los 3 Jobs de negocio en orden.
     */
    @Override
    public void run(String... args) throws Exception {

        if (environment.matchesProfiles("escalamiento")) {
            return;
        }

        // EFT: JobOperator.start() usa el RunIdIncrementer de cada job para crear
        // una instancia nueva (run.id = anterior + 1), por eso no se pasa timestamp.
        JobParameters parametros = new JobParameters();

        Map<String, JobExecution> resultados = new LinkedHashMap<>();
        for (Job job : new Job[] { reporteTransaccionesDiariasJob, calculoInteresesMensualesJob,
                estadosCuentaAnualesJob }) {
            resultados.put(job.getName(), ejecutorJobs.ejecutar(job, parametros));
        }

        System.out.println();
        System.out.println("==================== RESUMEN DE LA MIGRACION ====================");
        resultados.forEach((nombre, ejecucion) -> System.out.printf("  %-32s -> %s (instancia %d)%n",
                nombre, ejecucion.getStatus(), ejecucion.getJobInstance().getId()));
        System.out.println("=================================================================");

        boolean todoBien = resultados.values().stream()
                .allMatch(ejecucion -> ejecucion.getStatus() == BatchStatus.COMPLETED);
        codigoSalida = todoBien ? 0 : 1;
    }

    @Override
    public int getExitCode() {
        return codigoSalida;
    }
}
