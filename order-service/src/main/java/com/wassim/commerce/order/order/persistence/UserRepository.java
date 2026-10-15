package com.wassim.commerce.order.order.persistence;
import com.wassim.commerce.order.order.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

}