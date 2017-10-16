package com.platypus.crw;

import com.platypus.crw.data.Twist;
import com.platypus.crw.data.UtmPose;


/**
 * Standard interface for controlling a vehicle.  Methods in this interface
 * are assumed to block until completion, or throw a runtime exception upon
 * failure.
 *
 * For asynchronous operation, see AsyncVehicleServer.
 *
 * @see AsyncVehicleServer
 *
 * @author Pras Velagapudi <psigen@gmail.com>
 */
public interface VehicleServer {

	//public enum SensorType { ANALOG, DIGITAL, ES2, HDS_DEPTH, HDS_TEMP, GPS,ATLAS_DO,ATLAS_PH, BATTERY, UNKNOWN };
        public enum DataType
        {
            UNKNOWN("?", "?"),
            EC_ATLAS("EC", "uS/cm"),
            EC_GOSYS("EC", "uS/cm"),
            T_ATLAS("T", "C"),
            T_GOSYS("T", "C"),
            DO_ATLAS("DO", "mg/L"),
            DO_GOSYS("DO", "mg/L"),
            PH_ATLAS("pH", ""),
            PH_GOSYS("pH", ""),
            BATTERY("battery", "V"),
            SALINITY("salinity", "g/L"),
            TURBIDITY("turbidity", "NTU"),
            REDOX("redox", "mV"),
            CHLOROPHYLLA("cholorphyll-a", "ug/L"),
            TOC("TOC", "mg/L"),
            NITRATE("nitrate", "ug/L"),
            NITRITE("nitrite", "ug/L");

            String units;
            String type;

            DataType(String _type, String _units)
            {
                type = _type;
                units = _units;
            }
            public String getUnits() { return units; }
            public String getType() { return type; }
        }
	public enum WaypointState { GOING, PAUSED, DONE, CANCELLED, OFF, UNKNOWN };
	public enum CameraState { CAPTURING, DONE, CANCELLED, OFF, UNKNOWN };

	public void addPoseListener(PoseListener l);
	public void removePoseListener(PoseListener l);
	public void setPose(UtmPose pose);
	public UtmPose getPose();
	
	public void addImageListener(ImageListener l);
	public void removeImageListener(ImageListener l);
	public byte[] captureImage(int width, int height);

	public void addCameraListener(CameraListener l);
	public void removeCameraListener(CameraListener l);
	public void startCamera(int numFrames, double interval, int width, int height);
	public void stopCamera();
	public CameraState getCameraStatus();
	
	public void addSensorListener(SensorListener l);
	public void removeSensorListener(SensorListener l);
	//public void setSensorType(int channel, SensorType type);
	//public SensorType getSensorType(int channel);
	//public int getNumSensors();
	
	public void addVelocityListener(VelocityListener l);
	public void removeVelocityListener(VelocityListener l);
	public void setVelocity(Twist velocity);
	public Twist getVelocity();

	public void addWaypointListener(WaypointListener l);
	public void removeWaypointListener(WaypointListener l);
        public void startWaypoints(double[][] waypoints);
	public void stopWaypoints();
        public double[][] getWaypoints();
	public WaypointState getWaypointStatus();
	public int getWaypointsIndex();

	public void addCrumbListener(CrumbListener l);
	public void removeCrumbListener(CrumbListener l);

	public boolean isConnected();
	public boolean isAutonomous();
	public void setAutonomous(boolean auto);
        
	public void setGains(int axis, double[] gains);
	public double[] getGains(int axis);

	public void setHome(double[] home);
	public double[] getHome();
	public void startGoHome();
        
        public void newAutonomousPredicateMessage(String apm);
}
