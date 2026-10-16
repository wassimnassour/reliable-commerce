package com.wassim.commerce.order.product.service.impl;


import com.wassim.commerce.order.exaptions.BadRequest;
import com.wassim.commerce.order.exaptions.NotFoundException;
import com.wassim.commerce.order.product.api.dto.request.CreateProductDto;
import com.wassim.commerce.order.product.api.dto.response.ProductResponse;
import com.wassim.commerce.order.product.domain.Product;
import com.wassim.commerce.order.product.mapper.ProductMapper;
import com.wassim.commerce.order.product.persistence.ProductRepository;
import com.wassim.commerce.order.product.service.ProductService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

     public ProductResponse createProduct(CreateProductDto productDto) {
        try{
            Product product = productMapper.toProduct(productDto);
            Product saved = productRepository.save(product);
            return productMapper.toResponse(saved);

        }catch (DataIntegrityViolationException e){
            throw new BadRequest("Product data violates a database constraint.");
        }

     }

    @Override
    public ProductResponse getProductById(Long id) {
         Product product= productRepository.findById(id).orElseThrow(
                 ()-> new NotFoundException("Can't find product with id " + id)
         );


        return productMapper.toResponse(product);
    }

}
