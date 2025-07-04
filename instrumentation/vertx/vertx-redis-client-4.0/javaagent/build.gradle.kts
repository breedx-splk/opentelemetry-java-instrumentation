plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-redis-client")
    versions.set("[4.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.vertx:vertx-redis-client:4.0.0")
  compileOnly("io.vertx:vertx-codegen:4.0.0")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  testLibrary("io.vertx:vertx-codegen:4.0.0")
}

val collectMetadata = findProperty("collectMetadata")?.toString() ?: "false"

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", collectMetadata)
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")

    systemProperty("collectMetadata", collectMetadata)
    systemProperty("metaDataConfig", "otel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
