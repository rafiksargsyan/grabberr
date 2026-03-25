package com.rsargsyan.grabberr.main_ctx.adapters.driving.controllers;

public class UserContextHolder {
  private static final ThreadLocal<UserContext> userContext = new ThreadLocal<>();

  public static void set(UserContext ctx) { userContext.set(ctx); }
  public static UserContext get() { return userContext.get(); }
  public static void clear() { userContext.remove(); }
}
