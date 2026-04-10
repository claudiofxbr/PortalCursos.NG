package com.portalcursos.ng02;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.ApplicationPidFileWriter;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Ng02Application {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(Ng02Application.class);
		app.addListeners(new ApplicationPidFileWriter("portal.pid"));
		app.run(args);
	}

}
