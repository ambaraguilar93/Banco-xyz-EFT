package com.duoc.bancoxyz.pagos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MsPagosApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsPagosApplication.class, args);
    }
}
