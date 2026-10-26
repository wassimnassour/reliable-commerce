package com.wassim.commerce.order.user.presistance;
import com.wassim.commerce.order.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
     Optional<User> findByEmail(String email);

     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("select u from User u where u.email = :email")
     Optional<User> findByEmailForUpdate(@Param("email") String email);
}