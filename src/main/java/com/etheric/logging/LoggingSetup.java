package com.etheric.logging;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.logging.Handler;
import java.util.logging.Logger;

@ApplicationScoped
public class LoggingSetup {

    void onStart(@Observes StartupEvent event) {
        SecretMaskingLogFilter filter = new SecretMaskingLogFilter();
        Logger root = Logger.getLogger("");
        root.setFilter(filter);
        for (Handler handler : root.getHandlers()) {
            handler.setFilter(filter);
        }
    }
}
