package com.etheric.logging;

import com.etheric.util.SecretMasker;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * JUL filter that redacts secrets from log messages and parameters before they are written.
 */
public class SecretMaskingLogFilter implements Filter {

    @Override
    public boolean isLoggable(LogRecord logRecord) {
        if (logRecord.getMessage() != null) {
            logRecord.setMessage(SecretMasker.mask(logRecord.getMessage()));
        }
        Object[] parameters = logRecord.getParameters();
        if (parameters != null) {
            Object[] masked = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                Object parameter = parameters[i];
                if (parameter instanceof String value) {
                    masked[i] = SecretMasker.mask(value);
                } else {
                    masked[i] = parameter;
                }
            }
            logRecord.setParameters(masked);
        }
        return true;
    }
}
