package com.rsargsyan.grabberr.main_ctx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class GrabberrApplication {
  public static void main(String[] args) {
    SpringApplication.run(GrabberrApplication.class, args);
  }
}
