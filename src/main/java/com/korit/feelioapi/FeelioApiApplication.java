package com.korit.feelioapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class FeelioApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeelioApiApplication.class, args);
    }

}
