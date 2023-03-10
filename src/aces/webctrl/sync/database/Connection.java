/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.sync.database;
import java.util.concurrent.atomic.*;
import java.nio.channels.*;
import java.security.spec.*;
import javax.crypto.*;
import aces.webctrl.sync.common.*;
public class Connection implements Comparable<Connection> {
  private final static AtomicLong nextID = new AtomicLong();
  private final long ID = nextID.getAndIncrement();
  protected volatile SocketWrapper wrap;
  private volatile boolean initialized = false;
  private final AtomicBoolean closed = new AtomicBoolean();
  public Connection(AsynchronousSocketChannel ch){
    wrap = new SocketWrapper(ch);
    Logger.logAsync(wrap.getIP()+": Establishing connection...");
  }
  /**
   * @return whether or not initialization has been completed successfully.
   */
  public boolean isInitialized(){
    return initialized;
  }
  /**
   * Provides an ordering on {@code Connection} objects.
   */
  @Override public int compareTo(Connection con){
    if (ID==con.ID){
      return 0;
    }else if (ID>con.ID){
      return 1;
    }else{
      return -1;
    }
  }
  /**
   * Terminates the connection.
   * @param remove specifies whether to remove this {@code Connection} from the connection list.
   * @return {@code true} on success; {@code false} if an error occurs while closing the socket.
   */
  public boolean close(boolean remove){
    if (closed.compareAndSet(false,true)){
      if (remove){
        Connections.remove(this);
      }
      Logger.logAsync(wrap.getIP()+": Connection closed.");
      if (wrap.isClosed()){
        return true;
      }else{
        return wrap.close();
      }
    }
    return true;
  }
  /**
   * Convenience {@code CompletionHandler} class for having a default {@code failed} method.
   */
  abstract class Handler<T> implements CompletionHandler<T,Void> {
    public abstract void func(T ret);
    @Override public void completed(T ret, Void v){
      try{
        func(ret);
      }catch(Throwable t){
        Logger.logAsync("Error occurred in Handler.", t);
      }
    }
    @Override public void failed(Throwable e, Void v){
      close(true);
    }
  }
  public void init(){
    final Key k = Keys.getPreferredKey();
    SerializationStream s = new SerializationStream(k.length(false)+Config.VERSION_RAW.length+4);
    s.write(Config.VERSION_RAW);
    k.serialize(s,false);
    //Write the application version and public key to the client
    wrap.writeBytes(s.data, null, new Handler<Void>(){
      public void func(Void v){
        //Read and decrypt the client's temporary public key
        wrap.readBytes(16384, null, new Handler<byte[]>(){
          public void func(byte[] tmpPublicKey){
            try{
              tmpPublicKey = k.decrypt(tmpPublicKey);
              //Generate and encrypt a new symmetric key for this session
              final byte[] symmetricKey = new byte[16];
              Database.entropy.nextBytes(symmetricKey);
              Cipher cipher = Cipher.getInstance(Database.CIPHER);
              synchronized (Keys.keyFactory){
                cipher.init(Cipher.ENCRYPT_MODE, Keys.keyFactory.generatePublic(new X509EncodedKeySpec(tmpPublicKey)));
              }
              //Send the encrypted symmetric key to the client
              wrap.writeBytes(cipher.doFinal(symmetricKey), null, new Handler<Void>(){
                public void func(Void v){
                  //Now encryption has been successfully setup
                  wrap.setCipher(new StreamCipher(symmetricKey));
                  //Verify the client possesses the secret connection key
                  wrap.readBytes(16, null, new Handler<byte[]>(){
                    public void func(byte[] arr){
                      final boolean hasKey = arr.length==8 && new SerializationStream(arr).readLong()==Config.connectionKey;
                      wrap.write(hasKey?Protocol.SUCCESS:Protocol.FAILURE, null, new Handler<Void>(){
                        public void func(Void v){
                          if (hasKey){
                            //Ask the client whether it is ready to synchronize addons
                            wrap.read(null, new Handler<Byte>(){
                              public void func(Byte b){
                                if (b==Protocol.CONTINUE){
                                  Logger.logAsync(wrap.getIP()+": Sync initiated.");
                                  //Synchronize addons
                                  wrap.writePath(Main.getSyncs(), null, new Handler<Boolean>(){
                                    public void func(Boolean b){
                                      Logger.logAsync(wrap.getIP()+(b?": Sync successful.":": Sync failed."));
                                      close(true);
                                    }
                                    @Override public void failed(Throwable e, Void v){
                                      Logger.logAsync(wrap.getIP()+": Sync failed.", e);
                                      super.failed(e,v);
                                    }
                                  }, null, null);
                                }else{
                                  close(true);
                                }
                              }
                            });
                          }else{
                            close(true);
                          }
                        }
                      });
                    }
                  });
                }
              });
            }catch(Throwable e){
              Logger.logAsync(wrap.getIP()+": Cipher negotiation error occurred.",e);
              close(true);
            }
          }
        });
      }
    });
  }
}