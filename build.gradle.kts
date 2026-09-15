plugins {
    id("java-library")
    id("io.freefair.lombok") version "9.5.0"
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "placeholderapi"
        url = uri("https://repo.helpch.at/releases/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.zaxxer:HikariCP:7.1.0")
    compileOnly("com.j256.ormlite:ormlite-jdbc:6.1")
    compileOnly("org.xerial:sqlite-jdbc:3.53.2.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }

    processResources {
        filteringCharset = "UTF-8"

        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
