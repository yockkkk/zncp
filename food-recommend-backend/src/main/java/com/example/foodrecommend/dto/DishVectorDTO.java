package com.example.foodrecommend.dto;

import lombok.Data;

import java.util.List;

@Data
public class DishVectorDTO {
    private Long dishId;
    private String name;
    private String category;
    private double price;
    private int calories;
    private double protein;
    private String taste;
    private List<String> tags;
    private List<String> suitablePeople;
    private List<String> scene;
    private double grossMargin;
}
