package com.taobao.druid.extesions.serverviewproxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.metamx.common.ISE;
import io.druid.guice.DruidGuiceExtensions;
import io.druid.guice.LifecycleModule;
import io.druid.guice.annotations.Json;
import io.druid.initialization.DruidModule;
import io.druid.jackson.JacksonModule;

import java.util.Collections;
import java.util.List;

/**
 * simplified version of io.druid.initialization
 */
public class Initialization
{
  public static Injector makeInjectorWithModules(Iterable<? extends Module> modules)
  {
    final Injector baseInjector = Guice.createInjector(new JacksonModule(), new DruidGuiceExtensions());
    final ModuleList defaultModules = new ModuleList(baseInjector);
    defaultModules.addModules(
        new LifecycleModule(),
        JacksonSetupModule.class
    );

    ModuleList actualModules = new ModuleList(baseInjector);
    for (Object module : modules) {
      actualModules.addModule(module);
    }

    Module intermediateModules = Modules.override(defaultModules.getModules()).with(actualModules.getModules());
    return Guice.createInjector(intermediateModules);
  }

  private static class ModuleList
  {
    private final Injector baseInjector;
    private final ObjectMapper jsonMapper;
    private final List<Module> modules;

    public ModuleList(Injector baseInjector)
    {
      this.baseInjector = baseInjector;
      this.jsonMapper = baseInjector.getInstance(Key.get(ObjectMapper.class, Json.class));
      this.modules = Lists.newArrayList();
    }

    private List<Module> getModules()
    {
      return Collections.unmodifiableList(modules);
    }

    public void addModule(Object input)
    {
      if (input instanceof DruidModule) {
        baseInjector.injectMembers(input);
        modules.add(registerJacksonModules(((DruidModule) input)));
      } else if (input instanceof Module) {
        baseInjector.injectMembers(input);
        modules.add((Module) input);
      } else if (input instanceof Class) {
        if (DruidModule.class.isAssignableFrom((Class) input)) {
          modules.add(registerJacksonModules(baseInjector.getInstance((Class<? extends DruidModule>) input)));
        } else if (Module.class.isAssignableFrom((Class) input)) {
          modules.add(baseInjector.getInstance((Class<? extends Module>) input));
        } else {
          throw new ISE("Class[%s] does not implement %s", input.getClass(), Module.class);
        }
      } else {
        throw new ISE("Unknown module type[%s]", input.getClass());
      }
    }

    public void addModules(Object... object)
    {
      for (Object o : object) {
        addModule(o);
      }
    }

    private DruidModule registerJacksonModules(DruidModule module)
    {
      for (com.fasterxml.jackson.databind.Module jacksonModule : module.getJacksonModules()) {
        jsonMapper.registerModule(jacksonModule);
      }
      return module;
    }
  }
}
