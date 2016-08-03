package com.taobao.druid.extesions.serverviewproxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import io.druid.guice.DruidGuiceExtensions;
import io.druid.guice.GuiceAnnotationIntrospector;
import io.druid.guice.GuiceInjectableValues;
import io.druid.guice.LazySingleton;
import io.druid.guice.annotations.Json;

/**
 * simplified version of DruidSecondaryModule
 */
public class JacksonSetupModule implements Module
{
  private final ObjectMapper jsonMapper;

  @Inject
  public JacksonSetupModule(
      @Json ObjectMapper jsonMapper
  )
  {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public void configure(Binder binder)
  {
    binder.install(new DruidGuiceExtensions());
    binder.bind(ObjectMapper.class).to(Key.get(ObjectMapper.class, Json.class));
  }

  @Provides
  @LazySingleton
  @Json
  public ObjectMapper getJsonMapper(final Injector injector)
  {
    setupJackson(injector, jsonMapper);
    return jsonMapper;
  }

  private void setupJackson(Injector injector, final ObjectMapper mapper)
  {
    final GuiceAnnotationIntrospector guiceIntrospector = new GuiceAnnotationIntrospector();

    mapper.setInjectableValues(new GuiceInjectableValues(injector));
    mapper.setAnnotationIntrospectors(
        new AnnotationIntrospectorPair(
            guiceIntrospector, mapper.getSerializationConfig().getAnnotationIntrospector()
        ),
        new AnnotationIntrospectorPair(
            guiceIntrospector, mapper.getDeserializationConfig().getAnnotationIntrospector()
        )
    );
  }
}
