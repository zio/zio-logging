/*
 * Copyright 2019-2023 John A. De Goes and the ZIO Contributors
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

    abstract protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern, Object[] arguments, Throwable throwable);

    private void handle_0ArgsCall(Level level, Marker marker, String msg, Throwable t) {
        handleNormalizedLoggingCall(level, marker, msg, null, t);
    }

    private void handle_1ArgsCall(Level level, Marker marker, String msg, Object arg1) {
        handleNormalizedLoggingCall(level, marker, msg, new Object[]{arg1}, null);
    }

    private void handle2ArgsCall(Level level, Marker marker, String msg, Object arg1, Object arg2) {
        if (arg2 instanceof Throwable) {
            handleNormalizedLoggingCall(level, marker, msg, new Object[]{arg1}, (Throwable) arg2);
        } else {
            handleNormalizedLoggingCall(level, marker, msg, new Object[]{arg1, arg2}, null);
        }
    }

    private void handleArgArrayCall(Level level, Marker marker, String msg, Object[] args) {
        Throwable throwableCandidate = MessageFormatter.getThrowableCandidate(args);
        if (throwableCandidate != null) {
            Object[] trimmedCopy = MessageFormatter.trimmedCopy(args);
            handleNormalizedLoggingCall(level, marker, msg, trimmedCopy, throwableCandidate);
        } else {
            handleNormalizedLoggingCall(level, marker, msg, args, null);
        }
    }


    @Override
    public boolean isTraceEnabled() {
        return true;
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public void trace(String msg) {
        if (isTraceEnabled()) {
            handle_0ArgsCall(Level.TRACE, null, msg, null);
        }
    }

    @Override
    public void trace(String format, Object arg) {
        if (isTraceEnabled()) {
            handle_1ArgsCall(Level.TRACE, null, format, arg);
        }
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        if (isTraceEnabled()) {
            handle2ArgsCall(Level.TRACE, null, format, arg1, arg2);
        }
    }

    @Override
    public void trace(String format, Object... arguments) {
        if (isTraceEnabled()) {
            handleArgArrayCall(Level.TRACE, null, format, arguments);
        }
    }

    @Override
    public void trace(String msg, Throwable t) {
        if (isTraceEnabled()) {
            handle_0ArgsCall(Level.TRACE, null, msg, t);
        }
    }

    public void debug(String msg) {
        if (isDebugEnabled()) {
            handle_0ArgsCall(Level.DEBUG, null, msg, null);
        }
    }

    public void debug(String format, Object arg) {
        if (isDebugEnabled()) {
            handle_1ArgsCall(Level.DEBUG, null, format, arg);
        }
    }

    public void debug(String format, Object arg1, Object arg2) {
        if (isDebugEnabled()) {
            handle2ArgsCall(Level.DEBUG, null, format, arg1, arg2);
        }
    }

    public void debug(String format, Object... arguments) {
        if (isDebugEnabled()) {
            handleArgArrayCall(Level.DEBUG, null, format, arguments);
        }
    }

    public void debug(String msg, Throwable t) {
        if (isDebugEnabled()) {
            handle_0ArgsCall(Level.DEBUG, null, msg, t);
        }
    }


    public void info(String msg) {
        if (isInfoEnabled()) {
            handle_0ArgsCall(Level.INFO, null, msg, null);
        }
    }

    public void info(String format, Object arg) {
        if (isInfoEnabled()) {
            handle_1ArgsCall(Level.INFO, null, format, arg);
        }
    }

    public void info(String format, Object arg1, Object arg2) {
        if (isInfoEnabled()) {
            handle2ArgsCall(Level.INFO, null, format, arg1, arg2);
        }
    }

    public void info(String format, Object... arguments) {
        if (isInfoEnabled()) {
            handleArgArrayCall(Level.INFO, null, format, arguments);
        }
    }

    public void info(String msg, Throwable t) {
        if (isInfoEnabled()) {
            handle_0ArgsCall(Level.INFO, null, msg, t);
        }
    }

    public void warn(String msg) {
        if (isWarnEnabled()) {
            handle_0ArgsCall(Level.WARN, null, msg, null);
        }
    }

    public void warn(String format, Object arg) {
        if (isWarnEnabled()) {
            handle_1ArgsCall(Level.WARN, null, format, arg);
        }
    }

    public void warn(String format, Object arg1, Object arg2) {
        if (isWarnEnabled()) {
            handle2ArgsCall(Level.WARN, null, format, arg1, arg2);
        }
    }

    public void warn(String format, Object... arguments) {
        if (isWarnEnabled()) {
            handleArgArrayCall(Level.WARN, null, format, arguments);
        }
    }

    public void warn(String msg, Throwable t) {
        if (isWarnEnabled()) {
            handle_0ArgsCall(Level.WARN, null, msg, t);
        }
    }

    public void error(String msg) {
        if (isErrorEnabled()) {
            handle_0ArgsCall(Level.ERROR, null, msg, null);
        }
    }

    public void error(String format, Object arg) {
        if (isErrorEnabled()) {
            handle_1ArgsCall(Level.ERROR, null, format, arg);
        }
    }

    public void error(String format, Object arg1, Object arg2) {
        if (isErrorEnabled()) {
            handle2ArgsCall(Level.ERROR, null, format, arg1, arg2);
        }
    }

    public void error(String format, Object... arguments) {
        if (isErrorEnabled()) {
            handleArgArrayCall(Level.ERROR, null, format, arguments);
        }
    }

    public void error(String msg, Throwable t) {
        if (isErrorEnabled()) {
            handle_0ArgsCall(Level.ERROR, null, msg, t);
        }
    }
}