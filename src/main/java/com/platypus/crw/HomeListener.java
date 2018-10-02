package com.platypus.crw;

import java.util.EventListener;

public interface HomeListener extends EventListener {
    public void receivedHome(double[] home);
}
