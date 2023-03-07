/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.sync.addon.core;
import java.nio.channels.*;
public abstract class Handler<T> implements CompletionHandler<T,Void> {
  @Override public void failed(Throwable e, Void v){
    Initializer.status = e.getClass().getSimpleName()+": "+e.getMessage();
    Initializer.disconnect(e,true,true);
  }
}