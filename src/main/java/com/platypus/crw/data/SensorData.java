package com.platypus.crw.data;

import com.platypus.crw.VehicleServer.DataType;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Structure for holding sensor data.
 * 
 * @author Prasanna Velagapudi <psigen@gmail.com>
 */
public class SensorData implements Cloneable, Serializable  {
    public int channel;
    public DataType type;
    public double value;
    public double[] latlng;
    
    @Override
    public String toString() {
        return "Sensor " + channel + ", " + type.getType() + 
                ", @[" + latlng[0] + "," +  latlng[1] + "] = " 
                + value + " " + type.getUnits();
    }
    
    public int key() {
        // simple hashkey that can be used to identify what sensors are 
        //      connected to a boat. Only channel and type matter for this.        
        int hash = 3;
        hash = 97 * hash + this.channel;
        hash = 97 * hash + this.type.hashCode();
        return hash;
    }
    
    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + this.channel;
        hash = 97 * hash + Double.hashCode(this.value);
        hash = 97 * hash + (this.type != null ? this.type.hashCode() : 0);
        return hash;
    }

    public boolean isSameProbe(SensorData other) {
        if (this.channel != other.channel) {
            return false;
        }
        if (!this.type.getType().equals(other.type.getType())) {
            return false;
        }        
        return true;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SensorData other = (SensorData) obj;
        if (!Arrays.equals(this.latlng, other.latlng)) {
            return false;
        }
        if (value != other.value) {
            return false;
        }
        if (!this.type.getType().equals(other.type.getType())) {
            return false;
        }
        if (this.channel != other.channel) {
            return false;
        }        
        return true;
    }    
}
