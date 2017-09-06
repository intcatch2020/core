package com.platypus.crw;

import java.util.EventListener;

public interface CrumbListener extends EventListener {
	public void receivedCrumb(double[] crumb, long index);
}
