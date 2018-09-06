/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.platypus.crw.udp;

import com.platypus.crw.data.SensorData;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.Random;
import com.platypus.crw.VehicleServer;
import com.platypus.crw.AsyncVehicleServer;
import com.platypus.crw.CameraListener;
import com.platypus.crw.FunctionObserver;
import com.platypus.crw.ImageListener;
import com.platypus.crw.PoseListener;
import com.platypus.crw.SensorListener;
import com.platypus.crw.SimpleBoatSimulator;
import com.platypus.crw.VehicleServer.CameraState;
import com.platypus.crw.VehicleServer.WaypointState;
import com.platypus.crw.VelocityListener;
import com.platypus.crw.WaypointListener;
import com.platypus.crw.data.Twist;
import com.platypus.crw.data.Utm;
import com.platypus.crw.data.UtmPose;
import java.io.IOException;
import java.util.Arrays;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import com.platypus.crw.data.Pose3D;
import java.net.InetSocketAddress;
import static org.junit.Assert.*;

/**
 *
 * @author Prasanna Velagapudi <psigen@gmail.com>
 */
public class UdpVehicleServerTest {

    UdpVehicleService service;
    SimpleBoatSimulator sbs;
    InetSocketAddress serviceAddress;
    Random rnd;

    public UdpVehicleServerTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        sbs = new SimpleBoatSimulator();
        service = new UdpVehicleService(sbs);
        int port = ((InetSocketAddress)service.getSocketAddress()).getPort();
        serviceAddress = new InetSocketAddress("127.0.0.1", port);
        rnd = new Random();
    }

    @After
    public void tearDown() {
        service.shutdown();
        sbs.shutdown();
    }

    /**
     * Test of shutdown method, of class UdpVehicleServer.
     */
    @Test
    public void testShutdown() {
        UdpVehicleServer instance = new UdpVehicleServer(serviceAddress);
        instance.shutdown();

        // Make sure the UDP socket was closed
        assertTrue(instance._udpServer._socket.isClosed());

        // Make sure the timer processes are shut down
        try {
            instance._timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    // Do nothing
                }
            }, 0);
            fail("Timer was not shut down.");
        } catch (IllegalStateException e) {

        }
    }

    /**
     * Test of setVehicleService method, of class UdpVehicleServer.
     */
    @Test
    public void testSetVehicleService() {
        UdpVehicleServer instance = new UdpVehicleServer();

        VehicleServer server = AsyncVehicleServer.Util.toSync(instance);
        assertEquals("Server reports connected to null service",
                false, server.isConnected());

        instance.setVehicleService(serviceAddress);
        assertEquals("Server reports not connected to service",
                true, server.isConnected());

        instance.shutdown();
    }
    /**
     * Test of getVehicleService method, of class UdpVehicleServer.
     */
    @Test
    public void testGetVehicleService() {
        UdpVehicleServer instance = new UdpVehicleServer();

        instance.setVehicleService(serviceAddress);
        assertEquals("SocketAddress was not set correctly",
                serviceAddress, instance.getVehicleService());

        instance.shutdown();
    }

    /**
     * Test of addPoseListener method, of class UdpVehicleServer.
     */
    @Test
    public void testAddPoseListener() {
        final CountDownLatch latch = new CountDownLatch(1);

        // Register a new pose listener on this server
        UdpVehicleServer instance = new UdpVehicleServer(serviceAddress);
        VehicleServer server = AsyncVehicleServer.Util.toSync(instance);
        server.addPoseListener(new PoseListener() {
            @Override
            public void receivedPose(UtmPose pose) {
                latch.countDown();
            }
        });

        // If we haven't received a pose in a full second, something is wrong
        try {
            assertTrue("Did not receive pose update.", latch.await(2, TimeUnit.SECONDS));
        } catch(InterruptedException e) {
            fail("Did not receive pose update.");
        }

        instance.shutdown();
    }

    /**
     * Test of removePoseListener method, of class UdpVehicleServer.
     */
    @Test
    public void testRemovePoseListener() {
        PoseListener l = null;
        //FunctionObserver<Void> obs = null;
        //UdpVehicleServer instance = new UdpVehicleServer();
        //instance.removePoseListener(l, obs);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of setPose and getPose method, of class UdpVehicleServer.
     */
    @Test
    public void testSetGetPose() {
        UdpVehicleServer instance = new UdpVehicleServer(serviceAddress);

        // Generate a random pose
        UtmPose pose = new UtmPose(
                new Pose3D(rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble(),
                        rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble()),
                new Utm(12, rnd.nextBoolean()));

        VehicleServer server = AsyncVehicleServer.Util.toSync(instance);
        instance.setPose(pose, null);
        UtmPose gp = server.getPose();

        assertNotNull("Pose returned from server was null.", gp);
        assertTrue("Poses didn't match enough.", pose.pose.getEuclideanDistance(gp.pose) < 1e-6);
        assertEquals("Poses were in different zones", pose.origin, gp.origin);

        instance.shutdown();
    }

    /**
     * Test of addImageListener method, of class UdpVehicleServer.
     */
    @Test
    public void testAddImageListener() {
        final CountDownLatch latch = new CountDownLatch(1);

        // Register a new pose listener on this server
        UdpVehicleServer instance = new UdpVehicleServer(serviceAddress);
        VehicleServer server = AsyncVehicleServer.Util.toSync(instance);
        server.addImageListener(new ImageListener() {
            @Override
            public void receivedImage(byte[] image) {
                latch.countDown();
            }
        });
        server.startCamera(2, 1.0, 640, 480);

        // If we haven't received a pose in a full second, something is wrong
        try {
            assertTrue("Did not receive image update.", latch.await(2, TimeUnit.SECONDS));
        } catch(InterruptedException e) {
            fail("Did not receive image update.");
        }

        instance.shutdown();
    }

    /**
     * Test of removeImageListener method, of class UdpVehicleServer.
     */
    @Test
    public void testRemoveImageListener() {
        ImageListener l = null;
        FunctionObserver<Void> obs = null;
        //UdpVehicleServer instance = new UdpVehicleServer();
        //instance.removeImageListener(l, obs);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of captureImage method, of class UdpVehicleServer.
     */
    @Test
    public void testCaptureImage() throws IOException {
        UdpVehicleServer instance = new UdpVehicleServer(serviceAddress);

        // Generate a random image size
        int width = rnd.nextInt(64) + 1;
        int height = rnd.nextInt(64) + 1;

        // Check that we got an image of this size
        VehicleServer server = AsyncVehicleServer.Util.toSync(instance);
        byte[] bytes = server.captureImage(width, height);
        if (bytes == null)
            fail("Did not receive an image.");

        try {
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            assertEquals("Width is wrong.", image.getWidth(), width);
            assertEquals("Height is wrong.", image.getHeight(), height);
        } catch (IOException e) {
            fail("Did not receive valid image.");
        }

        instance.shutdown();
    }

    /**
     * Test of addCameraListener method, of class UdpVehicleServer.
     */
    @Test
    public void testAddCameraListener() {
        final CountDownLatch latch = new CountDownLatch(1);

        // Register a new pose listener on this server
        UdpVehicleServer instance = new UdpVehicleServer(serviceAddress);
        VehicleServer server = AsyncVehicleServer.Util.toSync(instance);
        server.addCameraListener(new CameraListener() {
            @Override
            public void imagingUpdate(CameraState state) {
                latch.countDown();
            }
        });
        server.startCamera(2, 1.0, 640, 480);

        // If we haven't received a pose in a full second, something is wrong
        try {
            assertTrue("Did not receive camera update.", latch.await(2, TimeUnit.SECONDS));
        } catch(InterruptedException e) {
            fail("Did not receive camera update.");
        }

        instance.shutdown();
    }

    /**
     * Test of removeCameraListener method, of class UdpVehicleServer.
     */
    @Test
    public void testRemoveCameraListener() {
        CameraListener l = null;
        //FunctionObserver<Void> obs = null;
        //UdpVehicleServer instance = new UdpVehicleServer();
        //instance.removeCameraListener(l, obs);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of startCamera method, of class UdpVehicleServer.
     */
    @Test
    public void testStartCamera() {
        int numFrames = 0;
        double interval = 0.0;
        int width = 0;
        int height = 0;
        FunctionObserver<Void> obs = null;
        //UdpVehicleServer instance = new UdpVehicleServer();
        //instance.startCamera(numFrames, interval, width, height, obs);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of stopCamera method, of class UdpVehicleServer.
     */
    @Test
    public void testStopCamera() {
        FunctionObserver<Void> obs = null;
        //UdpVehicleServer instance = new UdpVehicleServer();
        //instance.stopCamera(obs);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of getCameraStatus method, of class UdpVehicleServer.
     */
    @Test
    public void testGetCameraStatus() {
        FunctionObserver<CameraState> obs = null;
        //UdpVehicleServer instance = new UdpVehicleServer();
        //instance.getCameraStatus(obs);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of addSensorListener method, of class UdpVehicleServer.
     */
    @Test
    public void testAddSensorListener() {
        final CountDownLatch latch = new CountDownLatch(1);

        // Register a new pose listener on this server
        UdpVehicleServer instance = new UdpVehicleServer(serviceAddress);
        VehicleServer server = AsyncVehicleServer.Util.toSync(instance);
        server.addSensorListener(new SensorListener() {
            @Override
            public void receivedSensor(SensorData data, long id) {
                latch.countDown();
            }
        });

        // If we haven't received a pose in a full second, something is wrong
        try {
            assertTrue("Did not receive sensor update.", latch.await(2, TimeUnit.SECONDS));
        } catch(InterruptedException e) {
            fail("Did not receive sensor update.");
        }

        instance.shutdown();
    }

    /**
     * Test of removeSensorListener method, of class UdpVehicleServer.
     */
    @Test
    public void testRemoveSensorListener() {
        int channel = 0;
        SensorListener l = null;
        FunctionObserver<Void> obs = null;
        //UdpVehicleServer instance = new UdpVehicleServer();
        //instance.removeSensorListener(channel, l, obs);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of addVelocityListener method, of class UdpVehicleServer.
     */
    @Test
    public void testAddVelocityListener() {
        final CountDownLatch latch = new CountDownLatch(1);

        // Register a new pose listener on this server
        UdpVehicleServer instance = new UdpVehicleServer(serviceAddress);
        VehicleServer server = AsyncVehicleServer.Util.toSync(instance);
        server.addVelocityListener(new VelocityListener() {
            @Override
            public void receivedVelocity(Twist velocity) {
                latch.countDown();
            }
        });

        // If we haven't received a pose in a full second, something is wrong
        try {
            assertTrue("Did not receive velocity update.", latch.await(2, TimeUnit.SECONDS));
        } catch(InterruptedException e) {
            fail("Did not receive velocity update.");
        }

        instance.shutdown();
    }

    /**
     * Test of removeVelocityListener method, of class UdpVehicleServer.
     */
    @Test
    public void testRemoveVelocityListener() {
        VelocityListener l = null;
        FunctionObserver<Void> obs = null;
        //UdpVehicleServer instance = new UdpVehicleServer();
        //instance.removeVelocityListener(l, obs);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of setVelocity method, of class UdpVehicleServer.
     */
    @Test
    public void testSetVelocity() {
        Twist velocity = null;
        FunctionObserver<Void> obs = null;
        //UdpVehicleServer instance = new UdpVehicleServer();
        //instance.setVelocity(velocity, obs);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of getVelocity method, of class UdpVehicleServer.
     */
    @Test
    public void testGetVelocity() {
        FunctionObserver<Twist> obs = null;
        //UdpVehicleServer instance = new UdpVehicleServer();
        //instance.getVelocity(obs);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of addWaypointListener method, of class UdpVehicleServer.
     */
    @Test
    public void testAddWaypointListener() {
        final CountDownLatch latch = new CountDownLatch(1);

        // Register a new pose listener on this server
        UdpVehicleServer instance = new UdpVehicleServer(serviceAddress);
        VehicleServer server = AsyncVehicleServer.Util.toSync(instance);
        server.addWaypointListener(new WaypointListener() {
            @Override
            public void waypointUpdate(WaypointState state) {
                latch.countDown();
            }
        });
        //server.startWaypoints(new UtmPose[]{new UtmPose()}, "STOP");
        server.startWaypoints(new double[][]{{0, 0}});

        // If we haven't received a pose in a full second, something is wrong
        try {
            assertTrue("Did not receive waypoint update.", latch.await(2, TimeUnit.SECONDS));
        } catch(InterruptedException e) {
            fail("Did not receive waypoint update.");
        }

        instance.shutdown();
    }

    /**
     * Test of removeWaypointListener method, of class UdpVehicleServer.
     */
    @Test
    public void testRemoveWaypointListener() {
        WaypointListener l = null;
        FunctionObserver<Void> obs = null;
        //UdpVehicleServer instance = new UdpVehicleServer();
        //instance.removeWaypointListener(l, obs);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of startWaypoints method, of class UdpVehicleServer.
     */
    @Test
    public void testStartWaypoints() {
        UtmPose[] waypoints = null;
        String controller = "";
        FunctionObserver<Void> obs = null;
        //UdpVehicleServer instance = new UdpVehicleServer();
        //instance.startWaypoints(waypoints, controller, obs);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of stopWaypoints method, of class UdpVehicleServer.
     */
    @Test
    public void testStopWaypoints() {
        FunctionObserver<Void> obs = null;
        //UdpVehicleServer instance = new UdpVehicleServer();
        //instance.stopWaypoints(obs);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of getWaypoints method, of class UdpVehicleServer.
     */
    @Test
    public void testGetWaypoints() {
        FunctionObserver<UtmPose[]> obs = null;
        //UdpVehicleServer instance = new UdpVehicleServer();
        //instance.getWaypoints(obs);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of getWaypointStatus method, of class UdpVehicleServer.
     */
    @Test
    public void testGetWaypointStatus() {
        FunctionObserver<WaypointState> obs = null;
        //UdpVehicleServer instance = new UdpVehicleServer();
        //instance.getWaypointStatus(obs);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of isConnected method, of class UdpVehicleServer.
     */
    @Test
    public void testIsConnected() {
        UdpVehicleServer instance = new UdpVehicleServer(serviceAddress);
        VehicleServer server = AsyncVehicleServer.Util.toSync(instance);

        // Since we are using a test server, it should always be connected
        assertTrue("Server claimed it was not connected", server.isConnected());
    }

    /**
     * Test of set/isAutonomous method, of class UdpVehicleServer.
     */
    @Test
    public void testSetIsAutonomous() {
        UdpVehicleServer instance = new UdpVehicleServer(serviceAddress);
        VehicleServer server = AsyncVehicleServer.Util.toSync(instance);

        // Set to autonomous and back
        server.setAutonomous(true);
        boolean autocheck = server.isAutonomous();
        assertTrue("setAutonomy failed to turn on.", autocheck);

        server.setAutonomous(false);
        autocheck = server.isAutonomous();
        assertFalse("setAutonomy failed to turn off.", autocheck);

        instance.shutdown();
    }

    /**
     * Test of setGains and getGains method, of class UdpVehicleServer.
     */
    @Test
    public void testSetGetGains() {
        UdpVehicleServer instance = new UdpVehicleServer(serviceAddress);

        // Generate a random gain vector and channel
        int axis = rnd.nextInt(6);
        double[] gains = new double[3];
        for (int i = 0; i < gains.length; ++i) {
            gains[i] = rnd.nextDouble();
        }

        // Set the gain vector
        VehicleServer server = AsyncVehicleServer.Util.toSync(instance);
        server.setGains(axis, gains);
        double[] pg = server.getGains(axis);

        assertTrue("Gains do not match: expected " + Arrays.toString(gains) + ", actual " + Arrays.toString(pg), Arrays.equals(gains, pg));

        instance.shutdown();
    }
}
