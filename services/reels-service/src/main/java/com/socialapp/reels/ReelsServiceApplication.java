package com.socialapp.reels;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(scanBasePackages = {"com.socialapp.reels", "com.socialapp.common"})
@EnableDiscoveryClient
@EnableKafka
public class ReelsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReelsServiceApplication.class, args);
    }
}
