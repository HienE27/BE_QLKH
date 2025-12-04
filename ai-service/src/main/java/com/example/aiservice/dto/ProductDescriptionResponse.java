package com.example.aiservice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProductDescriptionResponse {
    private String shortDescription;
    private String seoDescription;
    private String longDescription;
    private List<String> attributes;
}


