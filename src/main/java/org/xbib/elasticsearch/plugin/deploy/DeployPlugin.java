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
package org.xbib.elasticsearch.plugin.deploy;

import org.elasticsearch.action.ActionModule;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestModule;
import org.xbib.elasticsearch.action.deploy.DeployAction;
import org.xbib.elasticsearch.action.deploy.TransportDeployAction;
import org.xbib.elasticsearch.module.deploy.DeployModule;
import org.xbib.elasticsearch.module.deploy.DeployService;
import org.xbib.elasticsearch.rest.deploy.RestDeployAction;

import java.util.ArrayList;
import java.util.Collection;

/**
 * The deploy plugin is initialized at node startup by Elasticsearch
 */
public class DeployPlugin extends Plugin {

    public final static String NAME = "deploy";

    private final Settings settings;

    public DeployPlugin(Settings settings) {
        this.settings = settings;
    }

    @Override
    public String name() {
        return NAME + "-" + Build.getInstance().getVersion()
                + "-" + Build.getInstance().getShortHash()
                + " " + Build.getInstance().getPlatform();
    }

    @Override
    public String description() {
        return "Deploy plugin";
    }

    @Override
    public Collection<Module> nodeModules() {
        Collection<Module> modules = new ArrayList<>();
        if (settings.getAsBoolean("plugins.deploy.enabled", true)) {
            modules.add(new DeployModule());
        }
        return modules;
    }

    @Override
    public Collection<Class<? extends LifecycleComponent>> nodeServices() {
        Collection<Class<? extends LifecycleComponent>> services = new ArrayList<>();
        if (settings.getAsBoolean("plugins.deploy.enabled", true)) {
            services.add(DeployService.class);
        }
        return services;
    }

    public void onModule(ActionModule module) {
        module.registerAction(DeployAction.INSTANCE, TransportDeployAction.class);
    }

    public void onModule(RestModule module) {
        module.addRestAction(RestDeployAction.class);
    }

}
