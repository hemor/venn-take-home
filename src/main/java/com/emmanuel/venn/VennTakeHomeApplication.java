package com.emmanuel.venn;

import com.emmanuel.venn.cli.BatchRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class VennTakeHomeApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(VennTakeHomeApplication.class);
        if (BatchRunner.isRequested(args)) {
            application.setWebApplicationType(WebApplicationType.NONE);
        }
        application.run(args);
    }
}
