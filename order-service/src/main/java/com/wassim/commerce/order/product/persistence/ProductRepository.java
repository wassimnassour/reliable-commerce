package com.wassim.commerce.order.product.persistence;

import com.wassim.commerce.order.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

        @Modifying
        @Query(value = """
                           update products
                               set product_quantity= product_quantity- :quantity,
                                     version = version +1
                               where id = :productId and :quantity < product_quantity and status = true

                        """, nativeQuery = true)
        int decreaseTheStock(
                        @Param("quantity") int quantity,
                        @Param("productId") Long productId);
}
