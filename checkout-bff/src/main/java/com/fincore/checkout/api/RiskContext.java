package com.fincore.checkout.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RiskContext {

    private static final Set<String> COUNTRIES =
            Set.of(Locale.getISOCountries());

    private RiskContext() {}

    public static Map<String, String> from(
            Map<String, String> supplied, Locale requestLocale) {

        Map<String, String> context = new HashMap<>();

        if (supplied != null) {
            if (supplied.size() > 3) {
                throw invalid();
            }

            for (var entry : supplied.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (!Set.of("timezone", "country", "language").contains(key)
                        || value == null
                        || value.isBlank()
                        || value.length() > 128) {
                    throw invalid();
                }

                if ("timezone".equals(key)) {
                    try {
                        context.put(key, ZoneId.of(value.trim()).getId());
                    } catch (DateTimeException e) {
                        throw invalid();
                    }
                } else if ("country".equals(key)) {
                    String country = value.trim().toUpperCase(Locale.ROOT);

                    if (!COUNTRIES.contains(country)) {
                        throw invalid();
                    }

                    context.put(key, country);
                }
            }
        }

        String language = requestLocale.toLanguageTag();

        if (!"und".equals(language)) {
            context.put("language", language);
        }

        return Map.copyOf(context);
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid risk context");
    }
}