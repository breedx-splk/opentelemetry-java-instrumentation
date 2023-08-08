/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeoutException

// this test is run using
//   -Dotel.javaagent.exclude-classes=config.exclude.packagename.*,config.exclude.SomeClass,config.exclude.SomeClass$NestedClass
// (see integration-tests.gradle)
class AgentInstrumentationSpecificationTest extends AgentInstrumentationSpecification {

  def "waiting for child spans times out"() {
    when:
    runWithSpan("parent") {
      waitForTraces(1)
    }

    then:
    thrown(TimeoutException)
  }

  def "logging works"() {
    when:
    LoggerFactory.getLogger(AgentInstrumentationSpecificationTest).debug("hello")
    then:
    noExceptionThrown()
  }

  def "excluded classes are not instrumented"() {
    when:
    runWithSpan("parent") {
      subject.run()
    }

    then:
    assertTraces(1) {
      trace(0, spanName ? 2 : 1) {
        span(0) {
          name "parent"
        }
        if (spanName) {
          span(1) {
            name spanName
            childOf span(0)
          }
        }
      }
    }

    where:
    subject                                                | spanName
    new config.SomeClass()                                 | "SomeClass.run"
    new config.SomeClass.NestedClass()                     | "NestedClass.run"
    new config.exclude.SomeClass()                         | null
    new config.exclude.SomeClass.NestedClass()             | null
    new config.exclude.packagename.SomeClass()             | null
    new config.exclude.packagename.SomeClass.NestedClass() | null
  }

  def "test unblocked by completed span"() {
    setup:
    runWithSpan("parent") {
      runWithSpan("child") {}
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          hasNoParent()
        }
        span(1) {
          name "child"
          childOf span(0)
        }
      }
    }
  }

  private static String[] getClasspath() {
    return System.getProperty("java.class.path").split(System.getProperty("path.separator"))
  }
}
