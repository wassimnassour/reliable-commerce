package com.wassim.commerce.order.auth.mapper;

import com.wassim.commerce.order.auth.dto.request.RegisterRequestDto;
import com.wassim.commerce.order.auth.dto.response.AuthResponseDto;
import com.wassim.commerce.order.auth.dto.response.UserResponseDto;
import com.wassim.commerce.order.user.domain.Permission;
import com.wassim.commerce.order.user.domain.Role;
import com.wassim.commerce.order.user.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AuthServiceMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "authorities", ignore = true)
    void  createUserFromDto(
            @MappingTarget User user,
            RegisterRequestDto registerRequestDto
            );


    @Mapping(source = "user", target = "user")
    AuthResponseDto toAuthResponseDto(User user , String token , String refreshToken);

    @Mapping(source = "roles", target = "roles" , qualifiedByName = "roleNames")
    @Mapping(source = "roles" , target = "permissions" , qualifiedByName = "permissionsNames")
    UserResponseDto toUserResponseDto(User user);




    @Named("roleNames")
    default List<String> roleNames(List<Role> roles) {
     return roles.stream().map(Role::getName).toList();
    }

    @Named("permissionsNames")
    default List<String> permissionsNames(List<Role> roles) {
        return roles.stream().flatMap(
                role-> role.getPermissions().stream()
        ).map(Permission::getName).toList();
    }


}
