package com.platypus.crw.udp;

import com.platypus.crw.VehicleServer.DataType;
import com.platypus.crw.data.SensorData;
import com.platypus.crw.data.Twist;
import com.platypus.crw.data.Utm;
import com.platypus.crw.data.UtmPose;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import com.platypus.crw.data.Pose3D;

/**
 * Static helper class that contains constants and definitions required by the
 * UDP communications system.
 *
 * @author Pras Velagapudi <psigen@gmail.com>
 */
public class UdpConstants {

    public static final int REGISTRATION_RATE_MS = 1000;
    public static final int REGISTRATION_TIMEOUT_COUNT = 5;

    public static final long INITIAL_RETRY_RATE_NS = TimeUnit.NANOSECONDS.convert(200, TimeUnit.MILLISECONDS);
    public static final long RETRANSMISSION_DELAY_NS = TimeUnit.NANOSECONDS.convert(100, TimeUnit.MILLISECONDS);
    public static final int RETRY_COUNT = 4;
    public static final long TIMEOUT_NS = TimeUnit.NANOSECONDS.convert(10, TimeUnit.SECONDS);
    public static final long NO_TICKET = -1;
    public static final int TICKET_CACHE_SIZE = 100;

    public static final int INITIAL_PACKET_SIZE = 512;
    public static final int MAX_PACKET_SIZE = 4096;
    public static final int MAX_PAYLOAD_SIZE = 512;
    public static final String CMD_ACKNOWLEDGE = "OK";

    /**
     * Enumeration of tunneled commands and the strings used in the UDP packet
     * to represent them.
     */
    public enum COMMAND {
        UNKNOWN(""),
        CMD_REGISTER("HI"),
        CMD_LIST("HL"),
        CMD_CONNECT("CC"),
        CMD_REGISTER_POSE_LISTENER("RPL"),
        CMD_SEND_POSE("_P"),
        CMD_SET_POSE("SP"),
        CMD_GET_POSE("GP"),
        CMD_REGISTER_IMAGE_LISTENER("RIL"),
        CMD_SEND_IMAGE("_I"),
        CMD_CAPTURE_IMAGE("CI"),
        CMD_REGISTER_CAMERA_LISTENER("CIL"),
        CMD_SEND_CAMERA("_C"),
        CMD_START_CAMERA("STC"),
        CMD_STOP_CAMERA("SPC"),
        CMD_GET_CAMERA_STATUS("CS"),
        CMD_REGISTER_SENSOR_LISTENER("RSL"),
        CMD_SEND_SENSOR("_S"),
        CMD_ACK_SENSORDATA("ASD"),
        CMD_REGISTER_VELOCITY_LISTENER("RVL"),
        CMD_SEND_VELOCITY("_V"),
        CMD_SET_VELOCITY("SV"),
        CMD_GET_VELOCITY("GV"),
        CMD_REGISTER_WAYPOINT_LISTENER("RWL"),
        CMD_SEND_WAYPOINT("_W"),
        CMD_START_WAYPOINTS("STW"),
        CMD_STOP_WAYPOINTS("SPW"),
        CMD_GET_WAYPOINTS("GW"),
        CMD_GET_WAYPOINT_STATUS("GWS"),
        CMD_GET_WAYPOINTS_INDEX("GWI"),
        CMD_IS_CONNECTED("IC"),
        CMD_IS_AUTONOMOUS("IA"),
        CMD_SET_AUTONOMOUS("SA"),
        CMD_SET_GAINS("SG"),
        CMD_GET_GAINS("GG"),
        CMD_SET_HOME("SH"),
        CMD_GET_HOME("GH"),
        CMD_START_GO_HOME("SGH"),
        CMD_REGISTER_CRUMB_LISTENER("RCL"),
        CMD_SEND_CRUMB("_B"),
        CMD_ACK_CRUMB("AC"),
        CMD_REGISTER_RCOVER_LISTENER("RRC"),
        CMD_SEND_RCOVER("_RC"),
        CMD_REGISTER_KEYVALUE_LISTENER("KVL"),
        CMD_SEND_KEYVALUE("_KV"),
        CMD_SET_KEYVALUE("SKV"),
        CMD_GET_KEYVALUE("GKV"),
        CMD_NEW_AUTONOMOUS_PREDICATE_MSG("APM");

        COMMAND(String s) {
            str = s;
        }

        public final String str;
        
        static final TreeMap<String, COMMAND> _lookups = new TreeMap<String, COMMAND>();
        static {
            for (COMMAND cmd : COMMAND.values()) {
                _lookups.put(cmd.str, cmd);
            }
        }
        
        public static COMMAND fromStr(String str) {
            COMMAND result = _lookups.get(str);
            return (result == null) ? UNKNOWN : result;
        }
    }

    public static void writeTwist(DataOutputStream out, Twist twist) throws IOException {
        out.writeDouble(twist.dx());
        out.writeDouble(twist.dy());
        out.writeDouble(twist.dz());
        
        out.writeDouble(twist.drx());
        out.writeDouble(twist.dry());
        out.writeDouble(twist.drz());
    }
    
    public static Twist readTwist(DataInputStream in) throws IOException {
        double x = in.readDouble();
        double y = in.readDouble();
        double z = in.readDouble();

        double rx = in.readDouble();
        double ry = in.readDouble();
        double rz = in.readDouble();
        
        return new Twist(x,y,z,rx,ry,rz);
    }        
    
    public static void writeLatLng(DataOutputStream out, double[] latLng) throws IOException {
        out.writeDouble(latLng[0]);
        out.writeDouble(latLng[1]);
    }
    
    public static double[] readLatLng(DataInputStream in) throws IOException {
        double lat = in.readDouble();
        double lng = in.readDouble();
        double[] result = {lat, lng};
        return result; 
    }
    
    public static void writePose(DataOutputStream out, UtmPose utmPose) throws IOException {
        out.writeDouble(utmPose.pose.getX());
        out.writeDouble(utmPose.pose.getY());
        out.writeDouble(utmPose.pose.getZ());

        out.writeDouble(utmPose.pose.getRotation().getW());
        out.writeDouble(utmPose.pose.getRotation().getX());
        out.writeDouble(utmPose.pose.getRotation().getY());
        out.writeDouble(utmPose.pose.getRotation().getZ());

        out.writeByte(utmPose.origin.zone);
        out.writeBoolean(utmPose.origin.isNorth);
    }
    
    public static UtmPose readPose(DataInputStream in) throws IOException {
        double x = in.readDouble();
        double y = in.readDouble();
        double z = in.readDouble();

        double qw = in.readDouble();
        double qx = in.readDouble();
        double qy = in.readDouble();
        double qz = in.readDouble();
        
        int utmZone = in.readByte();
        boolean utmHemi = in.readBoolean();
        
        Pose3D pose = new Pose3D(x, y, z, qw, qx, qy, qz);
        Utm utm = new Utm(utmZone, utmHemi);
        
        return new UtmPose(pose, utm);
    }
 
    public static void writeSensorData(DataOutputStream out, SensorData sensor) throws IOException {
        out.writeInt(sensor.channel);
        out.writeByte(sensor.type.ordinal());
        out.writeDouble(sensor.value);
        out.writeDouble(sensor.latlng[0]);
        out.writeDouble(sensor.latlng[1]);
    }
    
    public static SensorData readSensorData(DataInputStream in) throws IOException {
        SensorData sensor = new SensorData();
        sensor.channel = in.readInt();
        int type_index = in.readUnsignedByte();
        sensor.type = DataType.values()[Math.max(type_index, 0)];
        sensor.value = in.readDouble();
        sensor.latlng = new double[]{in.readDouble(), in.readDouble()};
        return sensor;
    }
}
