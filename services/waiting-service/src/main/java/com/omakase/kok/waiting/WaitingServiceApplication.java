package com.omakase.kok.waiting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication(scanBasePackages = "com.omakase.kok")
public class WaitingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(WaitingServiceApplication.class, args);
	}

}
