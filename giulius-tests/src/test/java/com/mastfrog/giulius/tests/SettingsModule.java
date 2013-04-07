package com.mastfrog.giulius.tests;

import com.mastfrog.settings.Settings;
import com.google.inject.AbstractModule;

/**
 *
 * @author Tim Boudreau
 */
final class SettingsModule extends AbstractModule {
    static Settings settings;
    private Settings s;
    SettingsModule (Settings settings) {
        SettingsModule.settings = settings;
        this.s = settings;
    }

    SettingsModule() {
        throw new RuntimeException ("Non-settings constructor should not be invoked");
    }

    @Override
    protected void configure() {
        String val = s == null ? "No settings passed to constructor" : s.getString("settingsVal");
        bind (String.class).toInstance(val);
    }
}
