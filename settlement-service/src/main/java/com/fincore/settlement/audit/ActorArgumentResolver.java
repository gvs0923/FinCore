package com.fincore.settlement.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Configuration
class ActorArgumentResolver implements HandlerMethodArgumentResolver, WebMvcConfigurer {

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._@:,-]{1,160}");

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Actor.class;
    }

    @Override
    public Actor resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                 NativeWebRequest request, WebDataBinderFactory binders) {
        String subject = request.getHeader(Actor.SUBJECT_HEADER);
        String name = request.getHeader(Actor.NAME_HEADER);
        String roles = Objects.requireNonNullElse(request.getHeader(Actor.ROLES_HEADER), "");
        boolean valid = subject != null && SAFE.matcher(subject).matches()
                && name != null && SAFE.matcher(name).matches()
                && (roles.isEmpty() || SAFE.matcher(roles).matches())
                // HTTP callers may never claim to be an internal job.
                && !subject.startsWith("system:");
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authenticated actor required");
        }
        return new Actor(subject, name, roles);
    }
}
