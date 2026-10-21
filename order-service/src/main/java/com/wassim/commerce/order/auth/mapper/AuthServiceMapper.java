package com.wassim.commerce.order.auth.mapper;

import com.wassim.commerce.order.auth.dto.request.RegisterRequestDto;
import com.wassim.commerce.order.auth.dto.response.AuthResponseDto;
import com.wassim.commerce.order.user.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AuthServiceMapper {

    void  createUserFromDto(
            @MappingTarget User user,
            RegisterRequestDto registerRequestDto
            );


    AuthResponseDto toAuthResponseDto(User user , String token , String refreshToken);
}
