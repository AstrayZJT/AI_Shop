package com.aishop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.aishop.config.RagProperties;
import com.aishop.config.ShopProperties;

@SpringBootApplication
@EnableConfigurationProperties({ShopProperties.class, RagProperties.class})
public class AiShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiShopApplication.class, args);
    }
}
