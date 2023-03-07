/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.sync.addon.core;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.concurrent.locks.*;
import java.util.*;
import org.springframework.scheduling.support.*;
import aces.webctrl.sync.common.*;
import java.util.regex.*;
public class ClientConfig {
  /**
   * The filepath specifying where to load and save the primary configuration file.
   */
  private volatile static Path configFile;
  /**
   * The IP address of the database.
   */
  public volatile static String host = null;
  /**
   * Specifies where the database binds to listen for connections.
   * Default value is 1978, the year Automatic Controls Equipment Systems, Inc. was founded.
   */
  public volatile static int port = 1978;
  /**
   * Lock which controls access to host and port.
   */
  public final static ReentrantReadWriteLock ipLock = new ReentrantReadWriteLock();
  /**
   * The public key of the central database.
   */
  public volatile static Key databaseKey = null;
  /**
   * Specifies how long (in milliseconds) to keep log entries before deleting them.
   * Default value is one year.
   */
  public volatile static long deleteLogAfter = 31557600000L;
  /**
   * If this timeout (in milliseconds) is exceeded while waiting for a response, the connection will be terminated.
   * The default value is 1 minute.
   */
  public volatile static long timeout = 60000L;
  /**
   * Used to authenticate each WebCTRL server to the database.
   */
  public volatile static long connectionKey = 0L;
  /**
   * Cron expression specifying when to synchronize addons.
   */
  private volatile static String expr = null;
  /**
   * Cron expression object constructed from {@code expr}.
   */
  private volatile static CronSequenceGenerator cron = null;
  /**
   * When to synchronize addons, as specified by {@code expr}.
   */
  private volatile static long nextRunTime = -1L;
  /**
   * If this addon is disconnected from the central database, then it will try to reconnect after this timeout (specified in milliseconds).
   */
  public volatile static long reconnectTimeout = 300000L;
  /**
   * Sets the path to the primary configuration file.
   */
  public static void init(Path configFile){
    ClientConfig.configFile = configFile;
  }
  /**
   * Load the primary configuration file.
   */
  public static boolean load(){
    try{
      if (Files.exists(configFile)){
        byte[] arr;
        synchronized (ClientConfig.class){
          arr = Files.readAllBytes(configFile);
        }
        deserialize(arr);
      }
      return true;
    }catch(Throwable e){
      Logger.log("Error occurred while loading the primary configuration file.", e);
      return false;
    }
  }
  /**
   * Save the primary configuration file.
   */
  public static boolean save(){
    try{
      ByteBuffer buf = ByteBuffer.wrap(serialize());
      synchronized(ClientConfig.class){
        try(
          FileChannel out = FileChannel.open(configFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
          FileLock lock = out.tryLock();
        ){
          while (buf.hasRemaining()){
            out.write(buf);
          }
        }
      }
      return true;
    }catch(Throwable e){
      Logger.log("Error occurred while saving the primary configuration file.", e);
      return false;
    }
  }
  private final static Pattern blank = Pattern.compile("\\s*+");
  private static boolean isBlank(String str){
    return blank.matcher(str).matches();
  }
  private static byte[] serialize(){
    ipLock.readLock().lock();
    final int port = ClientConfig.port;
    final String host = ClientConfig.host;
    ipLock.readLock().unlock();
    byte[] hostBytes = (host==null||isBlank(host)?"NULL":host).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] cronBytes = (expr==null||isBlank(expr)?"NULL":expr).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    int len = hostBytes.length+cronBytes.length+44;
    Key k = databaseKey;
    if (k!=null){
      len+=k.length(false);
    }
    SerializationStream s = new SerializationStream(len);
    s.write(hostBytes);
    s.write(port);
    s.write(cronBytes);
    s.write(deleteLogAfter);
    s.write(timeout);
    s.write(connectionKey);
    s.write(reconnectTimeout);
    if (k!=null){
      k.serialize(s,false);
    }
    return s.data;
  }
  private static void deserialize(byte[] arr){
    SerializationStream s = new SerializationStream(arr);
    String host = s.readString();
    if (host.equals("NULL")){
      host = null;
    }
    int port = s.readInt();
    ipLock.writeLock().lock();
    ClientConfig.port = port;
    ClientConfig.host = host;
    ipLock.writeLock().unlock();
    String expr = s.readString();
    if (expr.equals("NULL")){
      expr = null;
    }
    setCronExpression(expr);
    deleteLogAfter = s.readLong();
    timeout = s.readLong();
    connectionKey = s.readLong();
    reconnectTimeout = s.readLong();
    if (s.end()){
      databaseKey = null;
    }else{
      try{
        databaseKey = Key.deserialize(s,false);
        if (!s.end()){
          Logger.log("The primary configuration file \""+configFile.toString()+"\" may have been corrupted.");
        }
      }catch(Throwable e){
        databaseKey = null;
        Logger.log("Error occurred while deserializing the central database's public key.", e);
      }
    }
  }
  /**
   * @return the next run time of the addon synchronization task. If {@code -1}, then this task should not ever execute.
   */
  public static long getNextCron(){
    return nextRunTime;
  }
  /**
   * @return the next run time of the addon synchronization task as a {@code String}.
   */
  public static String getNextCronString(){
    long nextRunTime = ClientConfig.nextRunTime;
    return nextRunTime==-1?"None":Logger.format.format(java.time.Instant.ofEpochMilli(nextRunTime));
  }
  /**
   * Resets the next run time of the addon synchronization task.
   */
  public static void resetCron(){
    CronSequenceGenerator cron = ClientConfig.cron;
    if (cron==null){
      nextRunTime = -1;
    }else{
      try{
        nextRunTime = cron.next(new Date()).getTime();
      }catch(Throwable t){
        nextRunTime = -1;
      }
    }
  }
  /**
   * @return the cron expression which controls scheduling for the addon synchronization task.
   */
  public static String getCronExpression(){
    return expr;
  }
  /**
   * Sets the cron expression used for the addon synchronization task.
   * @return {@code true} on success; {@code false} if the given expression cannot be parsed.
   */
  public static boolean setCronExpression(String expr){
    if (expr.equals(ClientConfig.expr)){
      return true;
    }else{
      ClientConfig.expr = expr;
      try{
        cron = new CronSequenceGenerator(expr);
        return true;
      }catch(Throwable t){
        cron = null;
        return false;
      }finally{
        resetCron();
      }
    }
  }
}