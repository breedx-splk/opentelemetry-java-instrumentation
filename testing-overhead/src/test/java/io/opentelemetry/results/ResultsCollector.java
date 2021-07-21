/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.results;

import com.jayway.jsonpath.JsonPath;
import io.opentelemetry.util.JFRUtils;
import io.opentelemetry.util.NamingConvention;
import io.opentelemetry.results.AppPerfResults.MinMax;
import io.opentelemetry.agents.Agent;
import io.opentelemetry.config.TestConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

public class ResultsCollector {

  private final NamingConvention namingConvention;

  public ResultsCollector(NamingConvention namingConvention) {this.namingConvention = namingConvention; }

  public Map<Agent, AppPerfResults> collect(TestConfig config) {
    return config.getAgents().stream()
        .collect(Collectors.toMap(a -> a, a -> readAgentResults(a, config)));
  }

  private AppPerfResults readAgentResults(Agent agent, TestConfig config) {
    try {
      AppPerfResults.Builder builder = AppPerfResults.builder()
          .agent(agent)
          .config(config);

      builder = addStartupTime(builder, agent);
      builder = addK6Results(builder, agent);
      builder = addJfrResults(builder, agent);

      return builder.build();
    } catch (IOException e) {
      throw new RuntimeException("Error reading results", e);
    }
  }

  private AppPerfResults.Builder addStartupTime(
      AppPerfResults.Builder builder, Agent agent) throws IOException {
    Path file = namingConvention.startupDurationFile(agent);
    long startupDuration = Long.parseLong(new String(Files.readAllBytes(file)).trim());
    return builder.startupDurationMs(startupDuration);
  }

  private AppPerfResults.Builder addK6Results(
      AppPerfResults.Builder builder, Agent agent)
      throws IOException {
    Path k6File = namingConvention.k6Results(agent);
    String json = new String(Files.readAllBytes(k6File));
    double iterationAvg = JsonPath.read(json, "$.metrics.iteration_duration.avg");
    double iterationP95 = JsonPath.read(json, "$.metrics.iteration_duration['p(95)']");
    double requestAvg = JsonPath.read(json, "$.metrics.http_req_duration.avg");
    double requestP95 = JsonPath.read(json, "$.metrics.http_req_duration['p(95)']");
    return builder
        .iterationAvg(iterationAvg)
        .iterationP95(iterationP95)
        .requestAvg(requestAvg)
        .requestP95(requestP95);
  }

  private AppPerfResults.Builder addJfrResults(
      AppPerfResults.Builder builder, Agent agent) throws IOException {
    Path jfrFile = namingConvention.jfrFile(agent);
    return builder
        .totalGCTime(readTotalGCTime(jfrFile))
        .totalAllocated(readTotalAllocated(jfrFile))
        .heapUsed(readHeapUsed(jfrFile))
        .maxThreadContextSwitchRate(readMaxThreadContextSwitchRate(jfrFile))
        .peakThreadCount(readPeakThreadCount(jfrFile));
  }

  private long readPeakThreadCount(Path jfrFile) throws IOException {
    MinMax minMax = JFRUtils.findMinMax(jfrFile, "jdk.JavaThreadStatistics", "peakCount");
    return minMax.max;
  }

  private long readTotalGCTime(Path jfrFile) throws IOException {
    return JFRUtils.totalLongEvents(jfrFile, "jdk.G1GarbageCollection", "duration");
  }

  private long readTotalAllocated(Path jfrFile) throws IOException {
    return JFRUtils.totalLongEvents(jfrFile, "jdk.ThreadAllocationStatistics", "allocated");
  }

  private MinMax readHeapUsed(Path jfrFile) throws IOException {
    return JFRUtils.findMinMax(jfrFile, "jdk.GCHeapSummary", "heapUsed");
  }

  private float readMaxThreadContextSwitchRate(Path jfrFile) throws IOException {
    return JFRUtils.findMaxFloat(jfrFile, "jdk.ThreadContextSwitchRate", "switchRate");
  }

}
