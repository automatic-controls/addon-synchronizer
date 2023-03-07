/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.sync.common;
import java.security.*;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
//TODO (Performance Improvement) - Make every IO operation non-blocking
/**
 * Thread-safe namespace for initializing and saving the database.
 */
public class Database {
  /** Used to specify the transformation for {@code javax.crypto.Cipher}. */
  public final static String CIPHER = "RSA/ECB/OAEPWITHSHA-512ANDMGF1PADDING";
  /** All-purpose random number generator for use anywhere its needed. This object is thread-safe. */
  public volatile static SecureRandom entropy;
  /** Variable which helps to optimize {@link #save()}. */
  private volatile static AtomicBoolean saving;
  /** Specifies whether or not this application is meant to act as a server to host the database (some behavior gets changed slightly). */
  private volatile static boolean server;
  /** Used for certain non-blocking IO operations. */
  public volatile static java.util.concurrent.ExecutorService exec = null;
  /**
   * Initializes all components of the database.
   * Invoked only once at the start of the application.
   * Note the {@code Logger} should be initialized separately, before this method is invoked.
   * @return {@code true} on success; {@code false} if any component fails to initialize.
   */
  public static boolean init(Path rootFolder, boolean server){
    entropy = new SecureRandom();
    Database.server = server;
    boolean ret = true;
    if (server){
      saving = new AtomicBoolean();
      ret&=Keys.init(rootFolder.resolve("keys"));
      ret&=Config.init(rootFolder.resolve("config.txt"));
      SocketWrapper.config = new SocketWrapperConfig(){
        public long getTimeout(){
          return Config.timeout;
        }
      };
    }else{
      try{
        Keys.initCrypto(2048);
      }catch(Throwable e){
        ret = false;
        Logger.log("Error occurred while initializing cryptographic objects.", e);
      }
    }
    return ret;
  }
  /**
   * Saves all database components.
   * Optimized to return immediately if another invokation of this method is concurrently executing.
   */
  public static boolean save(){
    if (server && saving.compareAndSet(false, true)){
      boolean ret = true;
      ret&=Config.save();
      ret&=Keys.save();
      saving.set(false);
      return ret;
    }else{
      return true;
    }
  }
  /**
   * Specifies whether or not this application is meant to act as a server to host the database (some behavior gets changed slightly).
   */
  public static boolean isServer(){
    return server;
  }
  /**
   * Resolves a string against another path.
   * Environment variables enclosed by {@code %} are expanded.
   */
  public static Path resolve(Path base, String s){
    try{
      if (s==null){
        return base;
      }
      s = expandEnvironmentVariables(s).replace('\\',java.io.File.separatorChar).replace('/',java.io.File.separatorChar);
      if (base==null){
        return Paths.get(s);
      }
      return base.resolve(s);
    }catch(Throwable t){
      Logger.logAsync("Error occurred while resolving Path.", t);
      return null;
    }
  }
  /**
   * Replaces matches of the regular expression {@code %.*?%} with the corresponding environment variable.
   */
  public static String expandEnvironmentVariables(String str){
    int len = str.length();
    StringBuilder out = new StringBuilder(len+16);
    StringBuilder var = new StringBuilder();
    String tmp;
    boolean env = false;
    char c;
    for (int i=0;i<len;++i){
      c = str.charAt(i);
      if (c=='%'){
        if (env){
          tmp = System.getenv(var.toString());
          if (tmp!=null){
            out.append(tmp);
            tmp = null;
          }
          var.setLength(0);
        }
        env^=true;
      }else if (env){
        var.append(c);
      }else{
        out.append(c);
      }
    }
    return out.toString();
  }
}