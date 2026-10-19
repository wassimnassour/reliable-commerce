package com.wassim.commerce.order.product.api;

import com.wassim.commerce.order.product.api.dto.request.CreateProductDto;
import com.wassim.commerce.order.product.api.dto.request.UpdateProductDto;
import com.wassim.commerce.order.product.api.dto.response.ProductResponse;
import com.wassim.commerce.order.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {
    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductDto productDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(productDto));
    }


    @PatchMapping("/{id}")
    public ResponseEntity<ProductResponse> updateMethod(@Valid @RequestBody UpdateProductDto updateProductDto, @PathVariable Long id) {
        ProductResponse productResponse = productService.updateProduct(id, updateProductDto);
        return ResponseEntity.ok(productResponse);
    }
}

