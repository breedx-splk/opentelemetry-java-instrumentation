/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.WebClientBuilder;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.Options;
import org.junit.jupiter.api.extension.RegisterExtension;

class ArmeriaHttpClientTest extends AbstractArmeriaHttpClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forLibrary();

  @Override
  protected WebClientBuilder configureClient(WebClientBuilder clientBuilder) {
    return clientBuilder.decorator(
        ArmeriaTelemetry.create(testing.getOpenTelemetry()).newClientDecorator());
  }

  @Override
  protected void configure(Options.Builder optionsBuilder) {
    super.configure(optionsBuilder);

    // library instrumentation doesn't have a good way of suppressing nested CLIENT spans yet
    optionsBuilder.disableTestWithClientParent();

    // Agent users have automatic propagation through executor instrumentation, but library users
    // should do manually using Armeria patterns.
    optionsBuilder.disableTestCallbackWithParent();
    optionsBuilder.disableTestErrorWithCallback();
  }
}
