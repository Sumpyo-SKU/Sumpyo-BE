package com.app.sumpyo_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SumpyoBeApplication {

  public static void main(String[] args) {
    SpringApplication.run(SumpyoBeApplication.class, args);
  }

}
