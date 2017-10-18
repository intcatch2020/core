package com.platypus.crw.udp;

import com.platypus.crw.AsyncVehicleServer;
import com.platypus.crw.CameraListener;
import com.platypus.crw.FunctionObserver;
import com.platypus.crw.ImageListener;
import com.platypus.crw.PoseListener;
import com.platypus.crw.SensorListener;
import com.platypus.crw.CrumbListener;
import com.platypus.crw.VehicleServer.CameraState;
import com.platypus.crw.VehicleServer.DataType;
import com.platypus.crw.VehicleServer.WaypointState;
import com.platypus.crw.VelocityListener;
import com.platypus.crw.WaypointListener;
import com.platypus.crw.data.SensorData;
import com.platypus.crw.data.Twist;
import com.platypus.crw.data.UtmPose;
import com.platypus.crw.udp.UdpServer.Request;
import com.platypus.crw.udp.UdpServer.Response;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A vehicle server proxy that uses UDP to connect to the vehicle and forwards
 * functions calls and events.
 *
 * Implementation details:
 *
 * For stream communication, the server transmits a heartbeat packet
 * indicating that it should be notified for a given event stream.  If this
 * packet is lost, notifications will cease to be sent.
 *
 * For two-way calls, the server sends a packet to the server and puts the
 * future in a blocking queue and a hash map.  If the server gets
 * a response, the future's result is filled in and completed.  If not, the
 * future is taken out of the blocking queue and removed.
 * 
 * @author Prasanna Velagapudi <psigen@gmail.com>
 */
public class UdpVehicleServer implements AsyncVehicleServer, UdpServer.RequestHandler {
    private static final Logger logger = Logger.getLogger(UdpVehicleService.class.getName());

    protected final UdpServer _udpServer;
    protected SocketAddress _vehicleServer;
    protected SocketAddress _registryServer;

    final Timer _timer = new Timer(true);
    
     // Start ticket with random offset to prevent collisions across multiple clients
    final AtomicLong _ticketCounter = new AtomicLong(new Random().nextLong() << 32);
    final TimeoutMap _ticketMap = new TimeoutMap();
    
    final Object _imageReassemblyLock = new Object();
    int _imageReassemblyTicket;
    byte[][] _imageReassemblyTable;
    
    //protected final Map<Integer, List<SensorListener>> _sensorListeners = new TreeMap<Integer, List<SensorListener>>();
    protected final List<SensorListener> _sensorListeners = new ArrayList<SensorListener>();
    protected final List<ImageListener> _imageListeners = new ArrayList<ImageListener>();
    protected final List<VelocityListener> _velocityListeners = new ArrayList<VelocityListener>();
    protected final List<PoseListener> _poseListeners = new ArrayList<PoseListener>();
    protected final List<CameraListener> _cameraListeners = new ArrayList<CameraListener>();
    protected final List<WaypointListener> _waypointListeners = new ArrayList<WaypointListener>();
    protected final List<CrumbListener> _crumbListeners = new ArrayList<CrumbListener>();

    public UdpVehicleServer() {
        // Create a UDP server that will handle RPC
        _udpServer = new UdpServer();
        _udpServer.setHandler(this);
        _udpServer.start();
        
        // Start a task to periodically register for stream updates
        _timer.scheduleAtFixedRate(new RegistrationTask(), 0, UdpConstants.REGISTRATION_RATE_MS);
    }
    
    public UdpVehicleServer(SocketAddress addr) {
        this();
        _vehicleServer = addr;
    }
    
    public void shutdown() {
        _timer.cancel();
        _timer.purge();
        _ticketMap.shutdown();
        _udpServer.stop();
    }
    
    public void setVehicleService(SocketAddress addr) {
        _vehicleServer = addr;
        
        // Check if there is currently a registry server
        // TODO: better synchronization here
        if (_registryServer == null || _vehicleServer == null)
            return;
        
        // Make a connection request via the registry
        try {
            Response response = new Response(_ticketCounter.incrementAndGet(), _registryServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_CONNECT.str);
            response.stream.writeUTF(((InetSocketAddress)addr).getAddress().getHostAddress());
            response.stream.writeInt(((InetSocketAddress)addr).getPort());
            _udpServer.respond(response);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to make connection request to registry.", e);
        }
    }
    
    public SocketAddress getVehicleService() {
        return _vehicleServer;
    }
    
    public void setRegistryService(SocketAddress addr) {
        _registryServer = addr;
    }
    
    public SocketAddress getRegistryService() {
        return _registryServer;
    }

    private void registerListener(List listenerList, UdpConstants.COMMAND registerCommand) {
        synchronized(listenerList) {
            if (!listenerList.isEmpty()) {
                try {
                    Response response = new Response(UdpConstants.NO_TICKET, _vehicleServer);
                    response.stream.writeUTF(registerCommand.str);
                    _udpServer.send(response);
                } catch (IOException e) {
                    // TODO: should probably change state or something
                    logger.log(Level.WARNING, "Failed to transmit listener registration: {0}", registerCommand);
                }
            }
        }
    }

    /**
     * This function attempts to reconstruct an image from a UDP packet stream.
     * It assumes a continuous stream of unreliable, non-retransmitted images.
     * 
     * @param req a request containing the latest image
     * @return byte array containing compressed image, or null if not complete
     * @throws IOException 
     */
    private byte[] reconstructImage(Request req) throws IOException {
        synchronized(_imageReassemblyLock) {
            
            // Reconstruct all the data from the request
            int imageSeq = req.stream.readInt();
            int totalIdx = req.stream.readInt();
            int pieceIdx = req.stream.readInt();
            
            // If this is a new image, reset the table
            if (_imageReassemblyTicket != imageSeq) {
                _imageReassemblyTicket = imageSeq;
                _imageReassemblyTable = new byte[totalIdx][];
            }
            
            // Put the piece in the corresponding slot if we don't have it
            if (_imageReassemblyTable[pieceIdx] == null) {
                byte[] image = new byte[req.stream.readInt()];
                req.stream.readFully(image);
                _imageReassemblyTable[pieceIdx] = image;
            } else {
                return null;
            }
            
            // Get total image length.  If the pieces aren't done, return null
            int totalLength = 0;
            for (byte[] piece : _imageReassemblyTable) {
                if (piece == null) {
                    return null;
                } else {
                    totalLength += piece.length;
                }
            }
            
            // Reconstruct the image by copying together the pieces
            byte[] totalImage = new byte[totalLength];
            int offset = 0;
            for (byte[] piece : _imageReassemblyTable) {
                System.arraycopy(piece, 0, totalImage, offset, piece.length);
                offset += piece.length;
            }
            return totalImage;
        }
    }

    private class RegistrationTask extends TimerTask {
        @Override
        public void run() {
            // Don't need to register if we don't know the server
            if (_vehicleServer == null)
                return;
            
            // Check the lists for listeners, register if there are any
            registerListener(_imageListeners, UdpConstants.COMMAND.CMD_REGISTER_IMAGE_LISTENER);
            registerListener(_velocityListeners, UdpConstants.COMMAND.CMD_REGISTER_VELOCITY_LISTENER);
            registerListener(_poseListeners, UdpConstants.COMMAND.CMD_REGISTER_POSE_LISTENER);
            registerListener(_cameraListeners, UdpConstants.COMMAND.CMD_REGISTER_CAMERA_LISTENER);
            registerListener(_waypointListeners, UdpConstants.COMMAND.CMD_REGISTER_WAYPOINT_LISTENER);
            registerListener(_crumbListeners, UdpConstants.COMMAND.CMD_REGISTER_CRUMB_LISTENER);
            registerListener(_sensorListeners, UdpConstants.COMMAND.CMD_REGISTER_SENSOR_LISTENER);
            /*
            // Special case to handle sensor listener channels
            synchronized (_sensorListeners) {
                for (Integer i : _sensorListeners.keySet()) {
                    try {
                        Response response = new Response(UdpConstants.NO_TICKET, _vehicleServer);
                        response.stream.writeUTF(UdpConstants.COMMAND.CMD_REGISTER_SENSOR_LISTENER.str);
                        response.stream.writeInt(i);
                        _udpServer.send(response);
                    } catch (IOException e) {
                        // TODO: should probably change state or something
                        logger.log(Level.WARNING, "Failed to transmit listener registration: {0}", 
                                UdpConstants.COMMAND.CMD_REGISTER_SENSOR_LISTENER.str);
                    }
                }
            }
            */
        }
    }

    public void received(Request req) {
        try {
            final String command = req.stream.readUTF();
            UdpConstants.COMMAND cmd = UdpConstants.COMMAND.fromStr(command);
            
            // TODO: remove me
            //logger.log(Level.INFO, "Received command {0} [{1}:{2}]", new Object[]{req.ticket, command, UdpConstants.COMMAND.fromStr(command)});

            // Handle one-way commands (asynchronous events)
            switch (cmd) {
                case CMD_SEND_CAMERA:
                    CameraState cState = CameraState.values()[req.stream.readByte()];
                    synchronized (_cameraListeners) {
                        for (CameraListener l : _cameraListeners) {
                            l.imagingUpdate(cState);
                        }
                    }
                    return;
                case CMD_SEND_IMAGE:
                    byte[] image = reconstructImage(req);
                    if (image != null) {
                        synchronized (_imageListeners) {
                            for (ImageListener l : _imageListeners) {
                                l.receivedImage(image);
                            }
                        }
                    }
                    return;
                case CMD_SEND_POSE:
                    UtmPose pose = UdpConstants.readPose(req.stream);
                    synchronized (_poseListeners) {
                        for (PoseListener l : _poseListeners) {
                            l.receivedPose(pose);
                        }
                    }
                    return;
                case CMD_SEND_CRUMB:
                {
                    double[] crumb = UdpConstants.readLatLng(req.stream);
                    long index = req.stream.readLong();                    
                    synchronized (_crumbListeners) {
                        for (CrumbListener l : _crumbListeners) {
                            l.receivedCrumb(crumb, index);
                        }
                    }
                    return;
                }
                case CMD_SEND_SENSOR:
                {
                    SensorData data = UdpConstants.readSensorData(req.stream);
                    long index = req.stream.readLong();
                    synchronized (_sensorListeners) {
                        // If there is no list of listeners, there is nothing to notify
                        if (_sensorListeners.isEmpty()) return;

                        // Notify each listener in the appropriate list
                        for (SensorListener l : _sensorListeners) {
                            l.receivedSensor(data, index);
                        }
                    }
                    return;
                }
                case CMD_SEND_VELOCITY:
                    Twist twist = UdpConstants.readTwist(req.stream);
                    synchronized (_velocityListeners) {
                        for (VelocityListener l : _velocityListeners) {
                            l.receivedVelocity(twist);
                        }
                    }
                    return;
                case CMD_SEND_WAYPOINT:
                    WaypointState wState = WaypointState.values()[req.stream.readByte()];
                    synchronized (_waypointListeners) {
                        for (WaypointListener l : _waypointListeners) {
                            l.waypointUpdate(wState);
                        }
                    }
                    return;          
            }
            
            // For two-way commands (functions), check for a ticket
            FunctionObserver obs = _ticketMap.remove(req.ticket);
            if (obs == null) return;
            
            // If one exists, dispatch the command
            switch (cmd) {
                case CMD_GET_POSE:
                    obs.completed(UdpConstants.readPose(req.stream));
                    return;
                case CMD_CAPTURE_IMAGE:
                    byte[] image = new byte[req.stream.readInt()];
                    req.stream.readFully(image);
                    obs.completed(image);
                    return;    
                case CMD_GET_CAMERA_STATUS:
                    obs.completed(CameraState.values()[req.stream.readByte()]);
                    return;
                case CMD_GET_VELOCITY:
                    obs.completed(UdpConstants.readTwist(req.stream));
                    return;
                case CMD_GET_WAYPOINTS:
                    double[][] poses = new double[req.stream.readInt()][2];
                    for (int i = 0; i < poses.length; i++) {
                        poses[i] = UdpConstants.readLatLng(req.stream);
                    }
                    obs.completed(poses);
                    return;
                case CMD_GET_WAYPOINT_STATUS:
                    obs.completed(WaypointState.values()[req.stream.readByte()]);
                    return;
                case CMD_GET_WAYPOINTS_INDEX:
                    obs.completed(req.stream.readInt());
                    return;
                case CMD_IS_CONNECTED:
                    obs.completed(req.stream.readBoolean());
                    return;
                case CMD_IS_AUTONOMOUS:
                    obs.completed(req.stream.readBoolean());
                    return;
                case CMD_GET_GAINS:
                    double[] gains = new double[req.stream.readInt()];
                    for (int i = 0; i < gains.length; ++i) {
                        gains[i] = req.stream.readDouble();
                    }
                    obs.completed(gains);
                    return;
                case CMD_GET_HOME:
                    obs.completed(UdpConstants.readLatLng(req.stream));
                    return;
                case CMD_LIST:
                    Map<SocketAddress, String> clients = new HashMap<SocketAddress, String>();
                    int numClients = req.stream.readInt();
                    
                    for (int i = 0; i < numClients; ++i) {
                        String name = req.stream.readUTF();
                        String hostname = req.stream.readUTF();
                        int port = req.stream.readInt();
                        clients.put(new InetSocketAddress(hostname, port), name);
                    }
                    obs.completed(clients);
                    return;                
                case CMD_SET_POSE:
                case CMD_SET_VELOCITY:
                case CMD_SET_AUTONOMOUS:
                case CMD_SET_GAINS:
                case CMD_START_CAMERA:
                case CMD_STOP_CAMERA:
                case CMD_START_WAYPOINTS:
                case CMD_STOP_WAYPOINTS:
                case CMD_SET_HOME:
                case CMD_START_GO_HOME:
                case CMD_NEW_AUTONOMOUS_PREDICATE_MSG:
                case CMD_ACK_CRUMB:
                case CMD_ACK_SENSORDATA:
                    obs.completed(null);
                    return;
                default:
                    logger.log(Level.WARNING, "Ignoring unknown command: {0}", command);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to parse request: {0}", req.ticket);
        }
    }

    public void timeout(long ticket, SocketAddress destination) {
        FunctionObserver obs = _ticketMap.remove(ticket);
        if (obs != null) {
            obs.failed(FunctionObserver.FunctionError.TIMEOUT);
        }
    }

    public void addPoseListener(PoseListener l, FunctionObserver<Void> obs) {
        synchronized (_poseListeners) {
            _poseListeners.add(l);
        }
        if (obs != null) {
            obs.completed(null);
        }
    }

    public void removePoseListener(PoseListener l, FunctionObserver<Void> obs) {
        synchronized (_poseListeners) {
            _poseListeners.remove(l);
        }
        if (obs != null) {
            obs.completed(null);
        }
    }

    public void addCrumbListener(CrumbListener l, FunctionObserver<Void> obs) {
        synchronized (_crumbListeners) {
            _crumbListeners.add(l);
        }
        if (obs != null) {
            obs.completed(null);
        }
    }

    public void removeCrumbListener(CrumbListener l, FunctionObserver<Void> obs) {
        synchronized (_crumbListeners) {
            _crumbListeners.remove(l);
        }
        if (obs != null) {
            obs.completed(null);
        }
    }
    
    public void acknowledgeCrumb(long id, FunctionObserver<Void> obs) {
        if (_vehicleServer == null) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
            return;
        }
        
        long ticket = (obs == null) ? UdpConstants.NO_TICKET : _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_ACK_CRUMB.str);
            response.stream.writeLong(id);
            if (obs != null) { 
                _ticketMap.put(ticket, obs);
                _udpServer.respond(response);
            } else {
                _udpServer.respond(response);
            }
            _udpServer.respond(response);
        } catch (IOException e) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
        }
    }

    public void setPose(UtmPose pose, FunctionObserver<Void> obs) {
        if (_vehicleServer == null) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
            return;
        }
        
        long ticket = (obs == null) ? UdpConstants.NO_TICKET : _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_SET_POSE.str);
            UdpConstants.writePose(response.stream, pose);
            if (obs != null) {
                _ticketMap.put(ticket, obs);
                _udpServer.respond(response);
            } else {
                _udpServer.send(response);
            }
        } catch (IOException e) {
            // TODO: Should I also flag something somewhere?
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
        }
    }

    public void getPose(FunctionObserver<UtmPose> obs) {
        // This is a pure getter function, just do nothing if there is no one listening.
        if (obs == null) return;

        if (_vehicleServer == null) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
            return;
        }

        long ticket = _ticketCounter.incrementAndGet();

        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_GET_POSE.str);
            _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
        }
    }

    public void addImageListener(ImageListener l, FunctionObserver<Void> obs) {
        synchronized (_imageListeners) {
            _imageListeners.add(l);
        }
        if (obs != null) {
            obs.completed(null);
        }
    }

    public void removeImageListener(ImageListener l, FunctionObserver<Void> obs) {
        synchronized (_imageListeners) {
            _imageListeners.remove(l);
        }
        if (obs != null) {
            obs.completed(null);
        }
    }

    public void captureImage(int width, int height, FunctionObserver<byte[]> obs) {
        if (_vehicleServer == null) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
            return;
        }

        long ticket = (obs == null) ? UdpConstants.NO_TICKET : _ticketCounter.incrementAndGet();

        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_CAPTURE_IMAGE.str);
            response.stream.writeInt(width);
            response.stream.writeInt(height);
            if (obs != null) _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            // TODO: Should I also flag something somewhere?
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
        }
    }

    public void addCameraListener(CameraListener l, FunctionObserver<Void> obs) {
        synchronized (_cameraListeners) {
            _cameraListeners.add(l);
        }
        if (obs != null) {
            obs.completed(null);
        }
    }

    public void removeCameraListener(CameraListener l, FunctionObserver<Void> obs) {
        synchronized (_cameraListeners) {
            _cameraListeners.remove(l);
        }
        if (obs != null) {
            obs.completed(null);
        }
    }

    public void startCamera(int numFrames, double interval, int width, int height, FunctionObserver<Void> obs) {
        if (_vehicleServer == null) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
            return;
        }
        
        long ticket = (obs == null) ? UdpConstants.NO_TICKET : _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_START_CAMERA.str);
            response.stream.writeInt(numFrames);
            response.stream.writeDouble(interval);
            response.stream.writeInt(width);
            response.stream.writeInt(height);           
            if (obs != null) _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            // TODO: Should I also flag something somewhere?
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
        }
    }

    public void stopCamera(FunctionObserver<Void> obs) {
        if (_vehicleServer == null) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
            return;
        }
        
        long ticket = (obs == null) ? UdpConstants.NO_TICKET : _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_STOP_CAMERA.str);
            if (obs != null) _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            // TODO: Should I also flag something somewhere?
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
        }
    }

    public void getCameraStatus(FunctionObserver<CameraState> obs) {
        // This is a pure getter function, just do nothing if there is no one listening.
        if (obs == null) return;

        if (_vehicleServer == null) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
            return;
        }
        
        long ticket = _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_GET_CAMERA_STATUS.str);
            _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
        }
    }

    public void addSensorListener(SensorListener l, FunctionObserver<Void> obs) {
        synchronized (_sensorListeners) {
            _sensorListeners.add(l);
        }
        if (obs != null) {
            obs.completed(null);
        }
    }

    public void removeSensorListener(SensorListener l, FunctionObserver<Void> obs) {
        synchronized (_sensorListeners) {
            // If there is no list of listeners, there is nothing to remove
            if (_sensorListeners.isEmpty()) return;

            // Remove the listener from the appropriate list
            _sensorListeners.remove(l);
        }
        if (obs != null) {
            obs.completed(null);
        }
    }
    
    public void acknowledgeSensorData(long id, FunctionObserver<Void> obs) {
        if (_vehicleServer == null) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
            return;
        }
        
        long ticket = (obs == null) ? UdpConstants.NO_TICKET : _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_ACK_SENSORDATA.str);            
            response.stream.writeLong(id);
            if (obs != null) { 
                _ticketMap.put(ticket, obs);
                _udpServer.respond(response);
            } else {
                _udpServer.respond(response);
            }
            _udpServer.respond(response);            
        } catch (IOException e) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
        }
    }

    public void addVelocityListener(VelocityListener l, FunctionObserver<Void> obs) {
        synchronized (_velocityListeners) {
            _velocityListeners.add(l);
        }
        if (obs != null) {
            obs.completed(null);
        }
    }

    public void removeVelocityListener(VelocityListener l, FunctionObserver<Void> obs) {
        synchronized (_velocityListeners) {
            _velocityListeners.remove(l);
        }
        if (obs != null) {
            obs.completed(null);
        }
    }

    public void setVelocity(Twist velocity, FunctionObserver<Void> obs) {
        if (_vehicleServer == null) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
            return;
        }
        
        long ticket = (obs == null) ? UdpConstants.NO_TICKET : _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_SET_VELOCITY.str);
            UdpConstants.writeTwist(response.stream, velocity);
            if (obs != null) {
                _ticketMap.put(ticket, obs);
                _udpServer.respond(response);
            } else {
                _udpServer.send(response);
            }
        } catch (IOException e) {
            // TODO: Should I also flag something somewhere?
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
        }
    }

    public void getVelocity(FunctionObserver<Twist> obs) {
        // This is a pure getter function, just do nothing if there is no one listening.
        if (obs == null) return;

        if (_vehicleServer == null) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
            return;
        }
        
        long ticket = _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_GET_VELOCITY.str);
            _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
        }
    }

    public void addWaypointListener(WaypointListener l, FunctionObserver<Void> obs) {
        synchronized (_waypointListeners) {
            _waypointListeners.add(l);
        }
        if (obs != null) {
            obs.completed(null);
        }
    }

    public void removeWaypointListener(WaypointListener l, FunctionObserver<Void> obs) {
        synchronized (_waypointListeners) {
            _waypointListeners.remove(l);
        }
        if (obs != null) {
            obs.completed(null);
        }
    }

    public void startWaypoints(double[][] waypoints, FunctionObserver<Void> obs) {
        if (_vehicleServer == null) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
            return;
        }
        
        long ticket = (obs == null) ? UdpConstants.NO_TICKET : _ticketCounter.incrementAndGet();

        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_START_WAYPOINTS.str);
            response.stream.writeInt(waypoints.length);
            for (double[] waypoint : waypoints) {
                UdpConstants.writeLatLng(response.stream, waypoint);
            }
            if (obs != null) _ticketMap.put(ticket, obs);
            _udpServer.respond(response);            
        } catch (IOException e) {
            // TODO: Should I also flag something somewhere?
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
        }
    }

    public void stopWaypoints(FunctionObserver<Void> obs) {
        if (_vehicleServer == null) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
            return;
        }
        
        long ticket = (obs == null) ? UdpConstants.NO_TICKET : _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_STOP_WAYPOINTS.str);
            if (obs != null) _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            // TODO: Should I also flag something somewhere?
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
        }
    }

    public void getWaypoints(FunctionObserver<double[][]> obs) {
        // This is a pure getter function, just do nothing if there is no one listening.
        if (obs == null) return;

        if (_vehicleServer == null) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
            return;
        }
        
        long ticket = _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_GET_WAYPOINTS.str);
            _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
        }        
    }

    public void getWaypointStatus(FunctionObserver<WaypointState> obs) {
        // This is a pure getter function, just do nothing if there is no one listening.
        if (obs == null) return;

        if (_vehicleServer == null) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
            return;
        }
        
        long ticket = _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_GET_WAYPOINT_STATUS.str);
            _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
        }
    }

    public void getWaypointsIndex(FunctionObserver<Integer> obs) {
        // This is a pure getter function, just do nothing if there is no one listening.
        if (obs == null) return;

        if (_vehicleServer == null) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
            return;
        }

        long ticket = _ticketCounter.incrementAndGet();

        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_GET_WAYPOINTS_INDEX.str);
            _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
        }
    }

    public void isConnected(FunctionObserver<Boolean> obs) {
        // This is a pure getter function, just do nothing if there is no one listening.
        if (obs == null) return;

        if (_vehicleServer == null) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
            return;
        }
        
        long ticket = _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_IS_CONNECTED.str);
            _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
        }
    }

    public void isAutonomous(FunctionObserver<Boolean> obs) {
        // This is a pure getter function, just do nothing if there is no one listening.        
        if (obs == null) return;

        if (_vehicleServer == null) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
            return;
        }
        
        long ticket = _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_IS_AUTONOMOUS.str);
            _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
        }
    }

    public void setAutonomous(boolean auto, FunctionObserver<Void> obs) {       
        if (_vehicleServer == null) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
            return;
        }
        
        long ticket = (obs == null) ? UdpConstants.NO_TICKET : _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_SET_AUTONOMOUS.str);
            response.stream.writeBoolean(auto);
            if (obs != null) _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            // TODO: Should I also flag something somewhere?
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
        }
    }

    public void setGains(int axis, double[] gains, FunctionObserver<Void> obs) {
        if (_vehicleServer == null) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
            return;
        }
        
        long ticket = (obs == null) ? UdpConstants.NO_TICKET : _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_SET_GAINS.str);
            response.stream.writeInt(axis);
            response.stream.writeInt(gains.length);
            for (int i = 0; i < gains.length; ++i) {
                response.stream.writeDouble(gains[i]);
            }
            if (obs != null) _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            // TODO: Should I also flag something somewhere?
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
        }
    }

    public void getGains(int axis, FunctionObserver<double[]> obs) {
        // This is a pure getter function, just do nothing if there is no one listening.
        if (obs == null) return;

        if (_vehicleServer == null) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
            return;
        }
        
        long ticket = _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_GET_GAINS.str);
            response.stream.writeInt(axis);
            _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
        }
    }

    public void setHome(double[] home, FunctionObserver<Void> obs) {
        if (_vehicleServer == null) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
            return;
        }    

        long ticket = (obs == null) ? UdpConstants.NO_TICKET : _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_SET_HOME.str);
            UdpConstants.writeLatLng(response.stream, home);
            if (obs != null) _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
        }            
    }    

    public void getHome(FunctionObserver<double[]> obs) {
        // This is a pure getter function, just do nothing if there is no one listening.
        if (obs == null) return;

        if (_vehicleServer == null) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
            return;
        }

        long ticket = _ticketCounter.incrementAndGet();

        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_GET_HOME.str);
            _ticketMap.put(ticket, obs);
            _udpServer.respond(response);            
        } catch (IOException e) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
        }
    }

    public void startGoHome(FunctionObserver<Void> obs) {
        if (_vehicleServer == null) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
            return;
        }
        
        long ticket = (obs == null) ? UdpConstants.NO_TICKET : _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_START_GO_HOME.str);
            if (obs != null) _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            // TODO: Should I also flag something somewhere?
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
        }        
    }
    
    public void newAutonomousPredicateMessage(String apm, FunctionObserver<Void> obs)
    {
        if (_vehicleServer == null) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
            return;
        }    

        long ticket = (obs == null) ? UdpConstants.NO_TICKET : _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _vehicleServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_NEW_AUTONOMOUS_PREDICATE_MSG.str);
            response.stream.writeUTF(apm);
            if (obs != null) _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            if (obs != null) {
                obs.failed(FunctionObserver.FunctionError.ERROR);
            }
        }         
    }
    
    /**
     * Special function that queries the already-set registry to find the list
     * of current clients.
     * 
     * @param obs an observer which will be called when the list is received
     */
    public void getVehicleServices(FunctionObserver<Map<SocketAddress, String>> obs) {
        // This is a pure getter function, just do nothing if there is no one listening.
        if (obs == null) return;

        if (_registryServer == null) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
            return;
        }
        
        long ticket = _ticketCounter.incrementAndGet();
        
        try {
            Response response = new Response(ticket, _registryServer);
            response.stream.writeUTF(UdpConstants.COMMAND.CMD_LIST.str);
            _ticketMap.put(ticket, obs);
            _udpServer.respond(response);
        } catch (IOException e) {
            obs.failed(FunctionObserver.FunctionError.ERROR);
        }
    }
}
