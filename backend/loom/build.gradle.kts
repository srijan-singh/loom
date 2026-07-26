plugins {
    id("java")
}

group = "com.loom"
version = "1.0-SNAPSHOT"

val lombokVersion: String by project
val jspecifyVersion: String by project

repositories {
    mavenCentral()
}

dependencies {
    // Lombok dependency configuration
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    compileOnly("org.jspecify:jspecify:$jspecifyVersion")

    // Required to use Lombok inside unit tests
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}