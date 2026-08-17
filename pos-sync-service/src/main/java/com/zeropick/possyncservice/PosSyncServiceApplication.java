package com.zeropick.possyncservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class PosSyncServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PosSyncServiceApplication.class, args);
    }
}
