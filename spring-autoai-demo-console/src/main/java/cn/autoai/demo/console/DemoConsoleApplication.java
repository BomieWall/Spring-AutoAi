package cn.autoai.demo.console;

import cn.autoai.core.spring.EnableAutoAi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

/**
 * Console demo entry point.
 * Demonstrates how to use the Spring-AutoAi framework in non-Web projects
 */
@SpringBootApplication
@EnableAutoAi
public class DemoConsoleApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DemoConsoleApplication.class);
        ApplicationContext context = application.run(args);

        // Get ConsoleService from Spring container and start it
        // This is the standard dependency injection approach, avoiding direct use of CommandLineRunner
        ConsoleService consoleService = context.getBean(ConsoleService.class);

        try {
            consoleService.start();
        } catch (Exception e) {
            System.err.println("Console service startup failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
