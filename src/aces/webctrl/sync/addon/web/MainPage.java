/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.sync.addon.web;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import aces.webctrl.sync.addon.Utility;
import aces.webctrl.sync.addon.core.*;
import aces.webctrl.sync.common.*;
import java.util.*;
public class MainPage extends SecureServlet {
  private volatile static String html = null;
  public MainPage(){
    super(Collections.singleton("view_administrator_only"));
  }
  @Override public void init() throws ServletException {
    try{
      html = Utility.loadResourceAsString("aces/webctrl/sync/addon/html/MainPage.html").replaceAll(
        "(?m)^[ \\t]++",
        ""
      ).replace(
        "__PREFIX__",
        Initializer.getPrefix()
      ).replace(
        "__VERSION__",
        'v'+Config.VERSION
      ).replace(
        "type=\"text/css\" href=\"../../../../../../root/webapp/css/main.css\"",
        "type=\"text/css\" href=\"css/main.css\""
      );
    }catch(Throwable e){
      if (e instanceof ServletException){
        throw (ServletException)e;
      }else{
        throw new ServletException(e);
      }
    }
  }
  @Override public void process(final HttpServletRequest req, final HttpServletResponse res) throws Throwable {
    final PrintWriter out = res.getWriter();
    if (req.getParameter("status")!=null){
      res.setContentType("text/plain");
      final Key k = ClientConfig.databaseKey;
      out.print(k==null?"NULL":k.getHashString());
      out.print(';');
      out.print(ClientConfig.getNextCronString());
      out.print(';');
      out.print(Initializer.nextCronString);
      out.print(';');
      out.print(Initializer.getStatus());
    }else if (req.getParameter("downloadLog")!=null){
      res.setContentType("application/octet-stream");
      res.setHeader("Content-Disposition","attachment;filename=\"log.txt\"");
      Logger.transferTo(out);
    }else if (req.getParameter("enableRemoval")!=null){
      //Initializer.enableAddonRemoval(0);
    }else if (req.getParameter("disableRemoval")!=null){
      //Initializer.disableAddonRemoval(0);
    }else if (req.getParameter("resetKey")!=null){
      ClientConfig.databaseKey = null;
    }else if (req.getParameter("config")!=null){
      final String host = req.getParameter("host");
      final String port = req.getParameter("port");
      final String timeout = req.getParameter("timeout");
      final String deleteLog = req.getParameter("deleteLog");
      final String syncSchedule = req.getParameter("syncSchedule");
      if (host==null || port==null || timeout==null || deleteLog==null || syncSchedule==null){
        res.setStatus(400);
      }else{
        {
          final String connectionKey = req.getParameter("connectionKey");
          if (connectionKey!=null && connectionKey.length()>0){
            try{
              ClientConfig.connectionKey = Long.parseUnsignedLong(connectionKey, 16);
            }catch(Throwable t){
              ClientConfig.connectionKey = 0;
            }
          }
        }
        try{
          final int portNum = Integer.parseInt(port);
          final long timeoutNum = Long.parseLong(timeout);
          final long deleteLogNum = Long.parseLong(deleteLog);
          ClientConfig.setCronExpression(syncSchedule);
          if (deleteLogNum<ClientConfig.deleteLogAfter){
            Logger.trim(deleteLogNum);
          }
          ClientConfig.deleteLogAfter = deleteLogNum;
          ClientConfig.timeout = timeoutNum;
          ClientConfig.ipLock.readLock().lock();
          boolean changeIP = !host.equals(ClientConfig.host) || portNum!=ClientConfig.port;
          ClientConfig.ipLock.readLock().unlock();
          if (changeIP){
            ClientConfig.ipLock.writeLock().lock();
            ClientConfig.host = host;
            ClientConfig.port = portNum;
            ClientConfig.ipLock.writeLock().unlock();
          }
          res.setContentType("text/plain");
          out.print(ClientConfig.getNextCronString());
        }catch(Throwable e){
          res.setStatus(400);
        }
      }
    }else{
      res.setContentType("text/html");
      ClientConfig.ipLock.readLock().lock();
      String host = ClientConfig.host;
      int port = ClientConfig.port;
      ClientConfig.ipLock.readLock().unlock();
      if (host==null){
        host = "";
      }
      String expr = ClientConfig.getCronExpression();
      if (expr==null){
        expr = "";
      }
      out.print(html.replace(
        "__HOST__",
        Utility.escapeJS(host)
      ).replace(
        "__PORT__",
        String.valueOf(port)
      ).replace(
        "__TIMEOUT__",
        String.valueOf(ClientConfig.timeout)
      ).replace(
        "__DELETE_LOG__",
        String.valueOf(ClientConfig.deleteLogAfter)
      ).replace(
        "__SYNC_SCHEDULE__",
        expr
      ).replace(
        "__NEXT_SYNC_LATENT__",
        ClientConfig.getNextCronString()
      ).replace(
        "__NEXT_SYNC_ACTUAL__",
        Initializer.nextCronString
      ));
    }
  }
}