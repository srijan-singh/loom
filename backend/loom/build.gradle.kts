plugins {
    id("java")
    id("com.gradleup.shadow") version "9.0.0-beta4"
    id("com.diffplug.spotless") version "7.0.4"
}

group = "com.loom"
version = "1.0-SNAPSHOT"

val lombokVersion = project.property("lombokVersion").toString()
val jspecifyVersion = project.property("jspecifyVersion").toString()
val javalinVersion = project.property("javalinVersion").toString()
val jacksonVersion = project.property("jacksonVersion").toString()
val slf4jVersion = project.property("slf4jVersion").toString()
val sqliteJdbcVersion = project.property("sqliteJdbcVersion").toString()

val junitVersion = project.property("junitVersion").toString()
val mockitoVersion = project.property("mockitoVersion").toString()
val okhttpVersion = project.property("okhttpVersion").toString()
val assertjVersion = project.property("assertjVersion").toString()

repositories {
    mavenCentral()
}

dependencies {
    // SSE
    implementation("io.javalin:javalin:$javalinVersion")

    // Databind
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

    // SQLite
    implementation("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")

    // HTTP client for LLM providers
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")

    // Lombok dependency configuration
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    compileOnly("org.jspecify:jspecify:$jspecifyVersion")

    // Logger
    implementation("org.slf4j:slf4j-simple:$slf4jVersion")

    // Required to use Lombok inside unit tests
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
    testImplementation("io.javalin:javalin-testtools:$javalinVersion")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okhttpVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ── Spotless: formatting + licence enforcement ─────────────────────────────

val licenseHeader = """
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
""".trimIndent()

spotless {
    java {
        // Enforce the Apache licence header on every .java file
        licenseHeader(licenseHeader)

        // Google Java Format keeps indentation at 2 spaces per its spec,
        // which is the de-facto standard formatter for Java at Google scale.
        // Use AOSP style (4-space indent) to match the project's existing code.
        googleJavaFormat("1.25.2").aosp().reflowLongStrings(false)

        // Trim trailing whitespace and ensure a single newline at EOF
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// spotlessCheck runs as part of the standard `check` lifecycle so the build
// breaks automatically on any formatting or licence violation.
tasks.named("check") {
    dependsOn("spotlessCheck")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("loom-engine")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "com.loom.Main"
    }
}