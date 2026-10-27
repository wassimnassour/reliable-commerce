package com.wassim.commerce.order.fixtures;

import com.wassim.commerce.order.user.domain.Role;

import java.util.List;

public class GenerateRoles {
    public static List<Role> generateRoles() {
        Role role =  new Role();
        role.setName("USER");
        return List.of(role);
    }
}
