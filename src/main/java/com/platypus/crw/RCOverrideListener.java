package com.platypus.crw;

import java.util.EventListener;

public interface RCOverrideListener extends EventListener {
    public void rcOverrideUpdate(boolean isRCOverrideOn);
}
