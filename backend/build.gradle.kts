plugins {
	java
	id("org.springframework.boot") version "3.5.7"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.wojtasj"
version = "0.0.1-SNAPSHOT"
description = "CamRelay"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("io.jsonwebtoken:jjwt-api:0.13.0")
	implementation("org.flywaydb:flyway-core:11.11.2")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.github.resilience4j:resilience4j-spring-boot3:2.3.0")
	implementation("org.springframework.retry:spring-retry:2.0.12")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13") {
		exclude(group = "com.vaadin.external.google", module = "android-json")
	}

	compileOnly("org.projectlombok:lombok")

	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
	runtimeOnly("org.flywaydb:flyway-database-postgresql:11.11.2")
	runtimeOnly("org.postgresql:postgresql")

	developmentOnly("org.springframework.boot:spring-boot-devtools")

	annotationProcessor("org.projectlombok:lombok")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
	testImplementation("org.assertj:assertj-core:3.26.3")
	testImplementation("org.mockito:mockito-core:5.19.0")
	testImplementation("org.mockito:mockito-junit-jupiter:5.19.0")
	testImplementation("com.h2database:h2:2.3.232")
	testImplementation("org.springframework.security:spring-security-crypto:7.0.0-M2")
	testImplementation("io.github.resilience4j:resilience4j-ratelimiter:2.3.0")

	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
