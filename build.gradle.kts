plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "9.1.1"
  id("org.jetbrains.kotlin.plugin.jpa") version "2.2.20"
  kotlin("plugin.spring") version "2.2.20"
  id("idea")
}

val hmppsStarterVersion = "1.7.0"
val springCoreVersion = "6.2.11"
val springSecurityCoreVersion = "6.5.4"
val azureIdentityVersion = "1.18.0"
val microsoftGraphVersion = "6.53.0"
val commonsLangVersion = "3.18.0"
val nettyCodecVersion = "4.1.125.Final"
val nettyCompressionVersion = "4.2.5.Final"
val wiremockVersion = "3.13.1"
val swaggerParserVersion = "2.1.34"
val springdocVersion = "2.8.13"

idea {
  module {
    resourceDirs.add(file("src/wiremock-stubs"))
  }
}

configurations {
  testImplementation {
    exclude(group = "org.junit.vintage")
  }
}

configurations.all {
  resolutionStrategy {
    force("io.netty:netty-codec:$nettyCodecVersion")
    force("io.netty:netty-codec-compression:$nettyCompressionVersion")
    force("org.apache.commons:commons-lang3:$commonsLangVersion")
  }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:$hmppsStarterVersion")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework:spring-core:$springCoreVersion")
  implementation("org.springframework.security:spring-security-core:$springSecurityCoreVersion")
  implementation("org.flywaydb:flyway-core")
  implementation("com.microsoft.graph:microsoft-graph:$microsoftGraphVersion")
  implementation("com.azure:azure-identity:$azureIdentityVersion")
  implementation("org.apache.commons:commons-lang3:$commonsLangVersion")
  implementation("io.netty:netty-codec:$nettyCodecVersion")
  implementation("io.netty:netty-codec-compression:$nettyCompressionVersion")

  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")

  compileOnly("org.wiremock:wiremock-standalone:$wiremockVersion")
  developmentOnly("org.wiremock:wiremock-standalone:$wiremockVersion")
  developmentOnly("com.h2database:h2")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:$hmppsStarterVersion")
  testImplementation("com.h2database:h2")
  testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")
  testImplementation("io.swagger.parser.v3:swagger-parser:$swaggerParserVersion") {
    exclude(group = "io.swagger.core.v3")
  }
}

kotlin {
  jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
}
