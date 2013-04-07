/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.giulius;

import com.google.common.collect.Maps;
import com.google.inject.Module;
import com.mastfrog.guicy.annotations.Namespace;
import com.mastfrog.util.ConfigurationError;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.util.Checks;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builder for Dependencies.  Allows for adding Settings for various
 * namespaces programmatically.
 *
 * @author Tim Boudreau
 */
public final class DependenciesBuilder {
    private Map<String, List<SettingsBuilder>> settingsForNamespace = Maps.newHashMapWithExpectedSize(5);
    private List<Module> modules = new LinkedList<>();
    private Set<File> locations = new LinkedHashSet<>();
    /**
     * Get the list of namespaces this DependenciesBuilder will bind settings
     * for.  If you have called <code>addDefaultSettings()</code> this will
     * include any namespaces generated from annotations, which are defined
     * as the concatenation of all files named <code>com/mastfrog/namespaces.list</code>
     * on the classpath.
     * @return A set of namespaces
     */
    public Set<String> namespaces() {
        return new HashSet<>(settingsForNamespace.keySet());
    }

    /**
     * Add modules to be used when creating the Injector
     * @param modules Some modules
     * @return this
     */
    public DependenciesBuilder add(Module... modules) {
        Checks.notNull("modules", modules);
        this.modules.addAll(Arrays.asList(modules));
        return this;
    }
    
    /**
     * Add a folder on disk to look in for configuration files
     * 
     * @param loc A folder on disk
     * @return 
     */
    public DependenciesBuilder addDefaultLocation(File loc) {
        if (loc.exists() && !loc.isDirectory()) {
            throw new ConfigurationError("Not a directory: " + loc);
        }
        locations.add(loc);
        return this;
    }

    private void addLocations(SettingsBuilder sb) {
        for (File loc : locations) {
            sb.addLocation(loc);
        }
    }

    /**
     * Add a namespace, causing all of the default locations to be used.
     * The namespace's settings will contain environment variables, system
     * properties and the contents of any generated-$NAMESPACE.properties and
     * $NAMESPACE.properties files in the default location on the classpath.
     * 
     * @param name The name of the namespace.
     * @return this
     * @throws IOException 
     */
    public DependenciesBuilder addNamespace(String name) throws IOException {
        if (!settingsForNamespace.containsKey(name)) {
            File home = new File(System.getProperty("user.home"));
            File f = new File (home, name + SettingsBuilder.DEFAULT_EXTENSION);
            SettingsBuilder sb = new SettingsBuilder(name)
                    .addEnv()
                    .addSystemProperties()
                    .addGeneratedDefaultsFromClasspath()
                    .addDefaultsFromClasspath();
            if (f.exists()) {
                sb.add(f);
            }
            addLocations(sb);
            add(sb.build(), name);
        } else {
            throw new IllegalArgumentException("Already added '" + name + "'");
        }
        return this;
    }

    /**
     * Add the default settings (see SettingsBuilder.createDefault()), and
     * default settings for any namespaces found in /com/mastfrog/namespaces.list
     * files anywhere on the classpath (these are generated from the &#064;Defaults
     * annotation).
     * 
     * @return this
     * @throws IOException If loading settings fails
     */
    public DependenciesBuilder addDefaultSettings() throws IOException {
        Set<String> namespaces = new HashSet<>(settingsForNamespace.keySet());
        namespaces.addAll(Dependencies.loadNamespaceListsFromClasspath());
        namespaces.add(Namespace.DEFAULT);
        for (String ns : namespaces) {
            Settings s;
            if (Namespace.DEFAULT.equals(ns)) {
                SettingsBuilder sb = new SettingsBuilder().addDefaultLocations();
                addLocations(sb);
                s = sb.build();
            } else {
                SettingsBuilder sb = new SettingsBuilder(ns).addDefaultLocations();
                addLocations(sb);
                s = sb.build();
            }
            add(s, ns);
        }
        return this;
    }
    
    public List<SettingsBuilder> getSettings(String ns) {
        return settingsForNamespace.get(ns);
    }

    /**
     * Add a Settings tied to a specific namespace.  Note that if you have called
     * <code>addDefaultSettings()</code>, this will merge these settings with
     * any settings files for that namespace which are on the classpath.
     * 
     * @param settings The settings
     * @param namespace The namespace, referenced by &#064;Namespace annotations
     * on the related classes
     * @return this
     */
    public DependenciesBuilder add(Settings settings, String namespace) {
        Checks.notNull("settings", settings);
        Checks.notNull("namespace", namespace);
        Checks.notEmpty("namespace", namespace);
        List<SettingsBuilder> l = settingsForNamespace.get(namespace);
        if (l == null) {
            l = new LinkedList<>();
            settingsForNamespace.put(namespace, l);
        }
        SettingsBuilder sb = new SettingsBuilder(namespace).add(settings);
        l.add(sb);
        return this;
    }
    
    private boolean useMutableSettings;
    
    /**
     * If called, bind a MutableSettings (has setters) rather than the default
     * (mutable settings are bound, but are created on the fly and any changes
     * are not shared with other code).
     * @return this
     */
    public DependenciesBuilder useMutableSettings() {
        useMutableSettings = true;
        return this;
    }

    private Map<String, Settings> collapse() throws IOException {
        Map<String, Settings> result = new HashMap<>();
        for (Map.Entry<String, List<SettingsBuilder>> e : settingsForNamespace.entrySet()) {
            if (e.getValue().size() == 1) {
                result.put(e.getKey(), e.getValue().iterator().next().build());
            } else {
                SettingsBuilder sb = new SettingsBuilder(e.getKey());
                for (SettingsBuilder s : e.getValue()) {
                    sb.add(s);
                }
                if (useMutableSettings) {
                    result.put(e.getKey(), sb.buildMutableSettings());
                } else {
                    result.put(e.getKey(), sb.build());
                }
            }
        }
        return result;
    }

    /**
     * Build a dependencies object.
     * 
     * @return
     * @throws IOException 
     */
    public Dependencies build() throws IOException {
        return new Dependencies(collapse(), modules.toArray(new Module[modules.size()]));
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MODULES:\n");
        for (Module m : modules) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(m).append('\n');
        }
        sb.append("NAMESPACES:\n");
        for (String ns : settingsForNamespace.keySet()) {
            sb.append(ns).append(',');
        }
        sb.append("\n");
        for (String ns : settingsForNamespace.keySet()) {
            sb.append(ns).append(": ").append("\n").append ("  ").append(settingsForNamespace.get(ns)).append("\n");
        }
        
        return sb.toString();
    }
}
