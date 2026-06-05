dependencies {
    // Provide BOM to annotationProcessor configs so Lombok version resolves
    // (subprojects{} adds Lombok to annotationProcessor without io.spring.dependency-management)
    "annotationProcessor"(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
    "testAnnotationProcessor"(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))

    "testImplementation"("io.rest-assured:rest-assured:5.4.0")
}

tasks.withType<Test> {
    // Per-service URL defaults matching docker-compose.test.yml port mappings
    systemProperty("e2e.auth.url",      System.getProperty("e2e.auth.url",      "http://localhost:8180"))
    systemProperty("e2e.gateway.url",   System.getProperty("e2e.gateway.url",   "http://localhost:8087"))
    systemProperty("e2e.promotion.url", System.getProperty("e2e.promotion.url", "http://localhost:8088"))
    systemProperty("e2e.form.url",      System.getProperty("e2e.form.url",      "http://localhost:8086"))
    // Only run when explicitly requested via `e2eTest` or `:tests:e2e:test` — not as part of `./gradlew test`
    onlyIf {
        gradle.startParameter.taskNames.any { name ->
            name.lowercase().let { it == "e2etest" || it == ":tests:e2e:test" }
        }
    }
}
