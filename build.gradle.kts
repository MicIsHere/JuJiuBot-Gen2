plugins {
    kotlin("jvm") version "2.0.21"
}

group = "cn.cutemic.bot"
version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("love.forte.simbot:simbot-core:4.10.0")
    implementation("love.forte.simbot.component:simbot-component-onebot-v11-core:1.5.0")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")
    implementation("org.apache.logging.log4j:log4j-api:2.24.3")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.24.3")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.reflections:reflections:0.10.2")
    implementation("com.hankcs.hanlp.restful:hanlp-restful:0.0.15")

    implementation("io.insert-koin:koin-ktor:4.0.2")

    implementation("org.jetbrains.exposed:exposed-core:0.59.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.59.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.59.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.59.0")
    implementation("com.impossibl.pgjdbc-ng:pgjdbc-ng:0.8.9")

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    runtimeOnly("io.ktor:ktor-client-java:2.3.13")
    testImplementation(kotlin("test"))

    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:5.3.0")
    implementation("org.mongodb:bson-kotlinx:5.3.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(19)
}