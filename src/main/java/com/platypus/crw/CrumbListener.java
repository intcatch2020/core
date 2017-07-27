package com.platypus.crw;

import com.platypus.crw.data.UtmPose;
import java.util.EventListener;

public interface CrumbListener extends EventListener {
	public void receivedCrumb(UtmPose crumb);
}
