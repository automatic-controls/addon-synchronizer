/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.sync.addon.core;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.function.*;
import java.util.concurrent.*;
import java.net.*;
import javax.servlet.*;
import com.controlj.green.addonsupport.*;
import aces.webctrl.sync.common.*;
/** Namespace which controls the primary operation of this addon */
public class Initializer implements ServletContextListener {
  /** The name of this addon */
  private volatile static String name;
  /** Prefix used for constructing relative URL paths */
  private volatile static String prefix;
  /** The main processing thread */
  private volatile static Thread mainThread = null;
  /** Task processing queue */
  private final static DelayQueue<DelayedRunnable> queue = new DelayQueue<DelayedRunnable>();
  /** Becomes true when the servlet context is destroyed */
  private volatile static boolean stop = false;
  /** Becomes true when the primary processing thread is terminated */
  private volatile static boolean stopped = false;
  /** Status message which may be displayed to to clients */
  public volatile static String status = "Uninitialized";
  /** Used to ensure the log file does not overflow with error messages when we try and fail to reconnect every few seconds */
  private volatile static boolean logConnectionErrors = true;
  /** Wraps the primary {@code AsynchronousSocketChannel} */
  private volatile static SocketWrapper wrap = null;
  /** The socket used to connect to the central database */
  private volatile static AsynchronousSocketChannel ch = null;
  /** Single-threaded group used for processing socket channel IO */
  private volatile static AsynchronousChannelGroup grp = null;
  /** Whether or not there is a connection to the central database */
  private volatile static boolean connected = false;
  /** Path to the WebCTRL installation directory (e.g, {@code C:\WebCTRL8.0}). */
  public volatile static Path rootWebCTRL = null;
  /** Path to the active WebCTRL system folder (e.g, {@code C:\WebCTRL8.0\webroot\test_system}). */
  public volatile static Path systemFolder = null;
  /** Path to the addons folder of WebCTRL (e.g, {@code C:\WebCTRL8.0\addons}). */
  public volatile static Path addonsFolder = null;
  /** Contains basic informatin about this addon. */
  public volatile static AddOnInfo info = null;
  /** Details when the next sync attempt will be made. */
  public volatile static String nextCronString = "Now";
  /**
   * @return whether or not there is an active connection to the database.
   */
  public static boolean isConnected(){
    return connected;
  }
  /** @return a status message. */
  public static String getStatus(){
    return status;
  }
  /** @return the name of this application. */
  public static String getName(){
    return name;
  }
  /** @return the prefix used for constructing relative URL paths. */
  public static String getPrefix(){
    return prefix;
  }
  /** Loads the local database and attempts to establish a connection to the central database. */
  @Override public void contextInitialized(ServletContextEvent sce){
    info = AddOnInfo.getAddOnInfo();
    name = info.getName();
    prefix = '/'+name+'/';
    final Path root = info.getPrivateDir().toPath();
    try{
      Logger.init(root.resolve("log.txt"), new Consumer<DelayedRunnable>(){
        public void accept(DelayedRunnable r){
          enqueue(r);
        }
      });
    }catch(Throwable e){
      e.printStackTrace();
    }
    try{
      systemFolder = root.getParent().getParent().getParent().normalize();
      rootWebCTRL = systemFolder.getParent().getParent().normalize();
      {
        final File addons = HelperAPI.getAddonsDirectory();
        if (addons==null){
          addonsFolder = rootWebCTRL.resolve("addons");
        }else{
          addonsFolder = addons.toPath();
        }
        addonsFolder = addonsFolder.normalize();
      }
    }catch(Throwable e){
      Logger.log(e);
    }
    SocketWrapper.config = new SocketWrapperConfig(){
      public long getTimeout(){
        return ClientConfig.timeout;
      }
    };
    Database.init(root, false);
    ClientConfig.init(root.resolve("config"));
    ClientConfig.load();
    Logger.trim(ClientConfig.deleteLogAfter);
    mainThread = new Thread(){
      public void run(){
        enqueueConnect(0);
        DelayedRunnable r = null;
        while (!stop){
          try{
            r = queue.poll(5000, TimeUnit.MILLISECONDS);
          }catch(InterruptedException e){
            r = null;
            continue;
          }
          while (!stop && r!=null){
            try{
              r.run();
            }catch(InterruptedException e){
              r = null;
              break;
            }catch(Throwable t){
              Logger.log("Error occurred in primary queue.", t);
            }
            r = queue.poll();
          }
        }
        if (r!=null){
          queue.offer(r);
        }
        stopped = true;
      }
    };
    Logger.log("Addon synchronizer successfully initialized.");
    status = "Initialized.";
    mainThread.start();
  }
  /** Disconnects from the central database and saves a copy of the local database. */
  @Override public void contextDestroyed(ServletContextEvent sce){
    stop = true;
    disconnect(null,true,false);
    if (mainThread!=null){
      mainThread.interrupt();
      //Wait for the primary processing thread to terminate.
      while (!stopped){
        try{
          mainThread.join();
        }catch(InterruptedException e){}
      }
    }
    DelayedRunnable r;
    while ((r=queue.poll())!=null){
      try{
        r.run();
      }catch(Throwable t){
        Logger.log("Error occurred in primary queue.", t);
      }
    }
    save();
    Logger.log("Add-on synchronizer has been shut down.");
    Logger.close();
  }
  /**
   * Disconnects from the database.
   */
  public synchronized static void disconnect(Throwable e, boolean log, boolean reconnect){
    connected = false;
    ch = null;
    if (wrap!=null){
      if (!wrap.isClosed()){
        wrap.close();
      }
      wrap = null;
    }
    if (grp!=null){
      try{
        grp.shutdownNow();
      }catch(Throwable t){
        if (log){
          Logger.logAsync("Error occurred while terminating AsynchronousChannelGroup.", t);
        }
      }
      grp = null;
      if (log){
        if (e==null){
          Logger.logAsync("Disconnected from database.");
        }else{
          Logger.logAsync("Disconnected from database.", e);
        }
      }
    }
    if (reconnect){
      Initializer.enqueueConnect(System.currentTimeMillis()+ClientConfig.reconnectTimeout);
    }
  }
  /** Enqueues a task on the primary processing queue */
  public static void enqueue(DelayedRunnable r){
    queue.offer(r);
  }
  /** Enqueues a task which attempts to connect to the database */
  private static void enqueueConnect(long expiry){
    if (!stop){
      enqueue(new DelayedRunnable(expiry){
        public void run(){
          if (!stop){
            nextCronString = "Now";
            ClientConfig.ipLock.readLock().lock();
            String host = ClientConfig.host;
            int port = ClientConfig.port;
            ClientConfig.ipLock.readLock().unlock();
            if (host==null){
              enqueueConnect(System.currentTimeMillis()+ClientConfig.reconnectTimeout);
            }else{
              try{
                status = "Connecting...";
                grp = AsynchronousChannelGroup.withFixedThreadPool(1, java.util.concurrent.Executors.defaultThreadFactory());
                ch = AsynchronousSocketChannel.open(grp);
                Future<Void> f = ch.connect(new InetSocketAddress(host,port));
                try{
                  f.get(ClientConfig.timeout, TimeUnit.MILLISECONDS);
                }catch(TimeoutException e){
                  f.cancel(true);
                  throw e;
                }
                final SocketWrapper wrapper = new SocketWrapper(ch);
                wrap = wrapper;
                connected = true;
                logConnectionErrors = true;
                //Read the application version and public key
                wrapper.readBytes(16384, null, new Handler<byte[]>(){
                  public void completed(byte[] arr, Void v){
                    try{
                      final SerializationStream s = new SerializationStream(arr);
                      final String version = s.readString();
                      //Ensure the database and addon versions are compatible
                      if (!Config.isCompatibleVersion(version)){
                        Logger.logAsync(status = "Incompatible versions: "+Config.VERSION+" and "+version);
                        disconnect(null,true,true);
                      }else{
                        final Key k = ClientConfig.databaseKey;
                        final Key kk = Key.deserialize(s,false);
                        //Ensure the key meets expectations
                        if (k==null){
                          ClientConfig.databaseKey = kk;
                        }else if (!k.equals(kk)){
                          Logger.logAsync(status = "Public keys do not match.");
                          disconnect(null,true,true);
                          return;
                        }
                        //Generate a temporary KeyPair for the handshake
                        java.security.KeyPair pair;
                        synchronized (Keys.keyPairGen){
                          pair = Keys.keyPairGen.generateKeyPair();
                        }
                        final java.security.PrivateKey pk = pair.getPrivate();
                        //Encrypt and write the temporary public key
                        wrapper.writeBytes(kk.encrypt(pair.getPublic().getEncoded()), null, new Handler<Void>(){
                          public void completed(Void v, Void vv){
                            //Read the encrypted symmetric key to use for this session
                            wrapper.readBytes(16384, null, new Handler<byte[]>(){
                              public void completed(byte[] arr, Void v){
                                try{
                                  javax.crypto.Cipher c = javax.crypto.Cipher.getInstance(Database.CIPHER);
                                  c.init(javax.crypto.Cipher.DECRYPT_MODE, pk);
                                  //Decrypt the symmetric key and use it to initialize a StreamCipher
                                  wrapper.setCipher(new StreamCipher(c.doFinal(arr)));
                                  //Write the secret connection key
                                  final SerializationStream s = new SerializationStream(8);
                                  s.write(ClientConfig.connectionKey);
                                  wrapper.writeBytes(s.data, null, new Handler<Void>(){
                                    public void completed(Void v, Void vv){
                                      //Determine whether the database accepted the connection key
                                      wrapper.read(null, new Handler<Byte>(){
                                        public void completed(Byte b, Void v){
                                          if (b==Protocol.SUCCESS){
                                            //Tell the database to synchronize addons
                                            wrapper.write(Protocol.CONTINUE, null, new Handler<Void>(){
                                              public void completed(Void v, Void vv){
                                                Logger.logAsync("Synchronization initiated.");
                                                //Synchronize addons
                                                wrapper.readPath(addonsFolder, null, new Handler<Boolean>(){
                                                  public void completed(Boolean b, Void v){
                                                    if (b){
                                                      Logger.logAsync("Synchronization successful.");
                                                      status = "Success";
                                                      ClientConfig.resetCron();
                                                      disconnect(null,true,false);
                                                      final long next = ClientConfig.getNextCron();
                                                      nextCronString = ClientConfig.getNextCronString();
                                                      enqueueConnect(next==-1?System.currentTimeMillis()+86400000:next);
                                                    }else{
                                                      disconnect(null,true,true);
                                                    }
                                                  }
                                                }, new Consumer<Path>(){
                                                  public void accept(Path p){
                                                    try{
                                                      //Disable addon before updating
                                                      if (Files.exists(p)){
                                                        String name = p.getFileName().toString();
                                                        final int len = name.length();
                                                        if (len>6){
                                                          name = name.substring(0,len-6);
                                                          HelperAPI.disableAddon(name);
                                                        }
                                                      }
                                                    }catch(Throwable t){
                                                      Logger.logAsync("PreConsumer Error", t);
                                                    }
                                                  }
                                                }, new BiConsumer<Path,Boolean>() {
                                                  public void accept(Path p, Boolean b){
                                                    try{
                                                      //Enable addon after updating
                                                      if (Files.exists(p)){
                                                        String name = p.getFileName().toString();
                                                        final int len = name.length();
                                                        if (len>6){
                                                          name = name.substring(0,len-6);
                                                          if (HelperAPI.enableAddon(name) || HelperAPI.deployAddon(p.toFile())){
                                                            Logger.logAsync("Updated: "+name);
                                                          }
                                                        }
                                                      }
                                                    }catch(Throwable t){
                                                      Logger.logAsync("PostConsumer Error", t);
                                                    }
                                                  }
                                                }, false);
                                              }
                                            });
                                          }else{
                                            Logger.logAsync(status = "Database rejected connection key.");
                                            disconnect(null,true,true);
                                          }
                                        }
                                      });
                                    }
                                  });
                                }catch(Throwable t){
                                  status = t.getClass().getSimpleName()+": "+t.getMessage();
                                  disconnect(t,true,true);
                                }
                              }
                            });
                          }
                        });
                      }
                    }catch(Throwable t){
                      status = t.getClass().getSimpleName()+": "+t.getMessage();
                      disconnect(t,true,true);
                    }
                  }
                });
              }catch(Throwable e){
                status = e.getClass().getSimpleName()+": "+e.getMessage();
                disconnect(e,logConnectionErrors,true);
                logConnectionErrors = false;
              }
            }
          }
        }
      });
    }
  }
  /** Saves all data */
  private static boolean save(){
    final boolean ret = ClientConfig.save();
    if (ret){
      Logger.log("Data saved successfully.");
    }else{
      Logger.log("Data backup failure.");
    }
    return ret;
  }
}