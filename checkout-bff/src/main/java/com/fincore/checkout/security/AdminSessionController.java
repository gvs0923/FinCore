package com.fincore.checkout.security;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AdminSessionController {

    public record AdminMe(String name, List<String> roles) {}

    @GetMapping("/v1/admin/me")
    public AdminMe me(Authentication authentication) {
        Actor actor = Actor.from(authentication);
        return new AdminMe(actor.displayName(), actor.roles());
    }
}