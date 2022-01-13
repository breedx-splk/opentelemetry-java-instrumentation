/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest
import java.time.Duration
import spock.lang.IgnoreIf

import static io.opentelemetry.smoketest.TestContainerManager.useWindowsContainers
import static java.util.stream.Collectors.toList

@IgnoreIf({ useWindowsContainers() })
class LogsSmokeTest extends SmokeTest {

  @Override
  protected String getTargetImage(String jdk) {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk11-20211213.1570880324"
  }

  @Override
  protected Map<String, String> getExtraEnv() {
    return Map.of("OTEL_LOGS_EXPORTER", "otlp")
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Started SpringbootApplication in.*")
  }

  def "Should export logs"() {
    setup:
    startTarget(11)

    when:
    client().get("/greeting").aggregate().join()
    Collection<ExportLogsServiceRequest> logs = waitForLogs()

    then:
    logs.size() > 0

    def logRecords = logs.stream()
      .flatMap(log -> log.getResourceLogsList().stream())
      .flatMap(log -> log.getInstrumentationLibraryLogsList().stream())
      .flatMap(log -> log.getLogsList().stream())
      .collect(toList())
    logRecords.size() >= logs.size()

    cleanup:
    stopTarget()

  }

}