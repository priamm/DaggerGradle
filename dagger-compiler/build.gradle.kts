plugins {
    id("java")
    id("kotlin")
    id("kotlin-kapt")
    id("com.vanniktech.maven.publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(project(":dagger"))
    implementation("com.squareup:javapoet:1.5.0")
    implementation("com.google.auto:auto-common:0.5")
    compileOnly("com.google.auto.service:auto-service:1.0-rc2")
    compileOnly("com.google.auto.value:auto-value:1.0")
    kapt("com.google.auto.value:auto-value:1.0")
    implementation("com.google.googlejavaformat:google-java-format:1.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("javax.inject:javax.inject:1")
    annotationProcessor("com.google.auto.service:auto-service:1.0-rc2")

}
