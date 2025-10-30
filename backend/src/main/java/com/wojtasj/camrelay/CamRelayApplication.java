package com.wojtasj.camrelay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Cam Relay application, configuring Spring Boot and enabling JPA auditing, retry, and web support.
 * @since 1.0
 */
@EnableJpaAuditing
@EnableRetry
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@SpringBootApplication
public class CamRelayApplication {

	public static void main(String[] args) {
		SpringApplication.run(CamRelayApplication.class, args);
	}

}
