package com.platypus.crw;

import java.util.EventListener;

public interface KeyValueListener extends EventListener {
    public void keyValueUpdate(String key, float value);
}
