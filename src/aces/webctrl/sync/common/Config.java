/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.sync.common;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
/**
 * Thread-safe namespace which encapsulates all primary configuration parameters.
 * Parameters are saved to the filesystem in a user-friendly format, so the configuration file can be modified in any text editor.
 */
public class Config {
  /**
   * Hardcoded internal version string for the application.
   * Used to determine compatibility when connecting remote hosts.
   */
  public final static String VERSION = "0.1.0";
  /**
   * Used for evaluating compatible version strings.
   */
  private final static String VERSION_SUBSTRING = VERSION.substring(0,VERSION.lastIndexOf('.'));
  /**
   * Raw version bytes.
   */
  public final static byte[] VERSION_RAW = VERSION.getBytes(java.nio.charset.StandardCharsets.UTF_8);
  /**
   * This application's display name
   */
  public final static String NAME = "Add-On Synchronizer";
  /**
   * The filepath specifying where to load and save the primary configuration file.
   */
  private volatile static Path configFile;
  /**
   * Specifies where the database binds to listen for connections
   * Default value is 1978, the year Automatic Controls Equipment Systems, Inc. was founded.
   */
  public volatile static int port = 1978;
  /**
   * The maximum waitlist size for client connection processing.
   * Default value is 1028.
   */
  public volatile static int backlog = 1028;
  /**
   * Specifies how long (in milliseconds) to keep log entries before deleting them.
   * Default value is one year.
   */
  public volatile static long deleteLogAfter = 31557600000L;
  /**
   * If this timeout (in milliseconds) is exceeded while waiting for a client response, the connection will be terminated.
   * The default value is 1 minute.
   */
  public volatile static long timeout = 60000L;
  /**
   * Clients must possess this secret key to register as a new server in this database.
   */
  public volatile static long connectionKey = 0;
  /**
   * Compares the given version string to the hardcoded version string of this application.
   * Assuming each version string is of the form "MAJOR.MINOR.PATCH",
   * two version strings are compatible whenever the MAJOR and MINOR versions agree.
   * The PATCH version number may differ between compatible instances.
   * @param ver is the given version string.
   * @return whether or not the given version string is compatible with the hardcoded version string.
   */
  public static boolean isCompatibleVersion(String ver){
    if (ver==null){
      return false;
    }
    int i = ver.lastIndexOf('.');
    if (i==-1){
      return false;
    }
    return VERSION_SUBSTRING.equals(ver.substring(0,i));
  }
  /**
   * Initializes parameters.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  public static boolean init(Path configFile){
    Config.configFile = configFile;
    return load();
  }
  /**
   * Helper method for {@link #loadConfig()}.
   * @param key
   * @param value
   * @return {@code true} on success; {@code false} if any error occurs.
   */
  private static boolean setConfigParameter(String key, String value){
    try{
      value = value.trim();
      switch (key.toUpperCase()){
        case "VERSION":{
          if (!isCompatibleVersion(value)){
            Logger.log("Configuration file version ("+value+") is not compatible with the application's internal version ("+VERSION+").");
            return false;
          }
          break;
        }
        case "PORT":{
          port = Integer.parseInt(value);
          break;
        }
        case "CONNECTIONKEY":{
          connectionKey = Long.parseUnsignedLong(value, 16);
          break;
        }
        case "DELETELOGAFTER":{
          deleteLogAfter = Long.parseLong(value);
          break;
        }
        case "BACKLOG":{
          backlog = Integer.parseInt(value);
          break;
        }
        case "TIMEOUT":{
          timeout = Long.parseLong(value);
          break;
        }
        default:{
          Logger.log("Unrecognized key-value pair in the primary configuration file ("+key+':'+value+')');
          return false;
        }
      }
      return true;
    }catch(Throwable e){
      Logger.log("Error occured while parsing configuration file.", e);
      return false;
    }
  }
  /**
   * Loads data from the primary configuration file into memory.
   * Invokes {@link #save()} if the configuration file does not exist.
   * Guaranteed to generate log message whenever this method returns false.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  public static boolean load(){
    try{
      byte[] arr;
      synchronized (Config.class){
        if (!Files.exists(configFile)){
          connectionKey = Database.entropy.nextLong();
          return save();
        }
        arr = Files.readAllBytes(configFile);
      }
      boolean ret = true;
      StringBuilder key = new StringBuilder();
      StringBuilder value = new StringBuilder();
      char c;
      for (int i=0;i<arr.length;++i){
        c = (char)arr[i];
        if (c=='='){
          for (++i;i<arr.length;++i){
            c = (char)arr[i];
            if (c=='\n'){
              ret&=setConfigParameter(key.toString(),value.toString());
              key.setLength(0);
              value.setLength(0);
              break;
            }else if (c!='\r'){
              value.append(c);
            }
          }
        }else if (c==';'){
          for (++i;i<arr.length;++i){
            c = (char)arr[i];
            if (c=='\n'){
              break;
            }
          }
        }else if (c!='\n' && c!='\r'){
          key.append(c);
        }
      }
      if (key.length()>0){
        ret&=setConfigParameter(key.toString(),value.toString());
      }
      return ret;
    }catch(Throwable e){
      Logger.log("Error occured while loading primary configuration file.", e);
      return false;
    }
  }
  /**
   * Overwrites the primary configuration file with parameters stored in the application's memory.
   * Guaranteed to generate log message whenever this method returns false.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  public static boolean save(){
    try{
      StringBuilder sb = new StringBuilder(512);
      sb.append(';'+NAME+" - Primary Configuration File\n");
      sb.append("\n;Note all time intervals are specified in milliseconds.");
      sb.append("\n\n;Used to determine compatibility\n");
      sb.append("Version=").append(VERSION);
      sb.append("\n\n;Specifies where the database binds to listen for connections\n");
      sb.append("Port=").append(port);
      sb.append("\n\n;This key hash authenticates the database\n");
      sb.append(";PublicKeyHash=").append(Keys.getPreferredKey().getHashString());
      sb.append("\n\n;This secret key authenticates clients\n");
      sb.append("ConnectionKey=").append(Long.toHexString(connectionKey));
      sb.append("\n\n;The maximum waitlist size for client connection processing\n");
      sb.append("BackLog=").append(backlog);
      sb.append("\n\n;Specifies how long to wait for a client response before assuming the connection has been lost\n");
      sb.append("Timeout=").append(timeout);
      sb.append("\n\n;Specifies how long to keep log entries before erasing them\n");
      sb.append("DeleteLogAfter=").append(deleteLogAfter);
      ByteBuffer buf = ByteBuffer.wrap(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      synchronized(Config.class){
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
      Logger.log("Error occured while saving primary configuration file.", e);
      return false;
    }
  }
}