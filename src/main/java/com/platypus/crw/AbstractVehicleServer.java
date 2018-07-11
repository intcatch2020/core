package com.platypus.crw;

import com.platypus.crw.data.SensorData;
import com.platypus.crw.data.Twist;
import com.platypus.crw.data.UtmPose;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

public abstract class AbstractVehicleServer implements VehicleServer {

    protected double[][] _gains = new double[6][3];
    protected final List<SensorListener> _sensorListeners = new ArrayList<SensorListener>();
    protected final List<ImageListener> _imageListeners = new ArrayList<ImageListener>();
    protected final List<VelocityListener> _velocityListeners = new ArrayList<VelocityListener>();
    protected final List<PoseListener> _stateListeners = new ArrayList<PoseListener>();
    protected final List<CameraListener> _cameraListeners = new ArrayList<CameraListener>();
    protected final List<WaypointListener> _waypointListeners = new ArrayList<WaypointListener>();
    protected final List<CrumbListener> _crumbListeners = new ArrayList<CrumbListener>();
    protected final List<RCOverrideListener> _rcListeners = new ArrayList<RCOverrideListener>();

    @Override
    public double[] getGains(int axis) {
        if (axis < 0 || axis >= _gains.length) {
            return new double[0];
        }

        // Make a copy of the current state (for immutability) and return it
        double[] gains = new double[_gains[axis].length];
        System.arraycopy(_gains[axis], 0, gains, 0, _gains[axis].length);
        return gains;
    }

    @Override
    public void setGains(int axis, double[] gains) {
        if (axis < 0 || axis >= _gains.length) {
            return;
        }

        // Make a copy of the provided state (for immutability)
        System.arraycopy(gains, 0, _gains[axis], 0, Math.min(gains.length, _gains[axis].length));
    }

    @Override
    public void addPoseListener(PoseListener l) {
        synchronized (_stateListeners) {
            _stateListeners.add(l);
        }
    }

    @Override
    public void removePoseListener(PoseListener l) {
        synchronized (_stateListeners) {
            _stateListeners.remove(l);
        }
    }

    @Override
    public void addCrumbListener(CrumbListener l) {
        synchronized (_crumbListeners) {
            _crumbListeners.add(l);
        }
    }

    @Override
    public void removeCrumbListener(CrumbListener l) {
        synchronized (_crumbListeners) {
            _crumbListeners.remove(l);
        }
    }
    
    @Override
    public void addRCOverrideListener(RCOverrideListener l) {
        synchronized (_rcListeners) {
            _rcListeners.add(l);
        }
    }
    
    @Override
    public void removeRCOverrideListener(RCOverrideListener l) {
        synchronized (_rcListeners) {
            _rcListeners.remove(l);
        }
    }    

    protected void sendState(UtmPose pose) {
        // Process the listeners last to first, notifying
        // those that are interested in this event
        synchronized (_stateListeners) {
            for (PoseListener l : _stateListeners) {
                l.receivedPose(pose);
            }
        }
    }

    protected void sendCrumb(double[] crumb, long index) {
        synchronized (_crumbListeners) {
            for (CrumbListener l : _crumbListeners) {
                l.receivedCrumb(crumb, index);
            }
        }
    }
    
    protected void sendRCOverride(boolean isRCOverrideOn) {
        synchronized(_rcListeners) {
            for (RCOverrideListener l: _rcListeners) {
                l.rcOverrideUpdate(isRCOverrideOn);
            }
        }
    }

    @Override
    public void addImageListener(ImageListener l) {
        synchronized (_imageListeners) {
            _imageListeners.add(l);
        }
    }

    @Override
    public void removeImageListener(ImageListener l) {
        synchronized (_imageListeners) {
            _imageListeners.remove(l);
        }
    }

    protected static byte[] toCompressedImage(RenderedImage image) {
        // This might be inefficient, but it is far more inefficient to
        // uncompress hardware-compressed JPEG images on Android.
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "jpeg", buffer);
            return buffer.toByteArray();
        } catch (IOException ex) {
            return null;
        }
    }

    protected void sendImage(byte[] image) {
        synchronized (_imageListeners) {
            for (ImageListener l : _imageListeners) {
                l.receivedImage(image);
            }
        }
    }

    @Override
    //public void addSensorListener(int channel, SensorListener l) {
    public void addSensorListener(SensorListener l) {
        synchronized (_sensorListeners) {
            _sensorListeners.add(l);
        }
    }

    @Override
    public void removeSensorListener(SensorListener l) {
        synchronized (_sensorListeners) {
            _sensorListeners.remove(l);
        }
    }

    protected void sendSensor(SensorData reading, long index) {
        synchronized (_sensorListeners) {
            for (SensorListener l : _sensorListeners) {
                l.receivedSensor(reading, index);
            }
        }
    }

    @Override
    public void addVelocityListener(VelocityListener l) {
        synchronized (_velocityListeners) {
            _velocityListeners.add(l);
        }
    }

    @Override
    public void removeVelocityListener(VelocityListener l) {
        synchronized (_velocityListeners) {
            _velocityListeners.remove(l);
        }
    }

    protected void sendVelocity(Twist velocity) {
        synchronized (_velocityListeners) {
            for (VelocityListener l : _velocityListeners) {
                l.receivedVelocity(velocity);
            }
        }
    }

    @Override
    public void addCameraListener(CameraListener l) {
        synchronized (_cameraListeners) {
            _cameraListeners.add(l);
        }
    }

    @Override
    public void removeCameraListener(CameraListener l) {
        synchronized (_cameraListeners) {
            _cameraListeners.remove(l);
        }
    }
    
    protected void sendCameraUpdate(CameraState status) {
        // Process the listeners last to first, notifying
        // those that are interested in this event
        synchronized (_cameraListeners) {
            for (CameraListener l : _cameraListeners) {
                l.imagingUpdate(status);
            }
        }
    }

    @Override
    public void addWaypointListener(WaypointListener l) {
        synchronized (_waypointListeners) {
            _waypointListeners.add(l);
        }
    }

    @Override
    public void removeWaypointListener(WaypointListener l) {
        synchronized (_waypointListeners) {
            _waypointListeners.remove(l);
        }
    }
    
    protected void sendWaypointUpdate(WaypointState status) {
        // Process the listeners last to first, notifying
        // those that are interested in this event
        synchronized (_waypointListeners) {
            for (WaypointListener l : _waypointListeners) {
                l.waypointUpdate(status);
            }
        }
    }
}
