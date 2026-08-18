package com.etheric.admin;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Resolves admin console UI strings for a locale.
 */
public class AdminConsoleI18n {

    private final AdminConsoleLocale locale;
    private final ResourceBundle bundle;

    public AdminConsoleI18n(AdminConsoleLocale locale, ResourceBundle bundle) {
        this.locale = locale;
        this.bundle = bundle;
    }

    public AdminConsoleLocale locale() {
        return locale;
    }

    public String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException ignored) {
            return key;
        }
    }

    public String tabLabel(String activeTab) {
        if (activeTab == null || activeTab.isBlank()) {
            return activeTab;
        }
        return get("tab." + activeTab);
    }
}
