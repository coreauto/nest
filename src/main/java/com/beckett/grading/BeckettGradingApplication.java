package com.beckett.grading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;


@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
@ComponentScan(basePackages = {"com.beckett.grading", "com.beckett.common"})
@EntityScan(basePackages = { "com.beckett.user.entity", "com.beckett.order.entity",
        "com.beckett.shdsvc.entity", "com.beckett.admin.entity", "com.beckett.location.entity",
        "com.beckett.customer.entity","com.beckett.location.entity",
"com.beckett.certificate.entity", "com.beckett.customer.entity", "com.beckett.grading.entity"})
@EnableCaching
@EnableWebMvc
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = { "com.beckett.user.repository",
        "com.beckett.order.repository", "com.beckett.shdsvc.repository",
        "com.beckett.admin.repository", "com.beckett.location.repository",
        "com.beckett.customer.repository", ",com.beckett.locations.repository", "com.beckett.certificate.repository",
        "com.beckett.customer.repository", "com.beckett.grading.repository"})
public class BeckettGradingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BeckettGradingApplication.class, args);
    }
}
