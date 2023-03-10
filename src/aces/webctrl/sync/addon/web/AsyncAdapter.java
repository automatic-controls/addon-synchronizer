/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.sync.addon.web;
import java.io.IOException;
import javax.servlet.*;
/**
 * Convenience class so that implementations don't need to override all methods.
 * <ul>
 * <li>{@code public void onComplete(AsyncEvent e) throws IOException;}</li>
 * <li>{@code public void onTimeout(AsyncEvent e) throws IOException;}</li>
 * <li>{@code public void onError(AsyncEvent e) throws IOException;}</li>
 * <li>{@code public void onStartAsync(AsyncEvent e) throws IOException;}</li>
 * </ul>
 */
public class AsyncAdapter implements AsyncListener {
  @Override public void onComplete(AsyncEvent e) throws IOException {}
  @Override public void onTimeout(AsyncEvent e) throws IOException {}
  @Override public void onError(AsyncEvent e) throws IOException {}
  @Override public void onStartAsync(AsyncEvent e) throws IOException {}
}