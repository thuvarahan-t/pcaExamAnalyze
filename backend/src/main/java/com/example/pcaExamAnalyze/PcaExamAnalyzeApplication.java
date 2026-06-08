package com.example.pcaExamAnalyze;

import com.example.pcaExamAnalyze.config.DotenvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PcaExamAnalyzeApplication {

	public static void main(String[] args) {
		DotenvLoader.load(); // load .env into system properties before Spring resolves placeholders
		SpringApplication.run(PcaExamAnalyzeApplication.class, args);
	}

}
