package org.slf4j.zio;

import org.slf4j.Marker;
import org.slf4j.event.Level;

public interface LoggerRuntime {
    void log(String name, Level level, Marker marker, String messagePattern, Object[] arguments, Throwable throwable);
}
