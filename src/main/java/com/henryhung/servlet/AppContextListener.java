package com.henryhung.servlet;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class AppContextListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        Initializer.main(null);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Cleanup code
    }
}
