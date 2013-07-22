package com.platypus.crw;

import com.platypus.crw.data.SensorData;
import java.util.EventListener;

public interface SensorListener extends EventListener {
	public void receivedSensor(SensorData sensor);
}
