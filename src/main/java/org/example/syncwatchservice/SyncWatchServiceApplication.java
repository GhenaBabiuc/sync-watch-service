package org.example.syncwatchservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SyncWatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncWatchServiceApplication.class, args);
    }
}
