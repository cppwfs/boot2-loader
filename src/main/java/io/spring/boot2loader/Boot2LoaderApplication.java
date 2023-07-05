package io.spring.boot2loader;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableBatchProcessing
public class Boot2LoaderApplication {

	public static void main(String[] args) {
		SpringApplication.run(Boot2LoaderApplication.class, args);
	}

}
