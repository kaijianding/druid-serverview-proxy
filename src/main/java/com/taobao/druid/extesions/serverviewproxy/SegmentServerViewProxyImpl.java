package com.taobao.druid.extesions.serverviewproxy;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.druid.curator.CuratorConfig;
import io.druid.server.initialization.ZkPathsConfig;
import io.druid.timeline.TimelineLookup;
import io.druid.timeline.TimelineObjectHolder;
import io.druid.timeline.partition.PartitionChunk;
import org.joda.time.Interval;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SegmentServerViewProxyImpl implements SegmentServerViewProxy
{

  private final SegmentServerView serverView;

  public SegmentServerViewProxyImpl(String zkHosts, String base)
  {
    ZkPathsConfig zkPathsConfig = new ZkPathsConfig();
    if (base != null) {
      setZkBase(zkPathsConfig, base);
    }
    CuratorConfig curatorConfig = new CuratorConfig();
    if (zkHosts != null) {
      curatorConfig.setZkHosts(zkHosts);
    }
    serverView = new ServerInventoryViewConnector(zkPathsConfig, curatorConfig).connect();
  }

  // constructor for test
  protected SegmentServerViewProxyImpl(SegmentServerView serverView)
  {
    this.serverView = serverView;
  }

  /**
   * base code from io.druid.client.CachingClusteredClient
   */
  @Override
  public Map<String, SegmentHolder> getSegments(String dataSource, List<Interval> intervals)
  {
    Map<String, SegmentHolder> segmentsCache = Maps.newHashMap();
    TimelineLookup<String, SegmentHolder> timeline = serverView.getTimeline(dataSource);
    if (timeline == null) {
      return segmentsCache;
    }

    List<TimelineObjectHolder<String, SegmentHolder>> serversLookup = Lists.newLinkedList();
    for (Interval interval : intervals) {
      Iterables.addAll(serversLookup, timeline.lookup(interval));
    }

    for (TimelineObjectHolder<String, SegmentHolder> holder : serversLookup) {
      for (PartitionChunk<SegmentHolder> chunk : holder.getObject()) {
        SegmentHolder selector = chunk.getObject();
        segmentsCache.put(selector.getSegment().getIdentifier(), selector);
      }
    }
    return segmentsCache;
  }

  @Override
  public Set<String> getDataSources()
  {
    return serverView.getDataSources();
  }

  private void setZkBase(ZkPathsConfig zkPathsConfig, String base)
  {
    try {
      Field f = ZkPathsConfig.class.getDeclaredField("base");
      f.setAccessible(true);
      f.set(zkPathsConfig, base);
    }
    catch (NoSuchFieldException | IllegalAccessException e) {
      throw Throwables.propagate(e);
    }
  }

}
