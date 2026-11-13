    package com.wassim.commerce.order.order.presisstance;

    import com.wassim.commerce.order.order.domain.Order;
    import jakarta.persistence.LockModeType;
    import org.springframework.data.jpa.repository.JpaRepository;
    import org.springframework.data.jpa.repository.Lock;
    import org.springframework.data.jpa.repository.Query;
    import org.springframework.data.repository.query.Param;
    import org.springframework.stereotype.Repository;

    import java.util.Optional;

    @Repository
    public interface OrderRepository extends JpaRepository<Order,Long> {

        @Query(
                """
           select  o from Order o where
                  o.id=:orderId and o.user.id=:userId
           """
        )
        Optional<Order> findToProcess(@Param("orderId") Long orderId , @Param("userId") Long userId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select o from Order o where o.id = :orderId")
        Optional<Order> findAndLockById(@Param("orderId") Long orderId);

        @Query(
                value = """
                select o.* from  orders o where o.id= (
                            select p.order_id from payments p where p.id =:paymentId
                        )
                FOR UPDATE OF o
              """
                ,
                nativeQuery = true
        )
        Optional<Order> findAndLockByPaymentId(@Param("paymentId") Long paymentId);
    }
