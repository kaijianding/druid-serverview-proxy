package com.taobao.druid.extesions.serverviewproxy;

import org.joda.time.Interval;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface SegmentServerViewProxy
{
  Map<String, SegmentHolder> getSegments(String dataSource, List<Interval> intervals);

  Set<String> getDataSources();
}
