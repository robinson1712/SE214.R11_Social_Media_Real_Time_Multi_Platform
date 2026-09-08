package com.socialapp.dating;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(scanBasePackages = {"com.socialapp.dating", "com.socialapp.common"})
@EnableDiscoveryClient
@EnableKafka
public class DatingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatingServiceApplication.class, args);
    }
}
