# CircleGuard — Development Repository

Sistema de rastreo de contactos de salud universitaria. 8 microservicios Spring Boot 3.2.4 / Java 21 / Kotlin.

## Servicios

| Servicio | Puerto | Descripción |
|---|---|---|
| auth-service | 8180 | Autenticación JWT + LDAP |
| identity-service | 8083 | Gestión de identidades |
| notification-service | 8082 | Notificaciones push/email |
| form-service | 8086 | Formularios de seguimiento |
| gateway-service | 8087 | API Gateway + rate limiting |
| promotion-service | 8088 | Promoción de contactos (Neo4j + PostgreSQL) |
| dashboard-service | 8084 | Dashboard administrativo |
| file-service | 8085 | Gestión de archivos |

## Requisitos locales

- Java 21
- Docker + Docker Compose
- Gradle 8+

## Ejecución local

**1. Levantar infraestructura:**
```bash
docker compose -f docker-compose.dev.yml up -d
```

Esto inicia: PostgreSQL (5432), Neo4j (7687), Kafka (9092), Redis (6379), OpenLDAP (389).

**2. Crear bases de datos:**
```bash
docker exec -i $(docker ps -qf name=postgres) psql -U admin -f /docker-entrypoint-initdb.d/init-db.sql
```

O aplica `init-db.sql` manualmente.

**3. Correr un servicio:**
```bash
./gradlew :services:circleguard-auth-service:bootRun
```

**4. Correr todos los servicios:**
```bash
# En terminales separadas o con un process manager
for svc in auth-service identity-service notification-service form-service gateway-service promotion-service dashboard-service file-service; do
  ./gradlew :services:circleguard-$svc:bootRun &
done
```

## Tests

```bash
# Unit tests (excluye E2E e Integration)
./gradlew unitTest

# Integration tests (requiere Docker)
./gradlew integrationTest

# Coverage report
./gradlew jacocoTestReport
# → Reporte en services/<service>/build/reports/jacoco/

# Performance tests (requiere servicios corriendo)
cd tests/performance
pip install locust
locust -f locustfile.py --host=http://localhost:8087
```

## Build de imágenes Docker

```bash
# Build individual (reemplaza <service> y <port>)
./gradlew :services:circleguard-auth-service:bootJar
docker build \
  --build-arg JAR_FILE=services/circleguard-auth-service/build/libs/circleguard-auth-service.jar \
  -t cgregistry.azurecr.io/circleguard/circleguard-auth-service:dev-local \
  services/circleguard-auth-service/
```

## CI/CD Pipeline

### GitHub Actions (CI)
- `ci-develop.yml` — push a `develop`: build + unit tests + SonarQube + Trivy → push `:dev-{SHA}` a ACR
- `ci-release.yml` — push a `release/*`: + integration tests → push `:staging-{SHA}` → trigger Jenkins staging
- `ci-main.yml` — push a `main`: + OWASP ZAP + Locust → push `:prod-{SHA}` → aprobación manual → Jenkins prod

### Jenkinsfile (CI alternativo)
`Jenkinsfile` en la raíz implementa el mismo pipeline para entornos Jenkins:
- Multi-branch: detecta `develop`, `release/*`, `main` automáticamente
- Stages: Checkout → Build → Unit Tests → SonarQube → Integration Tests → Docker Build → Trivy → Push ACR → Trigger CD

## Spring Boot Profiles

Los servicios usan `SPRING_PROFILES_ACTIVE` para seleccionar la configuración de ambiente:
- `dev` → `application-dev.yml` (DNS k8s `circleguard-dev`, DEBUG logging)
- `stage` → `application-stage.yml` (DNS k8s `circleguard-stage`, INFO logging)
- `prod` → `application-prod.yml` (DNS k8s `circleguard-prod`, WARN logging)

Los profiles están definidos en los 4 servicios core: auth, identity, gateway, promotion.

## Branching

```
main ← release/* ← develop ← feature/*
```

Convención de commits: `feat:`, `fix:`, `chore:`, `BREAKING CHANGE:` (semver automático en CI).

## Estructura

```
circle-guard-development/
├── .github/workflows/       # GitHub Actions CI
├── Jenkinsfile              # Jenkins CI alternativo
├── services/                # 8 microservicios Spring Boot
├── mobile/                  # App móvil Expo/React Native
├── tests/
│   ├── e2e/                 # Tests end-to-end (REST Assured)
│   └── performance/         # Locust performance tests
├── docker-compose.dev.yml   # Infraestructura local
└── docker-compose.test.yml  # Infraestructura para integration tests
```
