package com.platypus.crw;

import java.util.EventListener;

public interface PointsOfInterestListener extends EventListener {
    public void receivedPOI(double[] point, long index, String desc, int map_marker_type);
}
