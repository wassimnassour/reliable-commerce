package com.wassim.commerce.order.product.service.impl;


import com.wassim.commerce.order.exaptions.BadRequest;
import com.wassim.commerce.order.exaptions.NotFoundException;
import com.wassim.commerce.order.product.api.dto.request.CreateProductDto;
import com.wassim.commerce.order.product.api.dto.request.ProductFilter;
import com.wassim.commerce.order.product.api.dto.request.UpdateProductDto;
import com.wassim.commerce.order.product.api.dto.response.ProductResponse;
import com.wassim.commerce.order.product.domain.Product;
import com.wassim.commerce.order.product.mapper.ProductMapper;
import com.wassim.commerce.order.product.persistence.ProductRepository;
import com.wassim.commerce.order.product.persistence.specification.ProductSpecification;
import com.wassim.commerce.order.product.service.ProductService;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.wassim.commerce.order.product.api.dto.response.ProductSliceResponse;

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



    @Override
    public ProductSliceResponse getAllProducts(ProductFilter productFilter) {
        Specification<Product> specification =  Specification.where(
                ProductSpecification.productNameLike(productFilter.name())
        ).and(
                ProductSpecification.productPriceAtLeast(productFilter.minPrice())
        ).and(
                ProductSpecification.productPriceAtMost(productFilter.maxPrice())
        );
        String[] sortParts = productFilter.sortBy().split(",");
        String property = switch (sortParts[0]) {
            case "name" -> "productName";
            case "price" -> "productPrice";
            default -> "id";
        };
        Sort.Direction direction = sortParts.length == 2
                ? Sort.Direction.fromString(sortParts[1]) : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, property);
        if (!property.equals("id")) {
            sort = sort.and(Sort.by("id"));
        }
        PageRequest pageRequest = PageRequest.of(productFilter.page(), productFilter.size(), sort);
        var slice = productRepository.findBy(specification, query -> query.slice(pageRequest));
        return new ProductSliceResponse(slice.getContent().stream().map(productMapper::toResponse).toList(),
                slice.getNumber(), slice.getSize(), slice.hasNext());
    }

    @Override
    @Transactional
    public  ProductResponse updateProduct(Long id , UpdateProductDto updateProductDto){
        if (updateProductDto.name() != null && updateProductDto.name().isBlank()) {
            throw new BadRequest("Product name cannot be blank");
        }

        Product product =  productRepository.findById(id).orElseThrow(
                ()-> new NotFoundException("Can't find product with id " + id)
        );

        productMapper.updateProduct(product, updateProductDto);
        return productMapper.toResponse(product);
    }
}
