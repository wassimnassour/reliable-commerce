package com.wassim.commerce.order.product.api;

import com.wassim.commerce.order.product.api.dto.request.ProductFilter;
import com.wassim.commerce.order.product.api.dto.response.ProductResponse;
import com.wassim.commerce.order.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.wassim.commerce.order.product.api.dto.response.ProductSliceResponse;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductDetails(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping
    public ResponseEntity<ProductSliceResponse> getAllProducts(
            @Valid @ModelAttribute ProductFilter productFilter
    ) {

        ProductSliceResponse products =productService.getAllProducts(productFilter);
        return ResponseEntity.ok(products);
    }
}
