package com.taobao.druid.extesions.serverviewproxy;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.metamx.common.lifecycle.Lifecycle;
import io.druid.curator.CuratorConfig;
import io.druid.server.initialization.ZkPathsConfig;

import java.util.List;

public class ServerInventoryViewConnector
{
  private ZkPathsConfig zkPathsConfig;
  private CuratorConfig curatorConfig;

  public ServerInventoryViewConnector(ZkPathsConfig zkPathsConfig, CuratorConfig curatorConfig)
  {
    this.zkPathsConfig = zkPathsConfig;
    this.curatorConfig = curatorConfig;
  }

  private List<? extends Module> getModules()
  {
    return ImmutableList.of(
        new Module()
        {
          @Override
          public void configure(Binder binder)
          {
            binder.bind(ZkPathsConfig.class).toInstance(zkPathsConfig);
            binder.bind(CuratorConfig.class).toInstance(curatorConfig);
          }
        },
        new ServerInventoryViewSetupModule()
    );
  }

  private void startLifecycle(Lifecycle lifecycle)
  {
    try {
      lifecycle.start();
    }
    catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  private SegmentServerView getConnectedView(SegmentServerView serverView)
  {
    while (!serverView.isInitialized()) {
      try {
        Thread.sleep(10);
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    return serverView;
  }

  public SegmentServerView connect()
  {
    Injector injector = Initialization.makeInjectorWithModules(getModules());
    startLifecycle(injector.getInstance(Lifecycle.class));
    return getConnectedView(injector.getInstance(SegmentServerView.class));
  }
}
