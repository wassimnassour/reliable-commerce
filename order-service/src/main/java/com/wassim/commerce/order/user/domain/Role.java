package com.wassim.commerce.order.user.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.mapstruct.Named;

import java.util.List;

@Data
@Entity(name = "roles")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private  String name;


    @ManyToMany(
          fetch = FetchType.EAGER
    )
    @JoinTable(
            name = "roles_permission",
            joinColumns = @JoinColumn(
                    name = "role_id"
            ),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private List<Permission> permissions;


}
