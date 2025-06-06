/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import java.lang.reflect.Modifier;
import java.util.concurrent.CompletableFuture;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.asm.MemberAttributeExtension;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // testing instrumentation of deprecated class
class WithSpanInstrumentationTest {

  @RegisterExtension
  public static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  @Test
  void deriveAutomaticName() {
    new TracedWithSpan().otel();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.otel")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TracedWithSpan.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "otel"))));
  }

  @Test
  void manualName() {
    new TracedWithSpan().namedOtel();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("manualName")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TracedWithSpan.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "namedOtel"))));
  }

  @Test
  void manualKind() {
    new TracedWithSpan().someKind();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.someKind")
                        .hasKind(SpanKind.PRODUCER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TracedWithSpan.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "someKind"))));
  }

  @Test
  void multipleSpans() {
    new TracedWithSpan().server();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.server")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TracedWithSpan.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "server")),
                span ->
                    span.hasName("TracedWithSpan.otel")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TracedWithSpan.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "otel"))));
  }

  @Test
  void excludedMethod() throws Exception {
    new TracedWithSpan().ignored();

    Thread.sleep(500); // sleep a bit just to make sure no span is captured
    assertThat(testing.waitForTraces(0)).isEmpty();
  }

  @Test
  void completedCompletionStage() {
    CompletableFuture<String> future = CompletableFuture.completedFuture("Done");
    new TracedWithSpan().completionStage(future);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.completionStage")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TracedWithSpan.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "completionStage"))));
  }

  @Test
  void exceptionallyCompletedCompletionStage() {
    CompletableFuture<String> future = new CompletableFuture<>();
    Exception exception = new IllegalArgumentException("Boom");
    future.completeExceptionally(exception);
    new TracedWithSpan().completionStage(future);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.completionStage")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(exception)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TracedWithSpan.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "completionStage"))));
  }

  @Test
  void nullCompletionStage() {
    new TracedWithSpan().completionStage(null);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.completionStage")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TracedWithSpan.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "completionStage"))));
  }

  @Test
  void completingCompletionStage() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    new TracedWithSpan().completionStage(future);

    Thread.sleep(500); // sleep a bit just to make sure no span is captured
    assertThat(testing.waitForTraces(0)).isEmpty();

    future.complete("Done");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.completionStage")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TracedWithSpan.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "completionStage"))));
  }

  @Test
  void exceptionallyCompletingCompletionStage() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    new TracedWithSpan().completionStage(future);

    Thread.sleep(500); // sleep a bit just to make sure no span is captured
    assertThat(testing.waitForTraces(0)).isEmpty();

    Exception exception = new IllegalArgumentException("Boom");
    future.completeExceptionally(exception);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.completionStage")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(exception)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TracedWithSpan.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "completionStage"))));
  }

  @Test
  void completedCompletableFuture() {
    CompletableFuture<String> future = CompletableFuture.completedFuture("Done");
    new TracedWithSpan().completableFuture(future);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.completableFuture")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TracedWithSpan.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "completableFuture"))));
  }

  @Test
  void exceptionallyCompletedCompletableFuture() {
    CompletableFuture<String> future = new CompletableFuture<>();
    Exception exception = new IllegalArgumentException("Boom");
    future.completeExceptionally(exception);
    new TracedWithSpan().completableFuture(future);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.completableFuture")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(exception)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TracedWithSpan.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "completableFuture"))));
  }

  @Test
  void nullCompletableFuture() {
    new TracedWithSpan().completableFuture(null);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.completableFuture")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TracedWithSpan.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "completableFuture"))));
  }

  @Test
  void completingCompletableFuture() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    new TracedWithSpan().completableFuture(future);

    Thread.sleep(500); // sleep a bit just to make sure no span is captured
    assertThat(testing.waitForTraces(0)).isEmpty();

    future.complete("Done");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.completableFuture")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TracedWithSpan.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "completableFuture"))));
  }

  @Test
  void exceptionallyCompletingCompletableFuture() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    new TracedWithSpan().completableFuture(future);

    Thread.sleep(500); // sleep a bit just to make sure no span is captured
    assertThat(testing.waitForTraces(0)).isEmpty();

    Exception exception = new IllegalArgumentException("Boom");
    future.completeExceptionally(exception);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.completableFuture")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(exception)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TracedWithSpan.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "completableFuture"))));
  }

  @Test
  void captureAttributes() {
    new TracedWithSpan().withSpanAttributes("foo", "bar", null, "baz");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TracedWithSpan.withSpanAttributes")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TracedWithSpan.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "withSpanAttributes"),
                            equalTo(stringKey("implicitName"), "foo"),
                            equalTo(stringKey("explicitName"), "bar"))));
  }

  // Needs to be public for ByteBuddy
  public static class Intercept {
    @RuntimeType
    public void intercept(@This Object o) {
      testing.runWithSpan("intercept", () -> {});
    }
  }

  @Test
  void java6Class() throws Exception {
    /*
    class GeneratedJava6TestClass implements Runnable {
      @WithSpan
      public void run() {
        testing.runWithSpan("intercept", () -> {});
      }
    }
    */
    Class<?> generatedClass =
        new ByteBuddy(ClassFileVersion.JAVA_V6)
            .subclass(Object.class)
            .name("GeneratedJava6TestClass")
            .implement(Runnable.class)
            .defineMethod("run", void.class, Modifier.PUBLIC)
            .intercept(MethodDelegation.to(new Intercept()))
            .visit(
                new MemberAttributeExtension.ForMethod()
                    .annotateMethod(
                        AnnotationDescription.Builder.ofType(
                                io.opentelemetry.extension.annotations.WithSpan.class)
                            .build())
                    .on(ElementMatchers.named("run")))
            .make()
            .load(getClass().getClassLoader())
            .getLoaded();

    Runnable runnable = (Runnable) generatedClass.getConstructor().newInstance();
    runnable.run();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GeneratedJava6TestClass.run")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParentSpanId(SpanId.getInvalid())
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE, "GeneratedJava6TestClass"),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "run")),
                span ->
                    span.hasName("intercept")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParentSpanId(trace.getSpan(0).getSpanId())
                        .hasAttributes(Attributes.empty())));
  }
}
