package com.flowboard.eureka_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

	private static ConfigurableApplicationContext applicationContext;

	public static void main(String[] args) {

		applicationContext = SpringApplication.run(EurekaServerApplication.class, args);
	}

	static void shutdown() {
		if (applicationContext != null) {
			applicationContext.close();
			applicationContext = null;
		}
	}

}
