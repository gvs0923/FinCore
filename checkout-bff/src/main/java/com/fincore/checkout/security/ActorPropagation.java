package com.fincore.checkout.security;

import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ActorPropagation {

    @Bean
    RestClientCustomizer actorHeaders() {
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            Actor.current().ifPresent(actor -> {
                request.getHeaders().set(Actor.SUBJECT_HEADER, actor.subject());
                request.getHeaders().set(Actor.NAME_HEADER, actor.displayName());
                request.getHeaders().set(Actor.ROLES_HEADER, String.join(",", actor.roles()));
            });
            return execution.execute(request, body);
        });
    }
}
