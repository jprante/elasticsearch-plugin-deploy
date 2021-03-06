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

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.node.service.NodeService;
import org.elasticsearch.plugins.Plugin;
import org.xbib.classloader.uri.URIClassLoader;
import org.xbib.elasticsearch.plugin.deploy.DeployPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.elasticsearch.common.settings.Settings.settingsBuilder;

/**
 * The DeployService manages the plugin registry
 */
public class DeployService extends AbstractLifecycleComponent<DeployService> implements DeployableComponent<DeployService> {

    private final Injector injector;

    private final Environment environment;

    private final NodeService nodeService;

    private final ClassLoader deployClassLoader;

    private final DeployRegistry registry;

    @Inject
    public DeployService(Settings settings, Environment environment, Injector injector, NodeService nodeService,
                         DeployRegistry registry) {
        super(settings);
        this.injector = injector;
        this.environment = environment;
        this.nodeService = nodeService;
        this.deployClassLoader = getClass().getClassLoader();
        this.registry = registry;
    }

    @Override
    protected void doStart() throws ElasticsearchException {
        try {
            initializeInstalledPlugins();
        } catch (IOException e) {
            throw new ElasticsearchException(e.getMessage(), e);
        }
        nodeService.putAttribute("plugins",
                Strings.collectionToCommaDelimitedString(registry.getPlugins().keySet()));
        logger.info("started");
    }

    @Override
    protected void doStop() throws ElasticsearchException {
        nodeService.removeAttribute("plugins");
    }

    @Override
    protected void doClose() throws ElasticsearchException {
    }

    public DeployRegistry getRegistry() {
        return registry;
    }

    public void initializeInstalledPlugins() throws IOException {
        File dir = new File(environment.pluginsFile().toFile(), DeployPlugin.NAME + File.separator + "plugins");
        if (dir.isDirectory()) {
            logger.info("found plugin dir {}", dir.getAbsolutePath());
            File[] plugins = dir.listFiles();
            if (plugins != null) {
                for (File plugin : plugins) {
                    logger.info("found plugin {}", plugin.getAbsolutePath());
                    if (plugin.isDirectory()) {
                        add(plugin.getName(), plugin);
                    }
                }
            }
        }
    }

    /**
     * Add plugin to registry
     *
     * @param name the name to register the plugin under
     * @param path zip file or jar file
     * @throws java.io.IOException if method fails
     */
    public void add(String name, File path) throws IOException {
        // create new class loader for each plugin
        URIClassLoader classLoader = new URIClassLoader(deployClassLoader);
        // try to unpack zip
        path = tryUnpackArchive(path);
        // find all jars in archive
        Set<URI> jars = findJars(path);
        File descriptor = new File(path, "plugin-descriptor.properties");
        InputStream in = new FileInputStream(descriptor);
        Properties properties = new Properties();
        properties.load(in);
        String classname = properties.getProperty("classname");
        logger.debug("classname={} jars={}", classname, jars);
        Map<URI, String> pluginClassNames = new HashMap<>();
            for (URI jar : jars) {
                classLoader.addURI(jar);
                try {
                    classLoader.loadClass(classname);
                    pluginClassNames.put(jar, classname);
                    logger.debug("found classname in {}", jar);
                } catch (ClassNotFoundException e) {
                    // skip, next jar, do nothing
                }
            }
            for (URI uri : classLoader.getURIs()) {
                logger.info("class path member {}", uri);
            }
            // instantiate all plugins in this path, add them to registry
            for (Map.Entry<URI, String> entries : pluginClassNames.entrySet()) {
                // check for existing plugin
                if (registry.getPlugin(name) != null) {
                    logger.warn("old plugin {} exists", name);
                    Plugin oldPlugin = registry.getPlugin(name);
                    stopServices(registry.getInjector(name), oldPlugin);
                    logger.info("services stopped for plugin {}", name);
                    registry.removePlugin(name);
                }
                logger.info("instantiating plugin {}", name);
                Plugin plugin = instantiatePluginClass(entries.getValue(), classLoader);
                logger.info("processing modules for plugin {}", name);
                Injector injector = null;
                try {
                    injector = processModules(this.injector, plugin);
                    logger.info("modules processed for plugin {}, starting services...", name);
                    startServices(injector, plugin, settings, classLoader, entries.getKey().toURL());
                    logger.info("services started for plugin {}", name);
                } catch (Throwable t) {
                    logger.warn(t.getMessage(), t);
                }
                registry.addPlugin(name, plugin, injector);
            }
        logger.info("registry after adding = {}", registry);
        nodeService.putAttribute("plugins",
                Strings.collectionToCommaDelimitedString(registry.getPlugins().keySet()));
    }

    @SuppressWarnings("unchecked")
    private Plugin instantiatePluginClass(String className, ClassLoader classLoader) {
        try {
            Class<? extends Plugin> cl = (Class<? extends Plugin>) classLoader.loadClass(className);
            try {
                // add custom settings from plugin
                // add custom settings from elasticsearch.yml
                URL url = classLoader.getResource("elasticsearch.yml");
                if (url != null) {
                    Settings customSettings = settingsBuilder()
                            .put(settings)
                            .loadFromStream("elasticsearch.yml", url.openStream())
                            .build();
                    logger.info("custom settings = {}", customSettings.getAsMap());
                    return cl.getConstructor(Settings.class).newInstance(customSettings);
                } else {
                    return cl.getConstructor(Settings.class).newInstance(settings);
                }
            } catch (NoSuchMethodException e) {
                try {
                    return cl.getConstructor().newInstance();
                } catch (NoSuchMethodException e1) {
                    throw new NoSuchMethodException("No constructor for [" + cl + "]. A plugin class must " +
                            "have either an empty default constructor or a single argument constructor accepting a " +
                            "Settings instance");
                }
            }
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
            throw new ElasticsearchException("Failed to load plugin class [" + className + "]", e);
        }
    }

    private File tryUnpackArchive(File file) throws IOException {
        if (!file.exists()) {
            throw new IOException("file does not exist: " + file.getAbsolutePath());
        }
        if (!file.canRead()) {
            throw new IOException("can not read: " + file.getAbsolutePath());
        }
        if (!file.getName().toLowerCase().endsWith(".zip")) {
            return file;
        }
        ZipFile zipFile = new ZipFile(file);
        try {
            //we check whether we need to remove the top-level folder while extracting
            //sometimes (e.g. github) the downloaded archive contains a top-level folder which needs to be removed
            boolean removeTopLevelDir = topLevelDirInExcess(zipFile);
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipEntries.nextElement();
                if (zipEntry.isDirectory()) {
                    continue;
                }
                String zipEntryName = zipEntry.getName().replace('\\', '/');
                if (removeTopLevelDir) {
                    zipEntryName = zipEntryName.substring(zipEntryName.indexOf('/'));
                }
                File target = new File(file.getParent(), zipEntryName);
                File parent = target.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                Streams.copy(zipFile.getInputStream(zipEntry), new FileOutputStream(target));
            }
            return file.getParentFile();
        } catch (Exception e) {
            logger.error("failed to extract " + file.getAbsolutePath(), e);
        } finally {
            try {
                zipFile.close();
            } catch (IOException e) {
                // ignore
            }
            // remove zip file
            if (!file.delete()) {
                logger.warn("could not remove zip file");
            }
        }
        return file.getParentFile();
    }

    private boolean topLevelDirInExcess(ZipFile zipFile) {
        // we don't rely on ZipEntry#isDirectory because it might be that there is no explicit dir
        // but the files path do contain dirs, thus they are going to be extracted on sub-folders anyway
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        Set<String> topLevelDirNames = new HashSet<>();
        while (zipEntries.hasMoreElements()) {
            ZipEntry zipEntry = zipEntries.nextElement();
            String zipEntryName = zipEntry.getName().replace('\\', '/');
            int slash = zipEntryName.indexOf('/');
            // if there isn't a slash in the entry name it means that we have a file in the top-level
            if (slash == -1) {
                return false;
            }
            topLevelDirNames.add(zipEntryName.substring(0, slash));
            // if we have more than one top-level folder
            if (topLevelDirNames.size() > 1) {
                return false;
            }
        }
        return topLevelDirNames.size() == 1;
    }

    private Set<URI> findJars(File root) {
        Set<URI> jars = new HashSet<>();
        File[] list = root.listFiles();
        if (list == null) {
            return jars;
        }
        for (File f : list) {
            if (f.isDirectory()) {
                jars.addAll(findJars(f.getAbsoluteFile()));
            } else {
                if (f.getName().endsWith(".jar")) {
                    jars.add(f.toURI());
                }
            }
        }
        return jars;
    }

    private Injector processModules(Injector injector, Plugin plugin) {
        List<Module> modules = new ArrayList<>();
        try {
            for (Module module : plugin.nodeModules()) {
                modules.add(module);
            }
            for (Module module : modules) {
                List<OnModuleReference> references = findOnModuleReferences(plugin);
                if (references != null) {
                    for (OnModuleReference reference : references) {
                        if (reference.moduleClass.isAssignableFrom(module.getClass())) {
                            try {
                                reference.onModuleMethod.invoke(plugin, module);
                            } catch (Exception e) {
                                logger.warn("plugin {} failed to invoke custom onModule method", e, plugin.name());
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
        return injector.createChildInjector(modules);
    }

    @SuppressWarnings("unchecked")
    private List<OnModuleReference> findOnModuleReferences(Plugin plugin) {
        List<OnModuleReference> list = new ArrayList<>();
        for (Method method : plugin.getClass().getDeclaredMethods()) {
            if (!method.getName().equals("onModule")) {
                continue;
            }
            if (method.getParameterTypes().length == 0 || method.getParameterTypes().length > 1) {
                logger.warn("plugin {} implementing onModule with no parameters or more than one parameter", plugin.name());
                continue;
            }
            Class moduleClass = method.getParameterTypes()[0];
            if (!Module.class.isAssignableFrom(moduleClass)) {
                logger.warn("plugin {} implementing onModule by the type is not of Module type {}", plugin.name(), moduleClass);
                continue;
            }
            method.setAccessible(true);
            list.add(new OnModuleReference(moduleClass, method));
        }
        return list;
    }

    @Override
    public DeployService init(Settings settings, ClassLoader classLoader, URL jar) throws IOException {
        return this;
    }

    static class OnModuleReference {
        public final Class<? extends Module> moduleClass;
        public final Method onModuleMethod;

        OnModuleReference(Class<? extends Module> moduleClass, Method onModuleMethod) {
            this.moduleClass = moduleClass;
            this.onModuleMethod = onModuleMethod;
        }
    }

    private void startServices(Injector injector, Plugin plugin, Settings settings, ClassLoader classLoader, URL url)
            throws IOException {
        for (Class<? extends LifecycleComponent> service : plugin.nodeServices()) {
            logger.info("found service {} {} to start", service, service.getClass());
            LifecycleComponent t = injector.getInstance(service);
            if (t instanceof DeployableComponent) {
                DeployableComponent component = (DeployableComponent) t;
                logger.info("before init component {}", component);
                // add custom settings from elasticsearch.yml
                URL settingsUrl = classLoader.getResource("elasticsearch.yml");
                if (settingsUrl != null) {
                    Settings customSettings = settingsBuilder()
                            .put(settings)
                            .loadFromStream("elasticsearch.yml", settingsUrl.openStream())
                            .build();
                    logger.info("custom settings = {}", customSettings.getAsMap());
                    component.init(customSettings, classLoader, url);
                    logger.info("after component {}", component);
                }
            }
            logger.info("starting service {}", service);
            t.start();
            logger.info("service {} started", service);
        }
    }

    private void stopServices(Injector injector, Plugin plugin) {
        for (Class<? extends LifecycleComponent> service : plugin.nodeServices()) {
            logger.info("found service {} {} to stop", service, service.getClass());
            injector.getInstance(service).stop();
            logger.info("service {} stopped", service);
        }
    }

}
