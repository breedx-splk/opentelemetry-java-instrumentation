plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.glassfish.grizzly")
    module.set("grizzly-http")
    versions.set("[2.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("org.glassfish.grizzly:grizzly-http:2.0")

  testImplementation("javax.xml.bind:jaxb-api:2.2.3")
  testImplementation("javax.ws.rs:javax.ws.rs-api:2.0")
  testLibrary("org.glassfish.jersey.containers:jersey-container-grizzly2-http:2.0")

  latestDepTestLibrary("org.glassfish.jersey.containers:jersey-container-grizzly2-http:2.+")
  latestDepTestLibrary("org.glassfish.jersey.inject:jersey-hk2:2.+")
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.grizzly.enabled=true")
}

tasks.withType<Test>().configureEach {
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2640
  jvmArgs("-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=false")
}


// Requires old Guava. Can't use enforcedPlatform since predates BOM
configurations.testRuntimeClasspath.resolutionStrategy.force("com.google.guava:guava:19.0")
