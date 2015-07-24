/*
 * Copyright 2014 Jörg Prante
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xbib.elasticsearch.module.deploy;

import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.plugins.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for managing plugins
 */
public class DeployRegistry {

    private final Map<String, Plugin> plugins;

    private final Map<String, Injector> pluginInjectors;

    public DeployRegistry() {
        this.plugins = new HashMap<>();
        this.pluginInjectors = new HashMap<>();
    }

    public void addPlugin(String name, Plugin plugin, Injector injector) {
        plugins.put(name, plugin);
        pluginInjectors.put(name, injector);
    }

    public Plugin getPlugin(String name) {
        return plugins.get(name);
    }

    public Plugin removePlugin(String name) {
        return plugins.remove(name);
    }

    public Map<String, Plugin> getPlugins() {
        return plugins;
    }

    public Injector getInjector(String name) {
        return pluginInjectors.get(name);
    }

    public String toString() {
        return plugins.toString();
    }

}
