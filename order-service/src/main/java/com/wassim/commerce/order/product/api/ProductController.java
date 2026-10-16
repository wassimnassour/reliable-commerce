package com.wassim.commerce.order.product.api;

import com.wassim.commerce.order.product.api.dto.request.CreateProductDto;
import com.wassim.commerce.order.product.api.dto.response.CreateProductResponse;
import com.wassim.commerce.order.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }


    @PostMapping
    public ResponseEntity<CreateProductResponse> create(@Valid @RequestBody CreateProductDto productDto) {
        CreateProductResponse product = productService.createProduct(productDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

}
