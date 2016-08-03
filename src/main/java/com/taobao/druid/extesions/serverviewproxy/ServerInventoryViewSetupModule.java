package com.taobao.druid.extesions.serverviewproxy;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.inject.Binder;
import io.druid.client.BatchServerInventoryView;
import io.druid.client.ServerInventoryView;
import io.druid.curator.CuratorModule;
import io.druid.guice.LifecycleModule;
import io.druid.guice.ManageLifecycle;
import io.druid.initialization.DruidModule;
import io.druid.timeline.partition.HashBasedNumberedShardSpec;
import io.druid.timeline.partition.LinearShardSpec;
import io.druid.timeline.partition.NumberedShardSpec;
import io.druid.timeline.partition.SingleDimensionShardSpec;
import org.apache.curator.framework.CuratorFramework;

import java.util.Collections;
import java.util.List;

public class ServerInventoryViewSetupModule implements DruidModule
{
  @Override
  public void configure(Binder binder)
  {
    binder.bind(ServerInventoryView.class).to(BatchServerInventoryView.class).in(ManageLifecycle.class);
    binder.install(new CuratorModule()
    {
      @Override
      public void configure(Binder binder)
      {
      }
    });
    LifecycleModule.register(binder, CuratorFramework.class);
  }

  @Override
  public List<? extends Module> getJacksonModules()
  {
    return Collections.singletonList(
        new SimpleModule()
            .registerSubtypes(
                new NamedType(SingleDimensionShardSpec.class, "single"),
                new NamedType(LinearShardSpec.class, "linear"),
                new NamedType(NumberedShardSpec.class, "numbered"),
                new NamedType(HashBasedNumberedShardSpec.class, "hashed")
            )
    );
  }
}
