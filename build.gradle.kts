plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "9.1.4"
  id("org.jetbrains.kotlin.plugin.jpa") version "2.2.21"
  kotlin("plugin.spring") version "2.2.21"
  id("idea")
}

val hmppsSpringBootStarterVersion = "1.8.1"
val azureIdentityVersion = "1.18.1"
val microsoftGraphVersion = "6.55.0"
val wiremockVersion = "3.13.1"
val swaggerParserVersion = "2.1.35"
val springdocVersion = "2.8.13"
val fliptVersion = "1.1.2"
val sentryVersion = "8.25.0"
val notifyVersion = "5.2.1-RELEASE"

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
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.flywaydb:flyway-core")
  implementation("com.microsoft.graph:microsoft-graph:$microsoftGraphVersion")
  implementation("com.azure:azure-identity:$azureIdentityVersion")
  implementation("io.flipt:flipt-client-java:$fliptVersion")
  implementation("uk.gov.service.notify:notifications-java-client:$notifyVersion")
  implementation("io.sentry:sentry-spring-boot-starter-jakarta:$sentryVersion")

  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")

  compileOnly("org.wiremock:wiremock-standalone:$wiremockVersion")
  developmentOnly("org.wiremock:wiremock-standalone:$wiremockVersion")
  developmentOnly("com.h2database:h2")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:$hmppsSpringBootStarterVersion")
  testImplementation("com.h2database:h2")
  testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")
  testImplementation("io.swagger.parser.v3:swagger-parser:$swaggerParserVersion") {
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
