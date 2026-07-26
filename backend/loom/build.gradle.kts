plugins {
    id("java")
    id("com.gradleup.shadow") version "9.0.0-beta4"
}

group = "com.loom"
version = "1.0-SNAPSHOT"

val lombokVersion = project.property("lombokVersion").toString()
val jspecifyVersion = project.property("jspecifyVersion").toString()
val javalinVersion = project.property("javalinVersion").toString()
val jacksonVersion = project.property("jacksonVersion").toString()
val slf4jVersion = project.property("slf4jVersion").toString()

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
    testImplementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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