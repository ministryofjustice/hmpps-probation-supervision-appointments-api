plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "9.0.0"
  id("org.jetbrains.kotlin.plugin.jpa") version "2.2.10"
  kotlin("plugin.spring") version "2.2.10"
  id("idea")
}

idea {
  module {
    resourceDirs.add(file("src/wiremock-stubs"))
  }
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

configurations.all {
  resolutionStrategy {
    force("io.netty:netty-codec:4.1.125.Final")
    force("io.netty:netty-codec-compression:4.2.5.Final")
    force("org.apache.commons:commons-lang3:3.18.0")
  }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.7.0")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.flywaydb:flyway-core")
  implementation("com.microsoft.graph:microsoft-graph:6.53.0")
  implementation("org.springframework:spring-core:6.2.11")
  implementation("org.springframework.security:spring-security-core:6.5.4")
  implementation("com.azure:azure-identity:1.18.0")
  implementation("io.netty:netty-codec:4.1.125.Final")
  implementation("io.netty:netty-codec-compression:4.2.5.Final")
  implementation("org.apache.commons:commons-lang3:3.18.0")

  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")

  compileOnly("org.wiremock:wiremock-standalone:3.9.1")
  developmentOnly("org.wiremock:wiremock-standalone:3.9.1")
  developmentOnly("com.h2database:h2")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.7.0")
  testImplementation("com.h2database:h2")
  testImplementation("org.wiremock:wiremock-standalone:3.13.1")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.34") {
    exclude(group = "io.swagger.core.v3")
  }
}

kotlin {
  jvmToolchain(21)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
}
