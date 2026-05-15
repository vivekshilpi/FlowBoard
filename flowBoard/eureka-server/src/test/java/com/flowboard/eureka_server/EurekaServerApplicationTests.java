package com.flowboard.eureka_server;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class EurekaServerApplicationTests {

	@Test
	void applicationTestClassRuns() {
	}

	@Test
	void mainStartsAndStopsApplication() {
		try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
			ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
			springApplication.when(() -> SpringApplication.run(EurekaServerApplication.class, new String[0]))
					.thenReturn(context);

			assertThatCode(() -> {
				EurekaServerApplication.main(new String[0]);
				EurekaServerApplication.shutdown();
			}).doesNotThrowAnyException();
		}
	}

}
