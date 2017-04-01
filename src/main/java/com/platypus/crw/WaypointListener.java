package com.platypus.crw;

import com.platypus.crw.VehicleServer.WaypointState;

public interface WaypointListener {
	public void waypointUpdate(WaypointState status);
}
