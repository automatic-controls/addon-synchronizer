/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.sync.addon.web;
import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
/**
 * Ensures all data is sent and received securely with {@code UTF-8} character encoding.
 * The response will be committed with error code {@code 403} when an insecure connection is detected.
 * GET and POST requests are supported.
 */
public abstract class SecureServlet extends HttpServlet {
  private volatile Collection<String> roles = null;
  public volatile static boolean allowUnsecureProtocol = true;
  public SecureServlet(Collection<String> roles){
    this.roles = roles;
  }
  @Override public void doGet(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    doPost(req, res);
  }
  @Override public void doPost(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    try{
      req.setCharacterEncoding("UTF-8");
      res.setCharacterEncoding("UTF-8");
      if (allowUnsecureProtocol || req.isSecure()){
        if (roles!=null){
          for (String role:roles){
            if (!req.isUserInRole(role)){
              res.sendError(403, "You do not have the required permissions to view this page.");
              return;
            }
          }
        }
        process(req,res);
      }else{
        res.sendError(403, "You are using an insecure connection protocol.");
      }
    }catch(Throwable e){
      aces.webctrl.sync.common.Logger.logAsync("Error occurred in SecureServlet.", e);
      res.sendError(500);
    }
  }
  public abstract void process(HttpServletRequest req, HttpServletResponse res) throws Throwable;
}