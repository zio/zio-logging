package org.slf4j.zio;

import org.slf4j.ILoggerFactory;
import org.slf4j.Marker;
import org.slf4j.event.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LoggerFactory implements ILoggerFactory {

    private Map<String, Logger> loggers = new ConcurrentHashMap<String, Logger>();

    private LoggerRuntime runtime = null;

    public void attacheRuntime(LoggerRuntime runtime) {
        this.runtime  = runtime;
    }

    void log(String name, Level level, Marker marker, String messagePattern, Object[] arguments, Throwable throwable) {
        if(runtime != null) {
            runtime.log(name, level, marker, messagePattern, arguments, throwable);
        }
    }

    @Override
    public org.slf4j.Logger getLogger(String name) {
        return loggers.computeIfAbsent(name, n -> new Logger(n, this));
    }
}
