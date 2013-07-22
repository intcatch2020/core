package com.platypus.crw;

import com.platypus.crw.data.Twist;
import java.util.EventListener;

public interface VelocityListener extends EventListener {
	public void receivedVelocity(Twist velocity);
}