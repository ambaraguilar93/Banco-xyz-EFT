package com.duoc.bancoxyz.config;

import javax.sql.DataSource;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.duoc.bancoxyz.decider.ContinuidadDecider;
import com.duoc.bancoxyz.ejecucion.SimuladorFalloCritico;
import com.duoc.bancoxyz.integridad.VerificacionIntegridadTasklet;
import com.duoc.bancoxyz.listener.PreparacionCargaListener;
import com.duoc.bancoxyz.listener.JobListener;
import com.duoc.bancoxyz.listener.MetricasStepListener;
import com.duoc.bancoxyz.model.ResumenDiario;
import com.duoc.bancoxyz.model.Transaccion;
import com.duoc.bancoxyz.processor.TransaccionProcessor;
import com.duoc.bancoxyz.util.CampoCsv;
import com.duoc.bancoxyz.util.ParseadorFechas;

/**
 * 
 * Se arma el Job 1 - Reporte de transacciones diarias.
 *
 * EFT:
 * - El archivo es movimientos_financieros_diarios.csv, el nombre
 * sale de la property banco.archivo.transacciones.
 * - Step 3. verificarIntegridadTransaccionesStep comprueba que
 * lineas = validos + rechazados.
 * - Cada step limpia lo suyo antes de empezar (ya no hay DROP TABLE al
 * iniciar), asi se pueden reejecutar sin duplicar datos.
 */
@Configuration
public class TransaccionesJobConfig {

        private static final String NOMBRE_JOB = "reporteTransaccionesDiariasJob";
        private static final String TABLA = "transacciones";

        // ------------------------------------------------------------------
        // STEP 1: transacciones.csv -> tabla transacciones
        // ------------------------------------------------------------------
        /**
         * Arma el lector del CSV, le dice como se llaman las columnas del archivo.
         * Y como convertir cada fila de texto plano en un objeto Transaccion.
         */
        @Bean
        public FlatFileItemReader<Transaccion> transaccionReader(
                        @Value("${banco.data.directorio}") String directorio,
                        @Value("${banco.archivo.transacciones}") String archivo) {

                // EFT: saveState(false) porque el step corre con varios hilos y la
                // posicion del lector no es confiable, al reejecutar se recarga todo.
                return new FlatFileItemReaderBuilder<Transaccion>()
                                .name("transaccionReader")
                                .saveState(false)
                                .resource(new ClassPathResource(directorio + "/" + archivo))
                                .encoding("UTF-8")
                                .linesToSkip(1)
                                .delimited()
                                .delimiter(",")
                                .names("id", "fecha", "monto", "tipo")
                                .fieldSetMapper(campos -> {

                                        Transaccion transaccion = new Transaccion();
                                        transaccion.setId(CampoCsv.entero(campos, "id"));
                                        transaccion.setFecha(ParseadorFechas.parsear(
                                                        CampoCsv.texto(campos, "fecha")));
                                        transaccion.setMonto(CampoCsv.decimal(campos, "monto"));
                                        transaccion.setTipo(CampoCsv.texto(campos, "tipo"));
                                        return transaccion;
                                })
                                .build();
        }

        // Arma el escritor que guarda cada Transaccion valida en la tabla
        // transacciones.
        @Bean
        public JdbcBatchItemWriter<Transaccion> transaccionWriter(DataSource dataSource) {

                return new JdbcBatchItemWriterBuilder<Transaccion>()
                                .dataSource(dataSource)
                                .sql("""
                                                INSERT INTO transacciones (id, fecha, monto, tipo)
                                                VALUES (:id, :fecha, :monto, :tipo)
                                                """)
                                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                                .build();
        }

        /**
         * Junta el reader y el writer con TransaccionProcessor, y le pide a
         * FabricaStepsCarga que arme el step completo.
         */
        @Bean
        public Step cargarTransaccionesStep(FabricaStepsCarga fabrica,
                        JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        FlatFileItemReader<Transaccion> transaccionReader,
                        TransaccionProcessor transaccionProcessor,
                        JdbcBatchItemWriter<Transaccion> transaccionWriter,
                        JdbcTemplate jdbcTemplate,
                        @Value("${banco.archivo.transacciones}") String archivo) {

                return fabrica.construir(
                                "cargarTransaccionesStep",
                                jobRepository,
                                transactionManager,
                                transaccionReader,
                                transaccionProcessor,
                                transaccionWriter,
                                NOMBRE_JOB,
                                archivo,
                                new PreparacionCargaListener(jdbcTemplate, TABLA, archivo));
        }

        // ------------------------------------------------------------------
        // STEP 2: tabla transacciones -> tabla resumen_transacciones_diarias
        // ------------------------------------------------------------------
        /**
         * Arma el lector del segundo step, en vez de leer un archivo, lee
         * desde la base de datos con una consulta SQL, sumando los debitos
         * y creditos de cada dia.
         */
        @Bean
        public JdbcCursorItemReader<ResumenDiario> resumenReader(DataSource dataSource) {

                return new JdbcCursorItemReaderBuilder<ResumenDiario>()
                                .name("resumenReader")
                                .saveState(false)
                                .dataSource(dataSource)
                                .sql("""
                                                SELECT fecha,
                                                       COUNT(*) AS cantidad_transacciones,
                                                       COALESCE(SUM(CASE WHEN tipo = 'DEBITO'  THEN monto ELSE 0 END), 0) AS total_debitos,
                                                       COALESCE(SUM(CASE WHEN tipo = 'CREDITO' THEN monto ELSE 0 END), 0) AS total_creditos,
                                                       MAX(monto) AS monto_mayor
                                                  FROM transacciones
                                                 GROUP BY fecha
                                                 ORDER BY fecha
                                                """)
                                .rowMapper((rs, filaNumero) -> {

                                        ResumenDiario resumen = new ResumenDiario();
                                        resumen.setFecha(rs.getDate("fecha").toLocalDate());
                                        resumen.setCantidadTransacciones(rs.getInt("cantidad_transacciones"));
                                        resumen.setTotalDebitos(rs.getBigDecimal("total_debitos"));
                                        resumen.setTotalCreditos(rs.getBigDecimal("total_creditos"));
                                        resumen.setMontoMayor(rs.getBigDecimal("monto_mayor"));
                                        return resumen;
                                })
                                .build();
        }

        // Arma el escritor que guarda cada resumen diario en la tabla
        // resumen_transacciones_diarias
        @Bean
        public JdbcBatchItemWriter<ResumenDiario> resumenWriter(DataSource dataSource) {

                return new JdbcBatchItemWriterBuilder<ResumenDiario>()
                                .dataSource(dataSource)
                                .sql("""
                                                INSERT INTO resumen_transacciones_diarias
                                                    (fecha, cantidad_transacciones, total_debitos, total_creditos, monto_mayor)
                                                VALUES
                                                    (:fecha, :cantidadTransacciones, :totalDebitos, :totalCreditos, :montoMayor)
                                                """)
                                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                                .build();
        }

        /**
         * Este segundo step se arma directo con StepBuilder en vez de
         * usar la FabricaStepsCarga.
         */
        @Bean
        public Step resumenTransaccionesStep(JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        JdbcCursorItemReader<ResumenDiario> resumenReader,
                        JdbcBatchItemWriter<ResumenDiario> resumenWriter,
                        MetricasStepListener metricasStepListener,
                        SimuladorFalloCritico simuladorFalloCritico,
                        JdbcTemplate jdbcTemplate,
                        @Value("${banco.chunk.tamano:5}") int tamanoChunk) {

                // EFT: limpia el resumen anterior antes de empezar (idempotente) y
                // permite simular un fallo critico para ver la reejecucion.
                return new StepBuilder("resumenTransaccionesStep", jobRepository)
                                .<ResumenDiario, ResumenDiario>chunk(tamanoChunk, transactionManager)
                                .reader(resumenReader)
                                .writer(resumenWriter)
                                .listener(new PreparacionCargaListener(jdbcTemplate,
                                                "resumen_transacciones_diarias", null))
                                .listener(simuladorFalloCritico)
                                .listener(metricasStepListener)
                                .build();
        }

        // ------------------------------------------------------------------
        // STEP 3 (EFT): verificacion de integridad
        // ------------------------------------------------------------------
        @Bean
        public Step verificarIntegridadTransaccionesStep(JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        JdbcTemplate jdbcTemplate,
                        @Value("${banco.data.directorio}") String directorio,
                        @Value("${banco.archivo.transacciones}") String archivo) {

                return new StepBuilder("verificarIntegridadTransaccionesStep", jobRepository)
                                .tasklet(new VerificacionIntegridadTasklet(jdbcTemplate, transactionManager,
                                                new ClassPathResource(directorio + "/" + archivo), archivo, TABLA),
                                                transactionManager)
                                .build();
        }

        // ------------------------------------------------------------------
        // JOB
        // ------------------------------------------------------------------
        @Bean
        public Job reporteTransaccionesDiariasJob(JobRepository jobRepository,
                        @Qualifier("cargarTransaccionesStep") Step cargarTransaccionesStep,
                        @Qualifier("resumenTransaccionesStep") Step resumenTransaccionesStep,
                        @Qualifier("verificarIntegridadTransaccionesStep") Step verificarIntegridadStep,
                        ContinuidadDecider continuidadDecider,
                        JobListener jobListener) {

                return new JobBuilder(NOMBRE_JOB, jobRepository)
                                .incrementer(new RunIdIncrementer())
                                .listener(jobListener)
                                .start(cargarTransaccionesStep)
                                .next(continuidadDecider)
                                .on(ContinuidadDecider.SIN_DATOS).fail()
                                .from(continuidadDecider)
                                .on("*").to(resumenTransaccionesStep)
                                .next(verificarIntegridadStep)
                                .end()
                                .build();
        }
}