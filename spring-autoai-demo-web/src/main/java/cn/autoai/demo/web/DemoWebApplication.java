package cn.autoai.demo.web;

import cn.autoai.core.annotation.AutoAiToolScan;
import cn.autoai.core.spring.EnableAutoAi;
import cn.autoai.demo.web.controller.OrderManagementController;
import cn.autoai.demo.web.controller.UserManagementController;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


/**
 * Spring-AutoAi Web demo application entry point.
 *
 * Model configuration has been moved to application.yml, defining models through configuration file:
 * - adapter: Model adapter type (BigModel/MiniMax/OpenAI)
 * - model: Model name
 * - api-key: API key
 * - base-url: API base URL (optional)
 *
 * Web functionality is controlled by autoai.web.enabled configuration in application.yml:
 * - enabled: true means enable Web interface (default)
 * - enabled: false means disable Web interface
 */
@SpringBootApplication
@EnableAutoAi
@AutoAiToolScan(classes = {UserManagementController.class,DemoTools.class})
public class DemoWebApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DemoWebApplication.class);
        application.run(args);
    }
}