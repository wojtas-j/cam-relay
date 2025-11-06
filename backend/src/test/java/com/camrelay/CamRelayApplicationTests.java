package com.camrelay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Tests the loading of the application context in the Cam Relay application.
 * @since 1.0
 */
@SpringBootTest
@ActiveProfiles("test")
class CamRelayApplicationTests {
	/**
	 * Verifies that the application context loads successfully.
	 * @since 1.0
	 */
	@Test
	void contextLoads() {
	}
}
