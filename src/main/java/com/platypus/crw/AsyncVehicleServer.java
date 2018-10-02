package com.platypus.crw;

import com.platypus.crw.FunctionObserver.FunctionError;
import com.platypus.crw.VehicleServer.CameraState;
import com.platypus.crw.VehicleServer.WaypointState;
import com.platypus.crw.data.Twist;
import com.platypus.crw.data.UtmPose;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A variant of VehicleServer in which methods are asynchronous, and allows the
 * registration of observers that represent their outcomes.
 *
 * @see VehicleServer
 *
 * @author Pras Velagapudi <psigen@gmail.com>
 */
public interface AsyncVehicleServer {

  public void addPoseListener(PoseListener l, FunctionObserver<Void> obs);
  public void removePoseListener(PoseListener l, FunctionObserver<Void> obs);
  public void setPose(UtmPose pose, FunctionObserver<Void> obs);
  public void getPose(FunctionObserver<UtmPose> obs);

  public void addImageListener(ImageListener l, FunctionObserver<Void> obs);
  public void removeImageListener(ImageListener l, FunctionObserver<Void> obs);
  public void captureImage(int width, int height, FunctionObserver<byte[]> obs);

  public void addCameraListener(CameraListener l, FunctionObserver<Void> obs);
  public void removeCameraListener(CameraListener l, FunctionObserver<Void> obs);
  public void startCamera(int numFrames, double interval, int width, int height, FunctionObserver<Void> obs);
  public void stopCamera(FunctionObserver<Void> obs);
  public void getCameraStatus(FunctionObserver<CameraState> obs);

  public void addSensorListener(SensorListener l, FunctionObserver<Void> obs);
  public void removeSensorListener(SensorListener l, FunctionObserver<Void> obs);
  public void acknowledgeSensorData(long id, FunctionObserver<Void> obs);

  public void addVelocityListener(VelocityListener l, FunctionObserver<Void> obs);
  public void removeVelocityListener(VelocityListener l, FunctionObserver<Void> obs);
  public void setVelocity(Twist velocity, FunctionObserver<Void> obs);
  public void getVelocity(FunctionObserver<Twist> obs);

  public void addWaypointListener(WaypointListener l, FunctionObserver<Void> obs);
  public void removeWaypointListener(WaypointListener l, FunctionObserver<Void> obs);
  public void startWaypoints(double[][] waypoints, FunctionObserver<Void> obs);
  public void stopWaypoints(FunctionObserver<Void> obs);
  public void getWaypoints(FunctionObserver<double[][]> obs);
  public void getWaypointStatus(FunctionObserver<WaypointState> obs);
  public void getWaypointsIndex(FunctionObserver<Integer> obs);
  
  public void addRCOverrideListener(RCOverrideListener l, FunctionObserver<Void> obs);
  public void removeRCOverrideListener(RCOverrideListener l, FunctionObserver<Void> obs);
  
  public void addKeyValueListener(KeyValueListener l, FunctionObserver<Void> obs);
  public void removeKeyValueListener(KeyValueListener l, FunctionObserver<Void> obs);
  public void setKeyValue(String key, float value, FunctionObserver<Void> obs);
  public void getKeyValue(String key, FunctionObserver<Void> obs);

  public void isConnected(FunctionObserver<Boolean> obs);
  public void isAutonomous(FunctionObserver<Boolean> obs);
  public void setAutonomous(boolean auto, FunctionObserver<Void> obs);

  public void setGains(int axis, double[] gains, FunctionObserver<Void> obs);
  public void getGains(int axis, FunctionObserver<double[]> obs);

  public void addCrumbListener(CrumbListener l, FunctionObserver<Void> obs);
  public void removeCrumbListener(CrumbListener l, FunctionObserver<Void> obs);
  public void acknowledgeCrumb(long id, FunctionObserver<Void> obs);
  
  public void addHomeListener(HomeListener l, FunctionObserver<Void> obs);
  public void removeHomeListener(HomeListener l, FunctionObserver<Void> obs);
  public void setHome(double[] home, FunctionObserver<Void> obs);
  public void getHome(FunctionObserver<double[]> obs);
  public void startGoHome(FunctionObserver<Void> obs);
  
  public void newAutonomousPredicateMessage(String apm, FunctionObserver<Void> obs);          

  /**
   * Utility class for handling AsyncVehicleServer objects.
   */
  public static class Util {
    /**
     * Converts VehicleServer implementation into asynchronous implementation.
     *
     * @param server the synchronous vehicle server implementation that will be wrapped
     * @return an asynchronous vehicle server using the specified implementation
     */
    public static AsyncVehicleServer toAsync(final VehicleServer server) {
      return new AsyncVehicleServer() {

        ExecutorService executor = Executors.newCachedThreadPool();

        @Override
        public void addPoseListener(final PoseListener l, final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.addPoseListener(l);
              if (obs != null) obs.completed(null);
            }
          });
        }

        @Override
        public void removePoseListener(final PoseListener l, final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.removePoseListener(l);
              if (obs != null) obs.completed(null);
            }
          });
        }
        
        @Override
        public void addCrumbListener(final CrumbListener l, final FunctionObserver<Void> obs) {
            executor.submit(new Runnable () {
                @Override
                public void run() {
                    server.addCrumbListener(l);
                    if (obs != null) obs.completed(null);
                }
            });
        }

        @Override
        public void removeCrumbListener(final CrumbListener l, final FunctionObserver<Void> obs) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    server.removeCrumbListener(l);
                    if (obs != null) obs.completed(null);
                }
            });
        }
        
        @Override
        public void acknowledgeCrumb(final long id, final FunctionObserver<Void> obs) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    server.acknowledgeCrumb(id);
                    if (obs != null) obs.completed(null);
                }
            });
        }
        
        @Override
        public void addRCOverrideListener(final RCOverrideListener l, final FunctionObserver<Void> obs) {
            executor.submit(new Runnable() {
               @Override
               public void run() {
                   server.addRCOverrideListener(l);
                   if (obs != null) obs.completed(null);
               }
            });
        }
        
        @Override
        public void removeRCOverrideListener(final RCOverrideListener l, final FunctionObserver<Void> obs) {
            executor.submit(new Runnable() {
               @Override
               public void run() {
                   server.removeRCOverrideListener(l);
                   if (obs != null) obs.completed(null);
               }
            });
        }
        
        @Override
        public void addKeyValueListener(final KeyValueListener l, final FunctionObserver<Void> obs) {
            executor.submit(new Runnable() {
               @Override
               public void run() {
                   server.addKeyValueListener(l);
                   if (obs != null) obs.completed(null);                   
               }
            });
        }
        
        @Override
        public void removeKeyValueListener(final KeyValueListener l, final FunctionObserver<Void> obs) {
            executor.submit(new Runnable() {
               @Override
               public void run() {
                   server.removeKeyValueListener(l);
                   if (obs != null) obs.completed(null);                   
               }
            });
        }
        
        @Override
        public void addHomeListener(final HomeListener l, final FunctionObserver<Void> obs) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    server.addHomeListener(l);
                    if (obs != null) obs.completed(null);
                }
            });
        }
        
        @Override
        public void removeHomeListener(final HomeListener l, final FunctionObserver<Void> obs) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    server.removeHomeListener(l);
                    if (obs != null) obs.completed(null);
                }
            });
        }

        @Override
        public void setPose(final UtmPose state, final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.setPose(state);
              if (obs != null) obs.completed(null);
            }
          });
        }

        @Override
        public void getPose(final FunctionObserver<UtmPose> obs) {
          if (obs == null) return;

          executor.submit(new Runnable() {
            @Override
            public void run() {
              obs.completed(server.getPose());
            }
          });
        }

        @Override
        public void addImageListener(final ImageListener l, final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.addImageListener(l);
              if (obs != null) obs.completed(null);
            }
          });
        }

        @Override
        public void removeImageListener(final ImageListener l, final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.removeImageListener(l);
              if (obs != null) obs.completed(null);
            }
          });
        }

        @Override
        public void captureImage(final int width, final int height, final FunctionObserver<byte[]> obs) {
          if (obs == null) return;

          executor.submit(new Runnable() {
            @Override
            public void run() {
              obs.completed(server.captureImage(width, height));
            }
          });
        }

        @Override
        public void addCameraListener(final CameraListener l, final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.addCameraListener(l);
              if (obs != null) obs.completed(null);
            }
          });
        }

        @Override
        public void removeCameraListener(final CameraListener l, final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.removeCameraListener(l);
              if (obs != null) obs.completed(null);
            }
          });
        }

        @Override
        public void startCamera(final int numFrames, final double interval, final int width, final int height, final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.startCamera(numFrames, interval, width, height);
              if (obs != null) obs.completed(null);
            }
          });
        }

        @Override
        public void stopCamera(final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.stopCamera();
              if (obs != null) obs.completed(null);
            }
          });
        }

        @Override
        public void getCameraStatus(final FunctionObserver<CameraState> obs) {
          if (obs == null) return;

          executor.submit(new Runnable() {
            @Override
            public void run() {
              obs.completed(server.getCameraStatus());
            }
          });
        }
        
        @Override
        public void addSensorListener(final SensorListener l, final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.addSensorListener(l);
              if (obs != null) obs.completed(null);
            }
          });
        }

        @Override
        public void removeSensorListener(final SensorListener l, final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.removeSensorListener(l);
              if (obs != null) obs.completed(null);
            }
          });
        }
        
        @Override
        public void acknowledgeSensorData(final long id, final FunctionObserver<Void> obs) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    server.acknowledgeSensorData(id);
                    if (obs != null) obs.completed(null);
                }
            });
        }

        @Override
        public void addVelocityListener(final VelocityListener l, final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.addVelocityListener(l);
              if (obs != null) obs.completed(null);
            }
          });
        }

        @Override
        public void removeVelocityListener(final VelocityListener l, final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.removeVelocityListener(l);
              if (obs != null) obs.completed(null);
            }
          });
        }

        @Override
        public void setVelocity(final Twist velocity, final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.setVelocity(velocity);
              if (obs != null) obs.completed(null);
            }
          });
        }

        @Override
        public void getVelocity(final FunctionObserver<Twist> obs) {
          if (obs == null) return;

          executor.submit(new Runnable() {
            @Override
            public void run() {
              obs.completed(server.getVelocity());
            }
          });
        }

        @Override
        public void addWaypointListener(final WaypointListener l, final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.addWaypointListener(l);
              if (obs != null) obs.completed(null);
            }
          });
        }

        @Override
        public void removeWaypointListener(final WaypointListener l, final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.removeWaypointListener(l);
              if (obs != null) obs.completed(null);
            }
          });
        }

        @Override
        public void startWaypoints(final double[][] waypoints, final FunctionObserver<Void> obs) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    server.startWaypoints(waypoints);
                    if (obs != null) obs.completed(null);
                }
            });
        }

        @Override
        public void stopWaypoints(final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.stopWaypoints();
              if (obs != null) obs.completed(null);
            }
          });
        }

        @Override
        public void getWaypoints(final FunctionObserver<double[][]> obs) {
          if (obs == null) return;

          executor.submit(new Runnable() {
            @Override
            public void run() {
              obs.completed(server.getWaypoints());
            }
          });            
        }

        @Override
        public void getWaypointStatus(final FunctionObserver<WaypointState> obs) {
          if (obs == null) return;

          executor.submit(new Runnable() {
            @Override
            public void run() {
              obs.completed(server.getWaypointStatus());
            }
          });
        }

        @Override
        public void getWaypointsIndex(final FunctionObserver<Integer> obs) {
          if (obs == null) return;

          executor.submit(new Runnable() {
            @Override
            public void run() {
              obs.completed(server.getWaypointsIndex());
            }
          });
        }

        @Override
        public void isConnected(final FunctionObserver<Boolean> obs) {
          if (obs == null) return;

          executor.submit(new Runnable() {
            @Override
            public void run() {
              obs.completed(server.isConnected());
            }
          });
        }

        @Override
        public void isAutonomous(final FunctionObserver<Boolean> obs) {
          if (obs == null) return;

          executor.submit(new Runnable() {
            @Override
            public void run() {
              obs.completed(server.isAutonomous());
            }
          });
        }

        @Override
        public void setAutonomous(final boolean auto, final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.setAutonomous(auto);
              if (obs != null) obs.completed(null);
            }
          });
        }

        @Override
        public void setGains(final int axis, final double[] gains, final FunctionObserver<Void> obs) {
          executor.submit(new Runnable() {
            @Override
            public void run() {
              server.setGains(axis, gains);
              if (obs != null) obs.completed(null);
            }
          });
        }

        @Override
        public void getGains(final int axis, final FunctionObserver<double[]> obs) {
          if (obs == null) return;

          executor.submit(new Runnable() {
            @Override
            public void run() {
              obs.completed(server.getGains(axis));
            }
          });
        }

        @Override
        public void setHome(final double[] home, final FunctionObserver<Void> obs) {
            executor.submit(new Runnable() {
               @Override
               public void run() {
                   server.setHome(home);
                   if (obs != null) obs.completed(null);
               }
            });
        }
        
        @Override
        public void getHome(final FunctionObserver<double[]> obs) {
            if (obs == null) return;
            
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    obs.completed(server.getHome());
                }
            });
        }
        
        @Override
        public void startGoHome(final FunctionObserver<Void> obs) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    server.startGoHome();
                    if (obs != null) obs.completed(null);
                }
            });
        }
        
        @Override
        public void setKeyValue(final String key, final float value, final FunctionObserver<Void> obs) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                  server.setKeyValue(key, value);
                  if (obs != null) obs.completed(null);
                }
            });
        }
        
        @Override
        public void getKeyValue(final String key, final FunctionObserver<Void> obs) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    server.getKeyValue(key);
                    if (obs != null) obs.completed(null);
                }
            });
        }
        
        @Override
        public void newAutonomousPredicateMessage(final String apm, final FunctionObserver<Void> obs)
        {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    server.newAutonomousPredicateMessage(apm);
                    if (obs != null) obs.completed(null);
                }
            });
        }
      };
    }

    /**
     * Converts AsyncVehicleServer implementation into synchronous implementation.
     *
     * @param server the asynchronous vehicle server implementation that will be wrapped
     * @return a synchronous vehicle server using the specified implementation
     */
    public static VehicleServer toSync(final AsyncVehicleServer server) {
      return new VehicleServer() {

        /**
         * Simple delay class that blocks a synchronous function
         * call until the backing asynchronous one completes.
         */
        class Delayer<V> implements FunctionObserver<V> {
          final CountDownLatch _latch = new CountDownLatch(1);
          private V _result = null;

          public V awaitResult() {
            try { _latch.await(); } catch (InterruptedException e) {}
            return _result;
          }

          @Override
          public void completed(V result) {
            _latch.countDown();
            _result = result;
          }

          @Override
          public void failed(FunctionError cause) {
            _latch.countDown();
          }
        }

        @Override
        public void addPoseListener(PoseListener l) {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.addPoseListener(l, delayer);
          delayer.awaitResult();
        }

        @Override
        public void removePoseListener(PoseListener l) {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.removePoseListener(l, delayer);
          delayer.awaitResult();
        }
        
        @Override
        public void addCrumbListener(CrumbListener l) {
            final Delayer<Void> delayer = new Delayer<Void>();
            server.addCrumbListener(l, delayer);
            delayer.awaitResult();
        }

        @Override
        public void removeCrumbListener(CrumbListener l) {
            final Delayer<Void> delayer = new Delayer<Void>();
            server.removeCrumbListener(l, delayer);
            delayer.awaitResult();
        }
        
        @Override
        public void acknowledgeCrumb(long id) {
            final Delayer<Void> delayer = new Delayer<Void>();
            server.acknowledgeCrumb(id, delayer);
            delayer.awaitResult();
        }
        
        @Override
        public void addRCOverrideListener(RCOverrideListener l) {
            final Delayer<Void> delayer = new Delayer<Void>();
            server.addRCOverrideListener(l, delayer);
            delayer.awaitResult();
        }
        
        @Override
        public void removeRCOverrideListener(RCOverrideListener l) {
            final Delayer<Void> delayer = new Delayer<Void>();
            server.removeRCOverrideListener(l, delayer);
            delayer.awaitResult();
        }        
        
        @Override
        public void addKeyValueListener(KeyValueListener l) {
            final Delayer<Void> delayer = new Delayer<Void>();
            server.addKeyValueListener(l, delayer);
            delayer.awaitResult();
        }
        
        @Override
        public void removeKeyValueListener(KeyValueListener l) {
            final Delayer<Void> delayer = new Delayer<Void>();
            server.removeKeyValueListener(l, delayer);
            delayer.awaitResult();
        }
        
        @Override
        public void addHomeListener(HomeListener l) {
            final Delayer<Void> delayer = new Delayer<Void>();
            server.addHomeListener(l, delayer);
            delayer.awaitResult();
        }
        
        @Override
        public void removeHomeListener(HomeListener l) {
            final Delayer<Void> delayer = new Delayer<Void>();
            server.removeHomeListener(l, delayer);
            delayer.awaitResult();
        }

        @Override
        public void setPose(UtmPose state) {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.setPose(state, delayer);
          delayer.awaitResult();
        }

        @Override
        public UtmPose getPose() {
          final Delayer<UtmPose> delayer = new Delayer<UtmPose>();
          server.getPose(delayer);
          return delayer.awaitResult();
        }

        @Override
        public void addImageListener(ImageListener l) {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.addImageListener(l, delayer);
          delayer.awaitResult();
        }

        @Override
        public void removeImageListener(ImageListener l) {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.removeImageListener(l, delayer);
          delayer.awaitResult();
        }

        @Override
        public byte[] captureImage(int width, int height) {
          final Delayer<byte[]> delayer = new Delayer<byte[]>();
          server.captureImage(width, height, delayer);
          return delayer.awaitResult();
        }

        @Override
        public void addCameraListener(CameraListener l) {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.addCameraListener(l, delayer);
          delayer.awaitResult();
        }

        @Override
        public void removeCameraListener(CameraListener l) {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.removeCameraListener(l, delayer);
          delayer.awaitResult();
        }

        @Override
        public void startCamera(int numFrames, double interval, int width, int height) {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.startCamera(numFrames, interval, width, height, delayer);
          delayer.awaitResult();
        }

        @Override
        public void stopCamera() {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.stopCamera(delayer);
          delayer.awaitResult();
        }

        @Override
        public CameraState getCameraStatus() {
          final Delayer<CameraState> delayer = new Delayer<CameraState>();
          server.getCameraStatus(delayer);
          return delayer.awaitResult();
        }
        
        @Override
        public void addSensorListener(SensorListener l) {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.addSensorListener(l, delayer);
          delayer.awaitResult();
        }

        @Override
        public void removeSensorListener(SensorListener l) {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.removeSensorListener(l, delayer);
          delayer.awaitResult();
        }
        
        @Override
        public void acknowledgeSensorData(long id) {
            final Delayer<Void> delayer = new Delayer<Void>();
            server.acknowledgeSensorData(id, delayer);
            delayer.awaitResult();
        }

        @Override
        public void addVelocityListener(VelocityListener l) {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.addVelocityListener(l, delayer);
          delayer.awaitResult();
        }

        @Override
        public void removeVelocityListener(VelocityListener l) {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.removeVelocityListener(l, delayer);
          delayer.awaitResult();
        }

        @Override
        public void setVelocity(Twist velocity) {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.setVelocity(velocity, delayer);
          delayer.awaitResult();
        }

        @Override
        public Twist getVelocity() {
          final Delayer<Twist> delayer = new Delayer<Twist>();
          server.getVelocity(delayer);
          return delayer.awaitResult();
        }

        @Override
        public void addWaypointListener(WaypointListener l) {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.addWaypointListener(l, delayer);
          delayer.awaitResult();
        }

        @Override
        public void removeWaypointListener(WaypointListener l) {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.removeWaypointListener(l, delayer);
          delayer.awaitResult();
        }

        @Override
        public void startWaypoints(double[][] waypoints) {
            final Delayer<Void> delayer = new Delayer<Void>();
            server.startWaypoints(waypoints, delayer);
            delayer.awaitResult();
        }

        @Override
        public void stopWaypoints() {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.stopWaypoints(delayer);
          delayer.awaitResult();
        }

        @Override
        public double[][] getWaypoints() {
          final Delayer<double[][]> delayer = new Delayer<double[][]>();
          server.getWaypoints(delayer);
          return delayer.awaitResult();
        }

        @Override
        public WaypointState getWaypointStatus() {
          final Delayer<WaypointState> delayer = new Delayer<WaypointState>();
          server.getWaypointStatus(delayer);
          return delayer.awaitResult();
        }

        @Override
        public int getWaypointsIndex() {
          final Delayer<Integer> delayer = new Delayer<Integer>();
          server.getWaypointsIndex(delayer);
          return delayer.awaitResult();
        }

        @Override
        public boolean isConnected() {
          final Delayer<Boolean> delayer = new Delayer<Boolean>();
          server.isConnected(delayer);
          Boolean b = delayer.awaitResult();
          return (b != null) ? b : false;
        }

        @Override
        public boolean isAutonomous() {
          final Delayer<Boolean> delayer = new Delayer<Boolean>();
          server.isAutonomous(delayer);
          Boolean b = delayer.awaitResult();
          return (b != null) ? b : false;
        }

        @Override
        public void setAutonomous(boolean auto) {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.setAutonomous(auto, delayer);
          delayer.awaitResult();
        }

        @Override
        public void setGains(int axis, double[] gains) {
          final Delayer<Void> delayer = new Delayer<Void>();
          server.setGains(axis, gains, delayer);
          delayer.awaitResult();
        }

        @Override
        public double[] getGains(int axis) {
          final Delayer<double[]> delayer = new Delayer<double[]>();
          server.getGains(axis, delayer);
          return delayer.awaitResult();
        }
        
        @Override
        public void setHome(double[] home) {
            final Delayer<Void> delayer = new Delayer<Void>();
            server.setHome(home, delayer);
            delayer.awaitResult();
        }
        
        @Override
        public double[] getHome() {
            final Delayer<double[]> delayer = new Delayer<double[]>();
            server.getHome(delayer);
            return delayer.awaitResult();
        }
        
        @Override
        public void startGoHome() {
            final Delayer<Void> delayer = new Delayer<Void>();
            server.startGoHome(delayer);
            delayer.awaitResult();        
        }
        
        @Override
        public void setKeyValue(String key, float value) {
            final Delayer<Void> delayer = new Delayer<Void>();
            server.setKeyValue(key, value, delayer);
            delayer.awaitResult();
        }      
        
        @Override
        public void getKeyValue(String key) {
            final Delayer<Void> delayer = new Delayer<Void>();
            server.getKeyValue(key, delayer);
            delayer.awaitResult();
        }
        
        @Override
        public void newAutonomousPredicateMessage(String apm) {
            final Delayer<Void> delayer = new Delayer<Void>();
            server.newAutonomousPredicateMessage(apm, delayer);
            delayer.awaitResult();
        }
      };
    }
  }
}
