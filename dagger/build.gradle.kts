plugins {
    id("java-library")
    id("com.vanniktech.maven.publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation("javax.inject:javax.inject:1")
    implementation("com.google.guava:guava:19.0")
    implementation("com.google.code.findbugs:jsr305:2.0.1")
}
