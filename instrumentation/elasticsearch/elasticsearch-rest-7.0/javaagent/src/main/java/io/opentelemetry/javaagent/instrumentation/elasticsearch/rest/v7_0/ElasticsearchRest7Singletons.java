/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.v7_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal.ElasticsearchRestRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.ElasticsearchRestJavaagentInstrumenterFactory;
import org.elasticsearch.client.Response;

public final class ElasticsearchRest7Singletons {

  private static final Instrumenter<ElasticsearchRestRequest, Response> INSTRUMENTER =
      ElasticsearchRestJavaagentInstrumenterFactory.create(
          "io.opentelemetry.elasticsearch-rest-7.0");

  public static Instrumenter<ElasticsearchRestRequest, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private ElasticsearchRest7Singletons() {}
}
