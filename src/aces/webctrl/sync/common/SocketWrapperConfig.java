/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.sync.common;
import java.nio.*;
/**
 * Provides tuning parameters to the {@code SocketWrapper} class.
 */
public abstract class SocketWrapperConfig {
  /** @return the timeout to wait for data packets. */
  public abstract long getTimeout();
  /**
   * Invoked whenever bytes are written to a socket.
   * May be used to capture all raw data packets being transmitted.
   * @param IP - is the IP address of the remote host.
   * @param buf - contains raw bytes read from the socket.
   */
  public void onWrite(String IP, ByteBuffer buf){}
  /**
   * Invoked whenever bytes are read from a socket.
   * May be used to capture all raw data packets being received.
   * @param IP - is the IP address of the remote host.
   * @param buf - contains raw bytes read from the socket.
   */
  public void onRead(String IP, ByteBuffer buf){}
}