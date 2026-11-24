package com.fincore.checkout.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityDeploymentChecksTest {

    @Test
    void productionRejectsDemoOrInsecureSettings() {
        assertThrows(IllegalStateException.class, () -> new SecurityDeploymentChecks(
                "http://localhost:5173", "https://idp.example/realm", "configured-secret", true));
        assertThrows(IllegalStateException.class, () -> new SecurityDeploymentChecks(
                "https://shop.example", "https://idp.example/realm", "dev-bff-secret", true));
        assertThrows(IllegalStateException.class, () -> new SecurityDeploymentChecks(
                "https://shop.example", "https://idp.example/realm", "configured-secret", false));
        assertDoesNotThrow(() -> new SecurityDeploymentChecks(
                "https://shop.example", "https://idp.example/realm", "configured-secret", true));
    }
}
