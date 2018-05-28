package ru.makkarpov.mtoxy.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class ColoredLevel extends ClassicConverter {
    private String color(String code, String s) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 5 - s.length(); i++)
            sb.append(" ");

        sb.append("\033[").append(code).append("m");
        sb.append("[").append(s).append("]");
        sb.append("\033[0m");

        return sb.toString();
    }

    private String loggingPrefix(Level level) {
        if (level == Level.TRACE) {
            return color("34", "trace");
        } else if (level == Level.DEBUG) {
            return color("36", "debug");
        } else if (level == Level.INFO) {
            return color("37", "info");
        } else if (level == Level.WARN) {
            return color("1;33", "warn");
        } else if (level == Level.ERROR) {
            return color("1;31", "error");
        } else {
            return "???";
        }
    }

    @Override
    public String convert(ILoggingEvent event) {
        return loggingPrefix(event.getLevel());
    }
}
