plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("io.ktor.plugin") version "3.0.3"
    application
    jacoco
}

group = "com.catan"
version = "0.0.1"

application {
    mainClass.set("com.catan.ApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories { mavenCentral() }

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:3.0.3")
    implementation("io.ktor:ktor-server-netty-jvm:3.0.3")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.0.3")
    implementation("io.ktor:ktor-server-cors-jvm:3.0.3")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.0.3")
    implementation("io.ktor:ktor-server-websockets-jvm:3.0.3")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.0.3")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.jetbrains.exposed:exposed-core:0.57.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.57.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.57.0")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.0.3")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm:3.0.3")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
