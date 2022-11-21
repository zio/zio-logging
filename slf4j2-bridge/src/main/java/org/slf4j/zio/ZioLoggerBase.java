package org.slf4j.zio;

import org.slf4j.helpers.MarkerIgnoringBase;

public abstract class ZioLoggerBase extends MarkerIgnoringBase {

    public ZioLoggerBase(String name) {
        this.name = name;
    }
}