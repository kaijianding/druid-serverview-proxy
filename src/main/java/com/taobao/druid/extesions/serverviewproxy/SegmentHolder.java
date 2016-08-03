package com.taobao.druid.extesions.serverviewproxy;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import io.druid.client.DruidServer;
import io.druid.timeline.DataSegment;

import java.util.Set;

public class SegmentHolder
{
  private final Set<DruidServer> servers = Sets.newHashSet();
  private final DataSegment segment;

  public SegmentHolder(DataSegment segment)
  {
    this.segment = segment;
  }

  public Set<DruidServer> getDruidServers()
  {
    return servers;
  }

  public Set<String> getServers()
  {
    return Sets.newHashSet(
        Collections2.transform(servers, new Function<DruidServer, String>()
        {
          @Override
          public String apply(DruidServer server)
          {
            return server.getHost();
          }
        })
    );
  }

  public DataSegment getSegment()
  {
    return segment;
  }

  public void addServer(DruidServer server)
  {
    synchronized (this) {
      servers.add(server);
    }
  }

  public boolean removeServer(DruidServer server)
  {
    synchronized (this) {
      return servers.remove(server);
    }
  }

  public boolean isEmpty()
  {
    synchronized (this) {
      return servers.isEmpty();
    }
  }
}
