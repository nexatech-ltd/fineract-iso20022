package org.fineract.iso20022;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableRetry
@EnableAsync
public class FineractIso20022Application {

    public static void main(String[] args) {
        SpringApplication.run(FineractIso20022Application.class, args);
    }
}
