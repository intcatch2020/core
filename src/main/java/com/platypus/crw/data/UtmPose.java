package com.platypus.crw.data;

import java.io.Serializable;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import org.jscience.geography.coordinates.LatLong;
import org.jscience.geography.coordinates.UTM;
import org.jscience.geography.coordinates.crs.ReferenceEllipsoid;


/**
 * Represents a location in 6D pose and UTM origin.
 * 
 * @author Prasanna Velagapudi <psigen@gmail.com>
 */
public class UtmPose implements Serializable, Cloneable {
 
    public static final Pose3D DEFAULT_POSE = new Pose3D(0.0,0.0,0.0,0.0,0.0,0.0);
    public static final Utm DEFAULT_ORIGIN = new Utm(17, true);
    
    // TODO: make this externalizable and cloneable
    public Pose3D pose;
    public Utm origin;
    
    public UtmPose() {
        this.pose = DEFAULT_POSE;
        this.origin = DEFAULT_ORIGIN;
    }
    
    public UtmPose(Pose3D pose, Utm origin) {
        this.pose = pose;
        this.origin = origin;
    }
    
    public UtmPose(double lat, double lng) {        
        UTM utm = UTM.latLongToUtm(LatLong.valueOf(lat, lng, NonSI.DEGREE_ANGLE), ReferenceEllipsoid.WGS84);
        pose = new Pose3D(utm.eastingValue(SI.METER), utm.northingValue(SI.METER), 0.0, Quaternion.fromEulerAngles(0, 0, 0));
        origin = new Utm(utm.longitudeZone(), utm.latitudeZone() > 'O');        
    }
    
    public UtmPose(double[] latlng) {
        this(latlng[0], latlng[1]);
    }
    
    public double[] getLatLong() {
        UTM utm = UTM.valueOf(origin.zone, origin.isNorth ? 'T' : 'L', pose.getX(), pose.getY(), SI.METER);
        LatLong latlng = UTM.utmToLatLong(utm, ReferenceEllipsoid.WGS84);
        double[] result = {latlng.latitudeValue(NonSI.DEGREE_ANGLE), latlng.longitudeValue(NonSI.DEGREE_ANGLE)};
        return result;
    }
    
    @Override
    public UtmPose clone() {
        return new UtmPose(pose, origin);
    }
    
    @Override
    public String toString() {
        return pose.toString() + " @ " + origin.toString();
    }
    
    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + (this.pose != null ? this.pose.hashCode() : 0);
        hash = 53 * hash + (this.origin != null ? this.origin.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final UtmPose other = (UtmPose) obj;
        if (this.pose != other.pose && (this.pose == null || !this.pose.equals(other.pose))) {
            return false;
        }
        if (this.origin != other.origin && (this.origin == null || !this.origin.equals(other.origin))) {
            return false;
        }
        return true;
    }
}
