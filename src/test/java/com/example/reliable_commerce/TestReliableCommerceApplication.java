package com.example.reliable_commerce;

import org.springframework.boot.SpringApplication;

public class TestReliableCommerceApplication {

	public static void main(String[] args) {
		SpringApplication.from(ReliableCommerceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
