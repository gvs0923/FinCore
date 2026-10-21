package com.fincore.checkout.api.admin;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuth;

    public AdminWebConfig(AdminAuthInterceptor adminAuth) {
        this.adminAuth = adminAuth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // The authz rule is ONE matcher on the prefix — any future admin
        // endpoint under /v1/admin/** is automatically covered. A public
        // route that skips this prefix is a bug reviewers can grep for.
        registry.addInterceptor(adminAuth).addPathPatterns("/v1/admin/**");
    }
}