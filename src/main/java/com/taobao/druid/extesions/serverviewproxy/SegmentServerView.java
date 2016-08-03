package com.taobao.druid.extesions.serverviewproxy;

import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.inject.Inject;
import com.metamx.common.logger.Logger;
import io.druid.client.DruidServer;
import io.druid.client.ServerInventoryView;
import io.druid.client.ServerView;
import io.druid.concurrent.Execs;
import io.druid.server.coordination.DruidServerMetadata;
import io.druid.timeline.DataSegment;
import io.druid.timeline.VersionedIntervalTimeline;
import io.druid.timeline.partition.PartitionChunk;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

/**
 * most code from io.druid.client.BrokerServerView
 */
public class SegmentServerView
{
  private static final Logger log = new Logger(SegmentServerView.class);

  private final Object lock = new Object();
  private final ServerInventoryView baseView;
  private final ConcurrentMap<String, DruidServer> clients;
  private final Map<String, SegmentHolder> selectors;
  private final Map<String, VersionedIntervalTimeline<String, SegmentHolder>> timelines;
  private volatile boolean initialized = false;

  @Inject
  public SegmentServerView(ServerInventoryView baseView)
  {
    this.baseView = baseView;
    this.clients = Maps.newConcurrentMap();
    this.selectors = Maps.newHashMap();
    this.timelines = Maps.newHashMap();

    ExecutorService exec = Execs.singleThreaded("SegmentServerView-%s");
    baseView.registerSegmentCallback(
        exec,
        new ServerView.SegmentCallback()
        {
          @Override
          public ServerView.CallbackAction segmentAdded(DruidServerMetadata server, DataSegment segment)
          {
            serverAddedSegment(server, segment);
            return ServerView.CallbackAction.CONTINUE;
          }

          @Override
          public ServerView.CallbackAction segmentRemoved(final DruidServerMetadata server, DataSegment segment)
          {
            serverRemovedSegment(server, segment);
            return ServerView.CallbackAction.CONTINUE;
          }

          @Override
          public ServerView.CallbackAction segmentViewInitialized()
          {
            initialized = true;
            return ServerView.CallbackAction.CONTINUE;
          }
        }
    );

    baseView.registerServerCallback(
        exec,
        new ServerView.ServerCallback()
        {
          @Override
          public ServerView.CallbackAction serverRemoved(DruidServer server)
          {
            removeServer(server);
            return ServerView.CallbackAction.CONTINUE;
          }
        }
    );
  }

  public boolean isInitialized()
  {
    return initialized;
  }

  private DruidServer addServer(DruidServer server)
  {
    DruidServer exists = clients.put(server.getName(), server);
    if (exists != null) {
      log.warn("QueryRunner for server[%s] already existed!? Well it's getting replaced", server);
    }
    return server;
  }

  private DruidServer removeServer(DruidServer server)
  {
    for (DataSegment segment : server.getSegments().values()) {
      serverRemovedSegment(server.getMetadata(), segment);
    }
    return clients.remove(server.getName());
  }

  private void serverAddedSegment(final DruidServerMetadata server, final DataSegment segment)
  {
    String segmentId = segment.getIdentifier();
    synchronized (lock) {
      log.debug("Adding segment[%s] for server[%s]", segment, server);

      SegmentHolder selector = selectors.get(segmentId);
      if (selector == null) {
        selector = new SegmentHolder(segment);

        VersionedIntervalTimeline<String, SegmentHolder> timeline = timelines.get(segment.getDataSource());
        if (timeline == null) {
          timeline = new VersionedIntervalTimeline<>(Ordering.natural());
          timelines.put(segment.getDataSource(), timeline);
        }

        timeline.add(segment.getInterval(), segment.getVersion(), segment.getShardSpec().createChunk(selector));
        selectors.put(segmentId, selector);
      }

      DruidServer druidServer = clients.get(server.getName());
      if (druidServer == null) {
        druidServer = addServer(baseView.getInventoryValue(server.getName()));
      }
      selector.addServer(druidServer);
    }
  }

  private void serverRemovedSegment(DruidServerMetadata server, DataSegment segment)
  {
    String segmentId = segment.getIdentifier();
    final SegmentHolder selector;

    synchronized (lock) {
      log.debug("Removing segment[%s] from server[%s].", segmentId, server);

      selector = selectors.get(segmentId);
      if (selector == null) {
        log.warn("Told to remove non-existant segment[%s]", segmentId);
        return;
      }

      DruidServer druidServer = clients.get(server.getName());
      if (!selector.removeServer(druidServer)) {
        log.warn(
            "Asked to disassociate non-existant association between server[%s] and segment[%s]",
            server,
            segmentId
        );
      }

      if (selector.isEmpty()) {
        VersionedIntervalTimeline<String, SegmentHolder> timeline = timelines.get(segment.getDataSource());
        selectors.remove(segmentId);

        final PartitionChunk<SegmentHolder> removedPartition = timeline.remove(
            segment.getInterval(), segment.getVersion(), segment.getShardSpec().createChunk(selector)
        );

        if (removedPartition == null) {
          log.warn(
              "Asked to remove timeline entry[interval: %s, version: %s] that doesn't exist",
              segment.getInterval(),
              segment.getVersion()
          );
        }
      }
    }
  }

  public VersionedIntervalTimeline<String, SegmentHolder> getTimeline(String dataSource)
  {
    synchronized (lock) {
      return timelines.get(dataSource);
    }
  }

  public Set<String> getDataSources()
  {
    synchronized (lock) {
      return new HashSet<>(timelines.keySet());
    }
  }

  public ServerInventoryView getBaseView()
  {
    return baseView;
  }
}
