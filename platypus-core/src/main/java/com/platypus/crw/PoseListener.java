package com.platypus.crw;

import com.platypus.crw.data.UtmPose;
import java.util.EventListener;

public interface PoseListener extends EventListener {
	public void receivedPose(UtmPose pose);
}
