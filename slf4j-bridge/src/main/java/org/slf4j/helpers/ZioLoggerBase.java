package org.slf4j.helpers;

public abstract class ZioLoggerBase extends MarkerIgnoringBase {

    public ZioLoggerBase(String name) {
        this.name = name;
    }
}