package com.xu.orderservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderServiceOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("High Throughput Order Service API")
                .version("0.1.0")
                .description("高傳輸量訂單處理系統的 REST API 文件"));
    }
}
