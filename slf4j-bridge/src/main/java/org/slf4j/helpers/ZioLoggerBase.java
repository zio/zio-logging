/*
 * Copyright 2019-2025 John A. De Goes and the ZIO Contributors
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
package org.slf4j.helpers;

import org.slf4j.Marker;
import org.slf4j.event.Level;

public abstract class ZioLoggerBase extends MarkerIgnoringBase {

    public ZioLoggerBase(String name) {
        this.name = name;
    }

    abstract protected void log(Level level, Marker marker, String messagePattern, Object[] arguments, Throwable throwable);

    abstract protected boolean isEnabled(Level level);

    private void logWithThrowable(Level level, Marker marker, String msg, Throwable t) {
        log(level, marker, msg, null, t);
    }

    private void logWithArg(Level level, Marker marker, String msg, Object arg1) {
        log(level, marker, msg, new Object[]{arg1}, null);
    }

    private void logWithArgs(Level level, Marker marker, String msg, Object arg1, Object arg2) {
        if (arg2 instanceof Throwable) {
            log(level, marker, msg, new Object[]{arg1}, (Throwable) arg2);
        } else {
            log(level, marker, msg, new Object[]{arg1, arg2}, null);
        }
    }

    private void logWithArgs(Level level, Marker marker, String msg, Object[] args) {
        Throwable throwableCandidate = MessageFormatter.getThrowableCandidate(args);
        if (throwableCandidate != null) {
            Object[] trimmedCopy = MessageFormatter.trimmedCopy(args);
            log(level, marker, msg, trimmedCopy, throwableCandidate);
        } else {
            log(level, marker, msg, args, null);
        }
    }

    @Override
    public boolean isTraceEnabled() {
        return isEnabled(Level.TRACE);
    }

    @Override
    public boolean isDebugEnabled() {
        return isEnabled(Level.DEBUG);
    }

    @Override
    public boolean isErrorEnabled() {
        return isEnabled(Level.ERROR);
    }

    @Override
    public boolean isWarnEnabled() {
        return isEnabled(Level.WARN);
    }

    @Override
    public boolean isInfoEnabled() {
        return isEnabled(Level.INFO);
    }

    @Override
    public void trace(String msg) {
        if (isTraceEnabled()) {
            logWithThrowable(Level.TRACE, null, msg, null);
        }
    }

    @Override
    public void trace(String format, Object arg) {
        if (isTraceEnabled()) {
            logWithArg(Level.TRACE, null, format, arg);
        }
    }


    @Override
    public void trace(String format, Object arg1, Object arg2) {
        if (isTraceEnabled()) {
            logWithArgs(Level.TRACE, null, format, arg1, arg2);
        }
    }


    @Override
    public void trace(String format, Object... arguments) {
        if (isTraceEnabled()) {
            logWithArgs(Level.TRACE, null, format, arguments);
        }
    }


    @Override
    public void trace(String msg, Throwable t) {
        if (isTraceEnabled()) {
            logWithThrowable(Level.TRACE, null, msg, t);
        }
    }


    public void debug(String msg) {
        if (isDebugEnabled()) {
            logWithThrowable(Level.DEBUG, null, msg, null);
        }
    }


    public void debug(String format, Object arg) {
        if (isDebugEnabled()) {
            logWithArg(Level.DEBUG, null, format, arg);
        }
    }


    public void debug(String format, Object arg1, Object arg2) {
        if (isDebugEnabled()) {
            logWithArgs(Level.DEBUG, null, format, arg1, arg2);
        }
    }


    public void debug(String format, Object... arguments) {
        if (isDebugEnabled()) {
            logWithArgs(Level.DEBUG, null, format, arguments);
        }
    }


    public void debug(String msg, Throwable t) {
        if (isDebugEnabled()) {
            logWithThrowable(Level.DEBUG, null, msg, t);
        }
    }


    public void info(String msg) {
        if (isInfoEnabled()) {
            logWithThrowable(Level.INFO, null, msg, null);
        }
    }

    public void info(String format, Object arg) {
        if (isInfoEnabled()) {
            logWithArg(Level.INFO, null, format, arg);
        }
    }

    public void info(String format, Object arg1, Object arg2) {
        if (isInfoEnabled()) {
            logWithArgs(Level.INFO, null, format, arg1, arg2);
        }
    }

    public void info(String format, Object... arguments) {
        if (isInfoEnabled()) {
            logWithArgs(Level.INFO, null, format, arguments);
        }
    }

    public void info(String msg, Throwable t) {
        if (isInfoEnabled()) {
            logWithThrowable(Level.INFO, null, msg, t);
        }
    }

    public void warn(String msg) {
        if (isWarnEnabled()) {
            logWithThrowable(Level.WARN, null, msg, null);
        }
    }

    public void warn(String format, Object arg) {
        if (isWarnEnabled()) {
            logWithArg(Level.WARN, null, format, arg);
        }
    }

    public void warn(String format, Object arg1, Object arg2) {
        if (isWarnEnabled()) {
            logWithArgs(Level.WARN, null, format, arg1, arg2);
        }
    }

    public void warn(String format, Object... arguments) {
        if (isWarnEnabled()) {
            logWithArgs(Level.WARN, null, format, arguments);
        }
    }

    public void warn(String msg, Throwable t) {
        if (isWarnEnabled()) {
            logWithThrowable(Level.WARN, null, msg, t);
        }
    }

    public void error(String msg) {
        if (isErrorEnabled()) {
            logWithThrowable(Level.ERROR, null, msg, null);
        }
    }

    public void error(String format, Object arg) {
        if (isErrorEnabled()) {
            logWithArg(Level.ERROR, null, format, arg);
        }
    }

    public void error(String format, Object arg1, Object arg2) {
        if (isErrorEnabled()) {
            logWithArgs(Level.ERROR, null, format, arg1, arg2);
        }
    }

    public void error(String format, Object... arguments) {
        if (isErrorEnabled()) {
            logWithArgs(Level.ERROR, null, format, arguments);
        }
    }

    public void error(String msg, Throwable t) {
        if (isErrorEnabled()) {
            logWithThrowable(Level.ERROR, null, msg, t);
        }
    }
}