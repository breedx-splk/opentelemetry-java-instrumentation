/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;

public class JaxRsClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jaxrs-client-2.0";

  private static final Instrumenter<ClientRequestContext, ClientResponseContext> INSTRUMENTER;

  static {
    HttpClientAttributesExtractor<ClientRequestContext, ClientResponseContext>
        httpAttributesExtractor = new JaxRsClientHttpAttributesExtractor();
    SpanNameExtractor<? super ClientRequestContext> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<? super ClientRequestContext, ? super ClientResponseContext>
        spanStatusExtractor = HttpSpanStatusExtractor.create(httpAttributesExtractor);
    JaxRsClientNetAttributesGetter netAttributesAdapter = new JaxRsClientNetAttributesGetter();
    NetClientAttributesExtractor<ClientRequestContext, ClientResponseContext>
        netClientAttributesExtractor = NetClientAttributesExtractor.create(netAttributesAdapter);

    INSTRUMENTER =
        Instrumenter.<ClientRequestContext, ClientResponseContext>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .setSpanStatusExtractor(spanStatusExtractor)
            .setErrorCauseExtractor(
                (throwable) -> {
                  if (throwable instanceof ProcessingException) {
                    throwable = throwable.getCause();
                  }
                  return ErrorCauseExtractor.jdk().extractCause(throwable);
                })
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(netClientAttributesExtractor)
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesAdapter))
            .addRequestMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(ClientRequestContextHeaderSetter.INSTANCE);
  }

  public static Instrumenter<ClientRequestContext, ClientResponseContext> instrumenter() {
    return INSTRUMENTER;
  }

  private JaxRsClientSingletons() {}
}
