package com.fincore.checkout;

import com.fincore.checkout.config.TestKafkaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestKafkaConfig.class)
class CheckoutBffApplicationTests {

    @Test
    void contextLoads() {
    }
}
