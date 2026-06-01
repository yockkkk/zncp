package com.example.foodrecommend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.foodrecommend.mapper")
public class FoodRecommendApplication {
    public static void main(String[] args) {
        SpringApplication.run(FoodRecommendApplication.class, args);
    }
}
