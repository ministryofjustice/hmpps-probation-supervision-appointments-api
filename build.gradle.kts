plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "10.0.4"
  id("org.jetbrains.kotlin.plugin.jpa") version "2.3.10"
  kotlin("plugin.spring") version "2.3.10"
  id("idea")
  id("io.sentry.jvm.gradle") version "6.1.0"
}

val hmppsSpringBootStarterVersion = "2.0.2"
val azureIdentityVersion = "1.18.2"
val fliptVersion = "1.2.1"
val sentryVersion = "8.33.0"
val notifyVersion = "6.0.0-RELEASE"
val microsoftGraphVersion = "6.62.0"
val wiremockVersion = "3.13.2"
val swaggerParserVersion = "2.1.38"
val springdocVersion = "3.0.2"
val sqsVersion = "5.6.3"

idea {
  module {
    resourceDirs.add(file("src/wiremock-stubs"))
  }
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
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
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  implementation("org.openfolder:kotlin-asyncapi-spring-web:3.2.0")

  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")
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
}

kotlin {
  jvmToolchain(25)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
  }
}
