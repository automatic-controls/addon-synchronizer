/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.sync.common;
/**
 * This class contains byte constants used to transfer data.
 */
public class Protocol {
  /** Used for automatically controlling the value of byte constants. */
  private static byte ID = 0;
  /**
   * Indicates the hash comparison was successful.
   * Used to prevent data corruption (random or otherwise).
   */
  public final static byte[] HASH_COMPARISON_SUCCESS_ARRAY = new byte[]{32, 2, -83, -106};
  /**
   * Indicates {@link StreamCipher#hash(int)} failed to match the expected result.
   * Possible causes include random corruption or an attempt to initiate a man-in-the-middle attack.
   * Note random data corruption is unlikely since the TCP/IP protocol is generally reliable.
   */
  public final static byte[] HASH_COMPARISON_FAILURE_ARRAY = new byte[]{34, -103, -107, 44};
  /**
   * Indicates the hash comparison was successful.
   * Used to prevent data corruption (random or otherwise).
   */
  public final static byte HASH_COMPARISON_SUCCESS = ++ID;
  /**
   * Indicates {@link StreamCipher#hash(int)} failed to match the expected result.
   * Possible causes include random corruption or an attempt to initiate a man-in-the-middle attack.
   * Note random data corruption is unlikely since the TCP/IP protocol is generally reliable.
   */
  public final static byte HASH_COMPARISON_FAILURE = ++ID;
  /**
   * Indicates success.
   */
  public final static byte SUCCESS = ++ID;
  /**
   * Indicates partial success.
   */
  public final static byte PARTIAL_SUCCESS = ++ID;
  /**
   * Indicates failure.
   */
  public final static byte FAILURE = ++ID;
  /**
   * Indicates the end-of-file has been reached.
   */
  public final static byte EOF = ++ID;
  /**
   * Indicates there are more bytes to be send.
   */
  public final static byte CONTINUE = ++ID;
  /**
   * Indicates there is nothing left to do.
   */
  public final static byte NO_FURTHER_INSTRUCTIONS = ++ID;
  /**
   * Indicates there was an error reading or writing to a local file.
   */
  public final static byte FILE_ERROR = ++ID;
  /**
   * Indicates a resource does not exist.
   */
  public final static byte DOES_NOT_EXIST = ++ID;
  /**
   * Indicates a given {@code Path} object is a file.
   */
  public final static byte FILE_TYPE = ++ID;
  /**
   * Indicates a given {@code Path} object is a folder.
   */
  public final static byte FOLDER_TYPE = ++ID;
}