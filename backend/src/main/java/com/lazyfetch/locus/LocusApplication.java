package com.lazyfetch.locus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan("com.lazyfetch.locus.data.mf.entity")
public class LocusApplication {

	public static void main(String[] args) {
		SpringApplication.run(LocusApplication.class, args);
	}

}
