plugins {
    groovy
    java
}

group = "com.hivemq.helmcharts"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.codehaus.groovy:groovy-all:3.0.8")
    implementation("org.testng:testng:7.5")
    implementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testImplementation("org.testcontainers:testcontainers:1.16.3")
    testImplementation("org.testcontainers:k3s:1.16.3")
    testImplementation("org.slf4j:slf4j-api:1.7.36")
    testImplementation("org.slf4j:slf4j-simple:1.7.36")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.testcontainers:junit-jupiter:1.16.3")
    testImplementation("io.kubernetes:client-java:14.0.0")
    testImplementation("com.hivemq:hivemq-mqtt-client:1.3.0")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}