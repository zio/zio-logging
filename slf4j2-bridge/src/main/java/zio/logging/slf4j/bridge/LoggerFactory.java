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

import org.slf4j.ILoggerFactory;
import org.slf4j.Marker;
import org.slf4j.event.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class LoggerFactory implements ILoggerFactory {

    private Map<String, Logger> loggers = new ConcurrentHashMap<String, Logger>();

    private LoggerRuntime runtime = null;

    void attachRuntime(LoggerRuntime runtime) {
        this.runtime = runtime;
    }

    void log(String name, Level level, Marker marker, String messagePattern, Object[] arguments, Throwable throwable) {
        if (runtime != null) {
            runtime.log(name, level, marker, messagePattern, arguments, throwable);
        }
    }

    @Override
    public org.slf4j.Logger getLogger(String name) {
        return loggers.computeIfAbsent(name, n -> new Logger(n, this));
    }
}
