/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.sync.addon.core;
import aces.webctrl.sync.common.Logger;
import java.io.*;
import java.nio.file.*;
import com.controlj.green.addonsupport.web.auth.AuthenticationManager;
import com.controlj.green.extensionsupport.Extension;
import com.controlj.green.webserver.*;
/**
 * Namespace which contains methods to access small sections of a few internal WebCTRL APIs.
 */
public class HelperAPI {
  /**
   * Specifies whether methods of this API should log stack traces generated from errors.
   */
  public static volatile boolean logErrors = true;
  /**
   * Disables an add-on with the given name.
   * @param name is used to identify the add-on.
   * @return {@code true} on success; {@code false} on failure.
   */
  public static boolean disableAddon(String name){
    try{
      TomcatServer server = TomcatServerSingleton.get();
      if (server==null){
        return false;
      }
      AddOn addon = null;
      for (AddOn x:server.scanForAddOns()){
        if (x.getName().equals(name)){
          addon = x;
          break;
        }
      }
      if (addon==null){
        return true;
      }
      server.disableAddOn(addon);
      return true;
    }catch(Throwable t){
      if (logErrors){ Logger.log(t); }
      return false;
    }
  }
  /**
   * Enables an add-on with the given name.
   * @param name is used to identify the add-on.
   * @return {@code true} on success; {@code false} on failure.
   */
  public static boolean enableAddon(String name){
    try{
      TomcatServer server = TomcatServerSingleton.get();
      if (server==null){
        return false;
      }
      AddOn addon = null;
      for (AddOn x:server.scanForAddOns()){
        if (x.getName().equals(name)){
          addon = x;
          break;
        }
      }
      if (addon==null){
        return false;
      }
      server.enableAddOn(addon);
      return true;
    }catch(Throwable t){
      if (logErrors){ Logger.log(t); }
      return false;
    }
  }
  public static File getAddonsDirectory(){
    try{
      TomcatServer server = TomcatServerSingleton.get();
      return server==null?null:server.getAddOnsDir();
    }catch(Throwable t){
      if (logErrors){ Logger.log(t); }
      return null;
    }
  }
  public static boolean deployAddon(File f){
    try{
      TomcatServer server = TomcatServerSingleton.get();
      if (server==null){
        return false;
      }
      server.deployAddOn(f);
      return true;
    }catch(Throwable t){
      if (logErrors){ Logger.log(t); }
      return false;
    }
  }
  /**
   * Removes an add-on with the given name.
   * @param name is used to identify the add-on.
   * @param removeData specifies whether to remove data associated to the add-on.
   * @return {@code true} on success; {@code false} on failure.
   */
  public static boolean removeAddon(String name, boolean removeData){
    try{
      TomcatServer server = TomcatServerSingleton.get();
      if (server==null){
        return false;
      }
      AddOn addon = null;
      for (AddOn x:server.scanForAddOns()){
        if (x.getName().equals(name)){
          addon = x;
          break;
        }
      }
      if (addon==null){
        return false;
      }
      server.removeAddOn(addon, removeData);
      return true;
    }catch(Throwable t){
      if (logErrors){ Logger.log(t); }
      return false;
    }
  }
  /**
   * Activates the specified {@code WebOperatorProvider}.
   * @param addonFile specifies the location of the .addon file to activate.
   * @return an {@code Extension} object matching the given {@code addonFile}, or {@code null} if the addon cannot be found or if any error occurs.
   */
  public static Extension activateWebOperatorProvider(Path addonFile){
    try{
      AuthenticationManager auth = new AuthenticationManager();
      for (Extension e:auth.findWebOperatorProviders()){
        if (Files.isSameFile(e.getSourceFile().toPath(), addonFile)){
          auth.activateProvider(e);
          return e;
        }
      }
    }catch(Throwable t){
      if (logErrors){ Logger.log(t); }
    }
    return null;
  }
  /**
   * Activates the default {@code WebOperatorProvider}.
   * @return whether this method executed successfully.
   */
  public static boolean activateDefaultWebOperatorProvider(){
    try{
      new AuthenticationManager().activateProvider(null);
      return true;
    }catch(Throwable t){
      if (logErrors){ Logger.log(t); }
      return false;
    }
  }
}