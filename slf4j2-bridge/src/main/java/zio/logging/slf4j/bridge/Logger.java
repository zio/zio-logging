/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio.logging.slf4j.bridge;

import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;
import org.slf4j.helpers.AbstractLogger;
import org.slf4j.spi.LoggingEventAware;

import java.util.Collections;

final class Logger extends AbstractLogger implements LoggingEventAware {

    private LoggerFactory factory;

    Logger(String name, LoggerFactory factory) {
        this.name = name;
        this.factory = factory;
    }

    @Override
    protected String getFullyQualifiedCallerName() {
        return null;
    }

    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern, Object[] arguments, Throwable throwable) {
        factory.log(name, level, messagePattern, arguments, throwable, Collections.emptyList());
    }

    @Override
    public boolean isTraceEnabled() {
        return factory.isEnabled(name, Level.TRACE);
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return isTraceEnabled();
    }

    @Override
    public boolean isDebugEnabled() {
        return factory.isEnabled(name, Level.DEBUG);
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return isDebugEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return factory.isEnabled(name, Level.INFO);
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return isInfoEnabled();
    }

    @Override
    public boolean isWarnEnabled() {
        return factory.isEnabled(name, Level.WARN);
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return isWarnEnabled();
    }

    @Override
    public boolean isErrorEnabled() {
        return factory.isEnabled(name, Level.ERROR);
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return isErrorEnabled();
    }

    @Override
    public void log(LoggingEvent event) {
        if (factory.isEnabled(name, event.getLevel())) {
            factory.log(name, event.getLevel(), event.getMessage(), event.getArgumentArray(), event.getThrowable(), event.getKeyValuePairs());
        }
    }
}
