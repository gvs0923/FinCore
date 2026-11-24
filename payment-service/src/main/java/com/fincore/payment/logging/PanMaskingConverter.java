package com.fincore.payment.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Pattern;

public class PanMaskingConverter extends MessageConverter {
    private static final Pattern PAN_SHAPE = Pattern.compile("\\b(?:\\d[ -]?){12,18}\\d\\b");

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        return message == null ? null : PAN_SHAPE.matcher(message).replaceAll(m -> mask(m.group()));
    }

    static String mask(String candidate) {
        String digits = candidate.replaceAll("\\D", "");
        if (digits.length() < 13 || digits.length() > 19 || !luhnValid(digits)) return candidate;
        return "[PAN ****" + digits.substring(digits.length() - 4) + "]";
    }

    private static boolean luhnValid(String digits) {
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl && (d *= 2) > 9) d -= 9;
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }
}
