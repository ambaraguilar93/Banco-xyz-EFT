package com.duoc.bancoxyz.config;

import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.SynchronizedItemStreamReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import com.duoc.bancoxyz.exception.FalloTransitorioSimulado;
import com.duoc.bancoxyz.listener.MetricasStepListener;
import com.duoc.bancoxyz.listener.RechazoSkipListener;
import com.duoc.bancoxyz.policy.PoliticaOmisionPersonalizada;
import com.duoc.bancoxyz.repository.RechazoRepository;
import com.duoc.bancoxyz.writer.WriterConFalloSimulado;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.TransientDataAccessException;

// Los Jobs neecsitan el step de carga configurado igual 
// (mismos hilos en paralelo, mismo tamano de chunk, misma regla
// de que se omite y que se reintenta). En vez de repetir esta config 3 veces en 
// cada job, esta clase arma una sola para los 3 jobs.
@Component
public class FabricaStepsCarga {

        private final ThreadPoolTaskExecutor taskExecutor;
        private final MetricasStepListener metricasStepListener;
        private final RechazoRepository rechazoRepository;

        private final int tamanoChunk;
        private final long limiteOmisiones;
        private final int limiteReintentos;
        private final boolean simularFalloTransitorio;
        private final int fallosASimular;

        public FabricaStepsCarga(
                        @Qualifier(TaskExecutorConfig.EXECUTOR) ThreadPoolTaskExecutor taskExecutor,
                        MetricasStepListener metricasStepListener,
                        RechazoRepository rechazoRepository,
                        @Value("${banco.chunk.tamano:5}") int tamanoChunk,
                        @Value("${banco.skip.limite:100}") long limiteOmisiones,
                        @Value("${banco.retry.intentos:3}") int limiteReintentos,
                        @Value("${banco.simular.fallo-transitorio:false}") boolean simularFalloTransitorio,
                        @Value("${banco.simular.cantidad-fallos:2}") int fallosASimular) {

                this.taskExecutor = taskExecutor;
                this.metricasStepListener = metricasStepListener;
                this.rechazoRepository = rechazoRepository;
                this.tamanoChunk = tamanoChunk;
                this.limiteOmisiones = limiteOmisiones;
                this.limiteReintentos = limiteReintentos;
                this.simularFalloTransitorio = simularFalloTransitorio;
                this.fallosASimular = fallosASimular;
        }

        /**
         * Construye un step de carga paralelo, tolerante a fallos y auditado,
         * usando el tamano de chunk y el pool de hilos ya fijados en
         * application.properties.
         */
        public <I, O> Step construir(String nombreStep,
                        JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        ItemStreamReader<I> reader,
                        ItemProcessor<I, O> processor,
                        ItemWriter<O> writer,
                        String nombreJob,
                        String archivoOrigen) {

                return construir(nombreStep, jobRepository, transactionManager, reader, processor,
                                writer, nombreJob, archivoOrigen, tamanoChunk, taskExecutor);
        }

        /**
         * EFT: igual que el metodo de arriba, pero con un listener de
         * preparacion que limpia la tabla destino antes de que el step
         * empiece. Asi el step es idempotente y se puede reejecutar
         * automaticamente sin duplicar datos (ver PreparacionCargaListener).
         */
        public <I, O> Step construir(String nombreStep,
                        JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        ItemStreamReader<I> reader,
                        ItemProcessor<I, O> processor,
                        ItemWriter<O> writer,
                        String nombreJob,
                        String archivoOrigen,
                        StepExecutionListener preparacion) {

                return construir(nombreStep, jobRepository, transactionManager, reader, processor,
                                writer, nombreJob, archivoOrigen, tamanoChunk, taskExecutor, preparacion);
        }

        /**
         * Permite construir un step con las mismas politicas de omision/retry/auditoria
         * que los steps, pero con un tamano de chunk y un TaskExecutor distintos a
         * los que vienen fijos por configuracion.
         */
        public <I, O> Step construir(String nombreStep,
                        JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        ItemStreamReader<I> reader,
                        ItemProcessor<I, O> processor,
                        ItemWriter<O> writer,
                        String nombreJob,
                        String archivoOrigen,
                        int tamanoChunkOverride,
                        ThreadPoolTaskExecutor taskExecutorOverride) {

                return construir(nombreStep, jobRepository, transactionManager, reader, processor,
                                writer, nombreJob, archivoOrigen, tamanoChunkOverride, taskExecutorOverride, null);
        }

        private <I, O> Step construir(String nombreStep,
                        JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        ItemStreamReader<I> reader,
                        ItemProcessor<I, O> processor,
                        ItemWriter<O> writer,
                        String nombreJob,
                        String archivoOrigen,
                        int tamanoChunkOverride,
                        ThreadPoolTaskExecutor taskExecutorOverride,
                        StepExecutionListener preparacion) {

                // Si se ejecuta el perfil 'retry', se envuelve el writer real
                // para que falle a proposito un par de veces y se vea el reintento.
                ItemWriter<O> writerFinal = simularFalloTransitorio
                                ? new WriterConFalloSimulado<>(writer, fallosASimular)
                                : writer;

                var builder = new StepBuilder(nombreStep, jobRepository)
                                .<I, O>chunk(tamanoChunkOverride, transactionManager);

                // EFT: la preparacion se registra primero para que limpie la
                // tabla antes que cualquier otro listener o hilo empiece.
                if (preparacion != null) {
                        builder.listener(preparacion);
                }

                return builder
                                .reader(new SynchronizedItemStreamReader<>(reader))
                                .processor(processor)
                                .writer(writerFinal)
                                .faultTolerant()
                                .skipPolicy(new PoliticaOmisionPersonalizada(nombreStep, limiteOmisiones))
                                .retryLimit(limiteReintentos)
                                .retry(TransientDataAccessException.class)
                                .retry(CannotAcquireLockException.class)
                                .retry(DeadlockLoserDataAccessException.class)
                                .retry(FalloTransitorioSimulado.class)
                                .listener(new RechazoSkipListener<I, O>(
                                                rechazoRepository, nombreJob, archivoOrigen))
                                .listener(metricasStepListener)
                                .taskExecutor(taskExecutorOverride)
                                .build();
        }

        public int getTamanoChunk() {
                return tamanoChunk;
        }
}
