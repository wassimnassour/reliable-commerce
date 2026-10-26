package com.wassim.commerce.order.auth.presistance;


import com.wassim.commerce.order.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

     @Query("SELECT rt FROM refresh_token rt WHERE rt.jti = :jti")
     Optional<RefreshToken> findByJti(@Param("jti") String jti);


     @Query("SELECT rt.sub FROM refresh_token rt WHERE rt.jti = :jti")
     Optional<String> findSubByJti(@Param("jti") String jti);

     void deleteAllBySub(String sub);
}