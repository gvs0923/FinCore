package com.fincore.checkout;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CheckoutBffApplicationTests {

    @Test
    void contextLoads() {
        // If we get here, Spring Boot started successfully:
        // - All beans wired (controller, service, client)
        // - CORS config loaded
        // - No database needed — BFF is stateless
    }
}
