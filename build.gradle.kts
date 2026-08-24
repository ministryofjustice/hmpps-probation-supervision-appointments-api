plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "11.0.4"
  id("org.jetbrains.kotlin.plugin.jpa") version "2.4.10"
  kotlin("plugin.spring") version "2.4.10"
  id("idea")
  id("io.sentry.jvm.gradle") version "6.18.0"
}

val hmppsSpringBootStarterVersion = "3.0.0"
val azureIdentityVersion = "1.18.4"
val fliptVersion = "1.3.3"
val sentryVersion = "8.53.0"
val notifyVersion = "6.2.0-RELEASE"
val microsoftGraphVersion = "6.67.0"
val wiremockVersion = "3.13.2"
val swaggerParserVersion = "2.1.46"
val springdocVersion = "3.1.0"
val httpclient5Version = "5.6.4"
val sqsVersion = "7.4.0"
val postgresqlVersion = "42.7.13"

idea {
  module {
    resourceDirs.add(file("src/wiremock-stubs"))
  }
}

// Overrides Spring Boot's managed httpclient5 version (see spring-boot-dependencies BOM) to
// pick up the fix for CVE-2026-64607 (classic transport fails to release the underlying
// connection when it encounters an invalid/unsupported Content-Encoding header).
extra["httpclient5.version"] = httpclient5Version

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencyCheck {
  suppressionFiles.add("owasp-suppressions.xml")
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:$hmppsSpringBootStarterVersion")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("com.microsoft.graph:microsoft-graph:$microsoftGraphVersion")
  implementation("com.azure:azure-identity:$azureIdentityVersion")
  implementation("io.flipt:flipt-client-java:$fliptVersion")
  implementation("uk.gov.service.notify:notifications-java-client:$notifyVersion")
  implementation("io.sentry:sentry-spring-boot-4:$sentryVersion")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:$sqsVersion")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
  implementation("org.openfolder:kotlin-asyncapi-spring-web:3.2.4")

  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql:$postgresqlVersion")
  runtimeOnly("org.flywaydb:flyway-core")

  compileOnly("org.wiremock:wiremock-standalone:$wiremockVersion")
  developmentOnly("org.wiremock:wiremock-standalone:$wiremockVersion")
  developmentOnly("com.h2database:h2")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:$hmppsSpringBootStarterVersion")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
  testImplementation("com.h2database:h2")
  testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")
  testImplementation("io.swagger.parser.v3:swagger-parser:$swaggerParserVersion") {
    exclude(group = "io.swagger.core.v3")
  }

  testImplementation("org.awaitility:awaitility-kotlin")
  testImplementation("org.testcontainers:testcontainers:2.0.5")
  testImplementation("org.testcontainers:postgresql:1.21.4")
  testImplementation("org.testcontainers:localstack:1.21.4")
  testImplementation("org.testcontainers:junit-jupiter:1.21.4")
  testImplementation("org.jetbrains.kotlin:kotlin-test")
}

kotlin {
  jvmToolchain(25)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
  }
}
