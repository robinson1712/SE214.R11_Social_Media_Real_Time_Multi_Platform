package com.socialapp.reaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(scanBasePackages = {"com.socialapp.reaction", "com.socialapp.common"})
@EnableDiscoveryClient
@EnableKafka
public class ReactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactionServiceApplication.class, args);
    }
}
