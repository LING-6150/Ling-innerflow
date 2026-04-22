package com.ling.linginnerflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LingInnerflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(LingInnerflowApplication.class, args);
    }

}
