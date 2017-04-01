package com.platypus.crw;

import java.util.EventListener;

public interface ImageListener extends EventListener {
	public void receivedImage(byte[] image);
}
