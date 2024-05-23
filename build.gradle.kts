plugins {
    id("java")
    id("war")
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("application")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass.set("com.henryhung.aws.ImageProcessingServer")
}

tasks.shadowJar {
    archiveBaseName.set("ImageProcessingServer")
    archiveVersion.set("0.1")
    archiveClassifier.set("")
}

tasks.war {
    archiveBaseName.set("MyWebApp")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")
}

dependencies {
    implementation("software.amazon.awssdk:s3:2.17.89")
    implementation("software.amazon.awssdk:sqs:2.17.89")
    implementation("software.amazon.awssdk:ec2:2.17.89")
    implementation("javax.servlet:javax.servlet-api:4.0.1")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}