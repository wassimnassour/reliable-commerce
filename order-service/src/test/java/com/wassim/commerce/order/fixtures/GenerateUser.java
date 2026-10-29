package com.wassim.commerce.order.fixtures;

import com.wassim.commerce.order.user.domain.User;



public class   GenerateUser {


    public  static User generateUser(){


        User user = new User();
        user.setEmail(java.util.UUID.randomUUID() + "@example.com");
        user.setPassword("TestPassword");
        return user;
    }



}
