package com.duoc.bancoxyz.listener;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import com.duoc.bancoxyz.config.TaskExecutorConfig;

/**
 * MetricasStepListener
 *
 * Instrumentacion del step para poder evaluar rendimiento y ajustar la
 * configuracion con datos.
 *
 * Mide cuanto demoro cada step, cuantos registros logro procesar.
 * Cuenta cuantos registros proceso cada hilo.
 * Imprime los contadores de Spring Batch.
 */
@Component
public class MetricasStepListener implements StepExecutionListener {

    private final ThreadPoolTaskExecutor taskExecutor;

    // Cuenta cuantos registros proceso cada hilo (nombre del hilo -> cantidad).
    private final Map<String, AtomicLong> registrosPorHilo = new ConcurrentHashMap<>();
    private final Set<String> hilosVistos = ConcurrentHashMap.newKeySet();

    public MetricasStepListener(
            @Qualifier(TaskExecutorConfig.EXECUTOR) ThreadPoolTaskExecutor taskExecutor) {

        this.taskExecutor = taskExecutor;
    }

    /**
     * Cada ItemProcessor llama a este metodo por cada registro que procesa.
     * Asi se arma el conteo de cuantos registros atendio cada hilo.
     */
    public void anotarHilo() {

        String hilo = Thread.currentThread().getName();
        hilosVistos.add(hilo);
        registrosPorHilo.computeIfAbsent(hilo, clave -> new AtomicLong()).incrementAndGet();
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {

        registrosPorHilo.clear();
        hilosVistos.clear();

        System.out.println();
        System.out.println("----------------------------------------------------------");
        System.out.printf("[STEP INICIO] %s%n", stepExecution.getStepName());
        System.out.printf("   Pool disponible -> %s%n",
                TaskExecutorConfig.resumen(taskExecutor));
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {

        Duration duracion = calcularDuracion(stepExecution);
        long milisegundos = Math.max(duracion.toMillis(), 1);
        long leidos = stepExecution.getReadCount();
        double porSegundo = leidos * 1000.0 / milisegundos;

        System.out.printf("[STEP FIN] %s | estado: %s%n",
                stepExecution.getStepName(), stepExecution.getStatus());

        System.out.printf("   Rendimiento -> duracion: %d ms | throughput: %.1f registros/seg%n",
                milisegundos, porSegundo);

        System.out.printf(
                "   Contadores  -> leidos: %d | escritos: %d | filtrados: %d | omitidos: %d | commits: %d | rollbacks: %d%n",
                leidos,
                stepExecution.getWriteCount(),
                stepExecution.getFilterCount(),
                stepExecution.getSkipCount(),
                stepExecution.getCommitCount(),
                stepExecution.getRollbackCount());

        imprimirRepartoPorHilo();

        return stepExecution.getExitStatus();
    }

    // Muestra en consola cuantos registros proceso cada hilo, ordenados por nombre.
    private void imprimirRepartoPorHilo() {

        if (registrosPorHilo.isEmpty()) {
            System.out.println("   Paralelismo -> step sin procesamiento por item (no aplica)");
            return;
        }

        System.out.printf("   Paralelismo -> %d hilo(s) participaron en el procesamiento:%n",
                registrosPorHilo.size());

        registrosPorHilo.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entrada -> System.out.printf("      %-22s %d registros%n",
                        entrada.getKey(), entrada.getValue().get()));

        if (registrosPorHilo.size() == 1) {
            System.out.println("      AVISO: un solo hilo. El step corrio en serie, "
                    + "revisar que el TaskExecutor este asignado.");
        }
    }

    // Calcula cuanto duro el step, restando la hora de termino menos la de inicio.
    private Duration calcularDuracion(StepExecution stepExecution) {

        if (stepExecution.getStartTime() == null || stepExecution.getEndTime() == null) {
            return Duration.ZERO;
        }

        return Duration.between(stepExecution.getStartTime(), stepExecution.getEndTime());
    }

    // Vista de solo lectura del reparto, para que el JobListener lo consolide.
    public Map<String, AtomicLong> repartoPorHilo() {
        return Collections.unmodifiableMap(registrosPorHilo);
    }
}
