package com.socialapp.fanpage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {"com.socialapp.fanpage", "com.socialapp.common"})
@EnableDiscoveryClient
public class FanpageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FanpageServiceApplication.class, args);
    }
}
