package com.example.auth_service.security;

import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

@Component
public class FilterRegistrationLogger implements CommandLineRunner {

    @Autowired
    private ServletContext servletContext;

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("[FilterRegistrationLogger] Listing ServletContext filter registrations:");
        try {
            Map<String, ? extends FilterRegistration> regs = servletContext.getFilterRegistrations();
            if (regs == null || regs.isEmpty()) {
                System.out.println("[FilterRegistrationLogger] no servlet filter registrations found via ServletContext");
            } else {
                regs.forEach((name, reg) -> {
                    System.out.println("[FilterRegistration] name=" + name + ", class=" + reg.getClass().getName());
                });
            }
        } catch (Throwable t) {
            System.out.println("[FilterRegistrationLogger] error reading servlet registrations: " + t.getMessage());
        }

        System.out.println("[FilterRegistrationLogger] Listing FilterRegistrationBean beans in ApplicationContext:");
        try {
            String[] beanNames = applicationContext.getBeanNamesForType(org.springframework.boot.web.servlet.FilterRegistrationBean.class);
            if (beanNames == null || beanNames.length == 0) {
                System.out.println("[FilterRegistrationLogger] no FilterRegistrationBean beans found");
            } else {
                for (String beanName : beanNames) {
                    Object bean = applicationContext.getBean(beanName);
                    System.out.println("[FilterRegistrationBean] bean=" + beanName + ", class=" + bean.getClass().getName());
                }
            }
        } catch (Throwable t) {
            System.out.println("[FilterRegistrationLogger] error listing FilterRegistrationBean beans: " + t.getMessage());
        }
    }
}


