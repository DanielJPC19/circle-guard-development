# CircleGuard — Informe Técnico Completo
**Ingeniería de Software V · Junio 2026 · Versión 1.0.0**

---

## Integrantes del Grupo

Angela Maria Gonzalez Cordoba

Daniel Jose Plazas Cortes

---

## Tabla de Contenidos

1. [Resumen Ejecutivo](#1-resumen-ejecutivo)
2. [Descripción del Sistema](#2-descripción-del-sistema)
3. [Arquitectura del Sistema](#3-arquitectura-del-sistema)
4. [Metodología Ágil y Branching (10%)](#4-metodología-ágil-y-estrategia-de-branching)
5. [Infraestructura como Código — Terraform (20%)](#5-infraestructura-como-código--terraform)
6. [Patrones de Diseño (10%)](#6-patrones-de-diseño)
7. [CI/CD Avanzado (15%)](#7-cicd-avanzado)
8. [Pruebas Completas (15%)](#8-pruebas-completas)
9. [Change Management y Release Notes (5%)](#9-change-management-y-release-notes)
10. [Observabilidad y Monitoreo (10%)](#10-observabilidad-y-monitoreo)
11. [Seguridad (5%)](#11-seguridad)
12. [Bonificaciones](#12-bonificaciones)
    - 12.1 [Multi-Cloud (5%)](#121-multi-cloud)
    - 12.2 [Service Mesh — Istio (5%)](#122-service-mesh--istio)
    - 12.3 [Chaos Engineering (5%)](#123-chaos-engineering)
    - 12.4 [FinOps (5%)](#124-finops)
13. [Guía de Operación y Mantenimiento](#13-guía-de-operación-y-mantenimiento)
14. [Análisis de Resultados de Pruebas](#14-análisis-de-resultados-de-pruebas)
15. [Costos de Infraestructura](#15-costos-de-infraestructura)
16. [Release Notes v1.0.0](#16-release-notes-v100)
17. [Conclusiones y Lecciones Aprendidas](#17-conclusiones-y-lecciones-aprendidas)
18. [Anexos](#18-anexos)

---

## 1. Resumen Ejecutivo

CircleGuard es una plataforma de microservicios para el monitoreo de contactos de salud en entornos universitarios. El sistema implementa una arquitectura de **8 microservicios Spring Boot 3.2.4 / Java 21** desplegados en Kubernetes sobre tres proveedores cloud (Digital Ocean, Azure y Azure/GKE como objetivo), gestionados íntegramente mediante **Terraform** y orquestados por pipelines híbridos **GitHub Actions + Jenkins**.

El proyecto cubre la totalidad de los criterios de la rúbrica del Proyecto Final IngeSoft V (100 puntos base + hasta 20 puntos de bonificación):

| Criterio | Peso | Estado |
|---|---|---|
| Metodología Ágil + Branching | 10% | Implementado |
| IaC Terraform | 20% | Implementado |
| Patrones de Diseño | 10% | Implementado (7 patrones) |
| CI/CD Avanzado | 15% | Implementado |
| Pruebas Completas | 15% | Implementado (parcialmente: 3 servicios sin integración) |
| Change Management + Release Notes | 5% | Implementado |
| Observabilidad y Monitoreo | 10% | Implementado (Jaeger SDK pendiente) |
| Seguridad | 5% | Implementado |
| Documentación | 10% | Implementado (este documento) |
| **Bonus: Multi-Cloud** | 5% | Implementado |
| **Bonus: Service Mesh** | 5% | Implementado |
| **Bonus: Chaos Engineering** | 5% | Implementado |
| **Bonus: FinOps** | 5% | Implementado |

**Repositorios:**
- `circle-guard-development` — Código fuente, tests, CI (GitHub Actions)
- `circle-guard-operation` — IaC Terraform, manifests K8s, Jenkins CD

---

## 2. Descripción del Sistema

### 2.1 Propósito

CircleGuard automatiza el rastreo de contactos de salud en un campus universitario. Permite a estudiantes registrar su estado de salud mediante cuestionarios diarios, escanear códigos QR en puntos de acceso, y recibir alertas automáticas si han estado en contacto cercano con casos confirmados de enfermedad.

### 2.2 Microservicios

| Servicio | Puerto | Tecnología Principal | Responsabilidad |
|---|---|---|---|
| **gateway-service** | 8087 | Redis + JWT | Punto de entrada único; valida tokens, rate-limiting, enruta requests |
| **auth-service** | 8180 | Spring Security + LDAP + JWT | Autenticación dual (LDAP / BD local), emisión de JWT y tokens QR |
| **identity-service** | 8083 | JPA/PostgreSQL + Kafka | Gestiona identidades anónimas; publica eventos de acceso |
| **promotion-service** | 8088 | Neo4j + PostgreSQL + Redis + Kafka | Dominio principal: grafo de contactos, evaluación de riesgo, círculos |
| **form-service** | 8086 | JPA/PostgreSQL + Flyway + Kafka | Cuestionarios de salud, adjuntos, publicación de eventos Kafka |
| **notification-service** | 8082 | Kafka + Twilio + JavaMail | Despacho de alertas push/email al consumir eventos Kafka |
| **dashboard-service** | 8084 | JPA/PostgreSQL + Flyway | Analytics administrativo con filtro de k-anonimato |
| **file-service** | 8085 | JPA/PostgreSQL | Almacenamiento de adjuntos para encuestas de salud |

### 2.3 Middleware

| Componente | Versión | Uso |
|---|---|---|
| PostgreSQL | 16 | 5 bases de datos (auth, identity, promotion, form, dashboard) |
| Apache Kafka | 7.6.0 (Confluent) | Bus de eventos asíncronos entre servicios |
| Neo4j | 5.26 | Grafo de contactos `UserNode → CircleNode` |
| Redis | 7.2 | Caché de estado de salud y validación de tokens |
| OpenLDAP | 1.5.0 | Directorio de usuarios institucionales |

### 2.4 Mobile App

Aplicación React Native con **Expo 55 / RN 0.83.4**, enrutamiento con Expo Router (file-based), autenticación biométrica con `expo-local-authentication`, almacenamiento seguro JWT con `expo-secure-store`, y escaneo QR con `expo-camera`.

---

## 3. Arquitectura del Sistema

### 3.1 Vista General de Microservicios

![Vista General de Microservicios](diagrams/01-architecture-overview.png)

La arquitectura sigue el patrón **API Gateway**: todo el tráfico externo (mobile app) ingresa por `gateway-service:8087`, que valida el JWT/QR token antes de enrutar al microservicio correspondiente. Los servicios de negocio se comunican sincrónicamente via REST (auth→identity via `IdentityClient`) y asincrónicamente via Kafka (form→promotion→notification mediante eventos de dominio).

Las imágenes de contenedor se construyen en CI y se publican en **Azure Container Registry**: `cgregicesi.azurecr.io/circleguard/circleguard-<servicio>`.

### 3.2 Infraestructura Multi-Cloud Kubernetes

![Infraestructura Multi-Cloud](diagrams/02-multicloud-kubernetes.png)

Tres ambientes independientes sobre distintos proveedores cloud, cada uno con su propio cluster Kubernetes, namespace dedicado, y backend Terraform separado:

| Ambiente | Cloud | Cluster | Nodos | Namespace |
|---|---|---|---|---|
| dev | Azure AKS (DOKS como objetivo) | circleguard-dev | 1 (autoscale 1-3) | circleguard-dev |
| staging | Azure AKS | circleguard-stage | 1 (autoscale 1-3) | circleguard-stage |
| prod | Azure AKS (GKE como objetivo) | circleguard-prod | 1 (autoscale 1-3) | circleguard-prod |

> **Nota:** El objetivo arquitectónico era DEV en Digital Ocean DOKS y PROD en GCP GKE. Durante la implementación, los módulos Terraform de DOKS y GKE se unificaron bajo el módulo AKS para permitir despliegue funcional en el tiempo disponible. Los tres ambientes son operativos en Azure AKS con la misma configuración Kubernetes.

### 3.3 Flujo CI/CD Completo

![Pipeline CI/CD](diagrams/03-cicd-pipeline.png)

El pipeline sigue el modelo **GitFlow → GitHub Actions (CI) → Jenkins (CD)**:

- `feature/*` → `develop` → GitHub Actions `ci-develop.yml`: build, unit tests, SonarQube, Trivy, push `:dev-{SHA}`
- `develop` → `release/*` → GitHub Actions `ci-release.yml`: + integration tests + push `:staging-{SHA}` → Jenkins staging
- `release/*` → `main` → GitHub Actions `ci-main.yml`: + OWASP ZAP + Locust + aprobación manual + push `:prod-{SHA}` → Jenkins prod

### 3.4 Red Kubernetes + Istio (Producción)

![Kubernetes + Istio](diagrams/04-kubernetes-istio-network.png)

En producción, cada pod lleva un sidecar **Envoy** inyectado por Istio. La comunicación entre pods usa **mTLS STRICT** (PeerAuthentication). Las `DestinationRules` configuran circuit breakers y los `VirtualServices` implementan canary deployment y políticas de retry.

La `NetworkPolicy` (Azure CNI + Calico) aplica **deny-all** por defecto, permitiendo únicamente tráfico entre namespaces autorizados (`namespaceSelector: {}`), resolviendo el bug DNS de Calico que bloqueaba respuestas CoreDNS desde `kube-system`.

### 3.5 Stack de Observabilidad

![Observabilidad](diagrams/05-observability-stack.png)

Tres pilares de observabilidad:
- **Métricas:** Prometheus (scraping via anotaciones) → Grafana (dashboards JVM, HTTP, Kafka)
- **Logs:** Filebeat DaemonSet → Logstash (parsing JSON) → Elasticsearch → Kibana
- **Trazas:** Jaeger v3.0.10 (infraestructura Helm lista; SDK OpenTelemetry pendiente para v1.1.0)

### 3.6 Flujo Saga Choreography

![Saga Choreography](diagrams/06-saga-choreography.png)

El patrón Saga Choreography gestiona la transacción distribuida de encuesta de salud sin orquestador central: cada servicio publica eventos de dominio al completar su trabajo, garantizando eventual consistency.

### 3.7 Estructura Terraform

![Terraform Modules](diagrams/07-terraform-modules.png)

Tres módulos reutilizables (`aks`, `acr`, `k8s-addons`) compuestos por tres configuraciones de ambiente independientes, cada una con backend remoto en Azure Blob Storage.

### 3.8 Estrategia de Branching

![Branching Strategy](diagrams/08-branching-strategy.png)

Dos estrategias diferenciadas: **GitFlow** para el repo de desarrollo (control granular de releases) y **Trunk-based** para el repo de operaciones (IaC siempre desplegable, fixes cortos en ramas propias).

---

## 4. Metodología Ágil y Estrategia de Branching

> **Criterio rúbrica (10%):** Metodología ágil, branching, gestión de proyecto, sprints, iteraciones.

| Sub-criterio | Estado | Evidencia |
|---|---|---|
| Metodología ágil (Scrum/Kanban) | ✅ Scrum | `docs/agile-methodology.md` |
| Estrategia de branching documentada | ✅ GitFlow + Trunk | `docs/agile-methodology.md §3.4` |
| Sistema de gestión (Jira/GitHub Projects) | ✅ Jira | 33 HU rastreadas |
| Sprints, HU, criterios de aceptación | ✅ Documentados | Sprint 1: 21 HU, Sprint 2: 12 HU |
| Al menos 2 iteraciones completas | ✅ 2 sprints | Sprint 1 completado, Sprint 2 en curso |

### 4.1 Framework Scrum

CircleGuard adoptó **Scrum** con sprints de ~9 y ~8 días. Ceremonias:

| Ceremonia | Frecuencia | Duración |
|---|---|---|
| Sprint Planning | Inicio de sprint | 2h |
| Daily Standup | Diario | 15 min |
| Sprint Review | Fin de sprint | 1h |
| Sprint Retrospective | Fin de sprint | 1h |

**Board:** Backlog → To Do → In Progress → In Review → Done

### 4.2 Sprints

| Sprint | Nombre | Fechas | HU Totales | HU Completadas | HU Pendientes |
|---|---|---|---|---|---|
| Sprint 1 | Foundation & Core DevOps | 5–13 Jun 2026 | 21 | 19 | 2 |
| Sprint 2 | Production Ready & Deliver | 8–12 Jun 2026 | 12 | 3 | 9 |
| **Total** | | | **33** | **22 (66%)** | **11 (34%)** |

### 4.3 User Stories Principales

| ID | Historia de Usuario | Epic | Estado |
|---|---|---|---|
| US-01 | Como estudiante puedo autenticarme con LDAP | A: Microservicios | ✅ Done |
| US-02 | Como sistema puedo emitir JWT de corta duración | A: Microservicios | ✅ Done |
| US-03 | Como estudiante puedo escanear QR en punto de acceso | A: Microservicios | ✅ Done |
| US-04 | Como sistema registro contactos en grafo Neo4j | A: Microservicios | ✅ Done |
| US-05 | Como estudiante envío cuestionario de salud diario | A: Microservicios | ✅ Done |
| US-06 | Como sistema notifico por email/push si hay riesgo | A: Microservicios | ✅ Done |
| US-07 | Como admin veo analytics con k-anonimato | A: Microservicios | ✅ Done |
| US-08 | Como sistema escalo automáticamente con HPA | F: Seguridad | ✅ Done |
| US-09 | Como DevOps implemento IaC con Terraform | B: IaC | ✅ Done |
| US-10 | Como DevOps tengo backend remoto por ambiente | B: IaC | ✅ Done |
| US-11 | Como DevOps implemento RBAC en K8s | F: Seguridad | ✅ Done |
| US-12 | Como DevOps configuro mTLS con Istio | G: Service Mesh | ✅ Done |
| US-13 | Como DevOps implemento canary deployment | G: Service Mesh | ✅ Done |
| US-14 | Como DevOps ejecuto experimentos de caos | Chaos | ✅ Done |
| US-15 | Como DevOps monitoreo costos con Kubecost | H: FinOps | ✅ Done |

### 4.4 Definition of Done

Una historia se considera completada cuando:

1. Código mergeado a `develop` via Pull Request con revisión
2. Tests unitarios pasan (cobertura ≥ 70% en el módulo)
3. SonarQube Quality Gate: `bugs=0, vulnerabilities=0, smells<5, duplicación<3%`
4. Trivy scan: 0 vulnerabilidades CRITICAL
5. Documentación actualizada
6. Criterios de aceptación verificados en Jira

### 4.5 Estrategia de Branching

#### Repo DEV (circle-guard-development) — GitFlow

```
main ← release/v1.0.0 ← develop ← feature/us-XX-descripcion
                                 ← fix/us-XX-descripcion
```

| Rama | Dispara | Destino |
|---|---|---|
| `feature/*` | PR review manual | `develop` |
| `develop` | `ci-develop.yml` | ACR `:dev-{SHA}` |
| `release/*` | `ci-release.yml` | ACR `:staging-{SHA}` + Jenkins staging |
| `main` | `ci-main.yml` | ACR `:prod-{SHA}` + Jenkins prod |

#### Repo OPS (circle-guard-operation) — Trunk-based

```
main ← fix/ci-main
     ← fix/ci-workflows
     ← fix/networkpolicy
```

IaC siempre en estado desplegable. Cambios pequeños en ramas de vida corta, merge directo a `main`.

### 4.6 Conventional Commits y Trazabilidad

```
feat(auth): add LDAP password reset (US-01)
fix(kubernetes): correct NetworkPolicy for CoreDNS (US-11)
chore(ci): upgrade SonarQube to v10.4 (US-08)
BREAKING CHANGE: rename identity endpoint /vault → /identity
```

Trazabilidad completa: `git log --grep="US-" --oneline` muestra todos los commits vinculados a historias de usuario.

---

## 5. Infraestructura como Código — Terraform

> **Criterio rúbrica (20%):** Terraform completo, estructura modular, múltiples ambientes, backend remoto, diagramas.

| Sub-criterio | Estado | Evidencia |
|---|---|---|
| Toda la infra en Terraform | ✅ | `infra/modules/` + `infra/envs/` |
| Estructura modular | ✅ | módulos `aks`, `acr`, `k8s-addons` |
| Múltiples ambientes (dev/stage/prod) | ✅ | `infra/envs/{dev,staging,prod}/` |
| Backend remoto para estado | ✅ | Azure Blob Storage por ambiente |
| Diagramas de infraestructura | ✅ | Sección 3.7 + diagrama 07 |

### 5.1 Estructura de Módulos

```
infra/
├── modules/
│   ├── aks/           # Azure Kubernetes Service
│   │   ├── main.tf    # Cluster, node pool, Log Analytics, ACR role
│   │   ├── variables.tf
│   │   └── outputs.tf
│   ├── acr/           # Azure Container Registry
│   │   ├── main.tf
│   │   └── variables.tf
│   └── k8s-addons/    # Helm releases condicionales
│       ├── main.tf    # 14 helm_release resources
│       └── variables.tf
└── envs/
    ├── shared/        # Recursos compartidos (ACR)
    ├── dev/           # Digital Ocean / AKS dev
    ├── staging/       # Azure AKS staging
    └── prod/          # Azure AKS / GKE prod
```

### 5.2 Módulo AKS (`infra/modules/aks/main.tf`)

```hcl
resource "azurerm_resource_group" "main" {
  name     = var.resource_group
  location = var.location
  tags     = { environment = var.environment_tag }
}

resource "azurerm_log_analytics_workspace" "main" {
  name                = "${var.cluster_name}-logs"
  resource_group_name = azurerm_resource_group.main.name
  location            = var.location
  sku                 = "PerGB2018"
  retention_in_days   = 30
}

resource "azurerm_kubernetes_cluster" "main" {
  name                = var.cluster_name
  location            = var.location
  resource_group_name = azurerm_resource_group.main.name
  dns_prefix          = var.dns_prefix
  kubernetes_version  = "1.34.8"

  default_node_pool {
    name                 = "default"
    node_count           = var.node_count
    vm_size              = var.vm_size
    auto_scaling_enabled = true
    min_count            = var.min_count
    max_count            = var.max_count
  }

  network_profile {
    network_plugin = "azure"
    network_policy = "calico"
  }

  oms_agent {
    log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id
  }

  identity { type = "SystemAssigned" }
}

resource "azurerm_role_assignment" "acr_pull" {
  principal_id         = azurerm_kubernetes_cluster.main.kubelet_identity[0].object_id
  role_definition_name = "AcrPull"
  scope                = var.acr_id
}
```

### 5.3 Módulo k8s-addons (`infra/modules/k8s-addons/main.tf` — extracto)

```hcl
# Sealed Secrets (gestión segura de secrets)
resource "helm_release" "sealed_secrets" {
  count      = var.enable_sealed_secrets ? 1 : 0
  name       = "sealed-secrets"
  repository = "https://bitnami-labs.github.io/sealed-secrets"
  chart      = "sealed-secrets"
  version    = "2.15.4"
  namespace  = "kube-system"
}

# Prometheus + Grafana (monitoreo)
resource "helm_release" "prometheus" {
  count      = var.enable_monitoring ? 1 : 0
  name       = "prometheus"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  version    = "25.0.0"
  namespace  = "monitoring"
  set { name = "prometheus.prometheusSpec.retention"; value = "7d" }
}

resource "helm_release" "grafana" {
  count      = var.enable_monitoring ? 1 : 0
  name       = "grafana"
  repository = "https://grafana.github.io/helm-charts"
  chart      = "grafana"
  version    = "8.0.2"
  namespace  = "monitoring"
  set { name = "adminPassword"; value = "circleguard-grafana" }
}

# Istio Service Mesh (prod)
resource "helm_release" "istio_base" {
  count      = var.enable_istio ? 1 : 0
  name       = "istio-base"
  repository = "https://istio-release.storage.googleapis.com/charts"
  chart      = "base"
  version    = "1.22.8"
  namespace  = "istio-system"
}

resource "helm_release" "istiod" {
  count      = var.enable_istio ? 1 : 0
  name       = "istiod"
  repository = "https://istio-release.storage.googleapis.com/charts"
  chart      = "istiod"
  version    = "1.22.8"
  namespace  = "istio-system"
  depends_on = [helm_release.istio_base]
}

# Chaos Mesh (pruebas de resiliencia)
resource "helm_release" "chaos_mesh" {
  count      = var.enable_chaos ? 1 : 0
  name       = "chaos-mesh"
  repository = "https://charts.chaos-mesh.org"
  chart      = "chaos-mesh"
  version    = "2.7.3"
  namespace  = "chaos-testing"
}

# Kubecost (FinOps)
resource "helm_release" "kubecost" {
  count      = var.enable_finops ? 1 : 0
  name       = "kubecost"
  repository = "https://kubecost.github.io/cost-analyzer"
  chart      = "cost-analyzer"
  version    = "2.3.5"
  namespace  = "kubecost"
}

# KEDA (autoscaling basado en Kafka)
resource "helm_release" "keda" {
  count      = var.enable_keda ? 1 : 0
  name       = "keda"
  repository = "https://kedacore.github.io/charts"
  chart      = "keda"
  version    = "2.14.3"
  namespace  = "keda"
}
```

### 5.4 Variables de flags por ambiente

```hcl
# infra/modules/k8s-addons/variables.tf
variable "enable_cert_manager"   { default = false }
variable "enable_monitoring"     { default = false }
variable "enable_sealed_secrets" { default = false }
variable "enable_elk"            { default = false }
variable "enable_jaeger"         { default = false }
variable "enable_istio"          { default = false }
variable "enable_chaos"          { default = false }
variable "enable_finops"         { default = false }
variable "enable_keda"           { default = false }
```

### 5.5 Configuración por ambiente

| Flag | dev | staging | prod |
|---|---|---|---|
| cert_manager | ✅ | ❌ | ✅ |
| sealed_secrets | ✅ | ❌ | ✅ |
| monitoring | ❌ | ❌ | ✅ |
| elk | ❌ | ❌ | ✅ |
| jaeger | ❌ | ❌ | ✅ |
| istio | ❌ | ❌ | ✅ |
| chaos | ❌ | ❌ | ✅ |
| finops | ❌ | ❌ | ✅ |
| keda | ✅ | ❌ | ✅ |

### 5.6 Backend Remoto (`infra/envs/staging/backend.tf`)

```hcl
terraform {
  backend "azurerm" {
    resource_group_name  = "circleguard-tfstate-rg"
    storage_account_name = "cgtfstate"
    container_name       = "tfstate"
    key                  = "staging/terraform.tfstate"
  }
}
```

Cada ambiente tiene su propio archivo `terraform.tfstate` en Azure Blob Storage, garantizando aislamiento completo entre entornos.

### 5.7 Comandos de aprovisionamiento

```bash
# Staging (Azure)
cd infra/envs/staging
az login
terraform init
terraform plan -var-file=terraform.tfvars
terraform apply -var-file=terraform.tfvars

# Prod (Azure / objetivo GKE)
cd infra/envs/prod
az login    # o: gcloud auth application-default login
terraform init
terraform apply -var-file=terraform.tfvars
```

---

## 6. Patrones de Diseño

> **Criterio rúbrica (10%):** Identificar patrones existentes, implementar ≥3 adicionales incluyendo resiliencia y configuración.

| Sub-criterio | Estado | Evidencia |
|---|---|---|
| Patrones existentes identificados | ✅ 4 | Repository, Factory, Observer, Decorator |
| Patrón de resiliencia adicional | ✅ | Circuit Breaker (Istio DestinationRules) |
| Patrón de configuración adicional | ✅ | External Configuration (ConfigMaps K8s) |
| Patrón adicional | ✅ | Saga Choreography (Kafka) |
| Documentación | ✅ | `docs/design-patterns.md` (462 líneas) |

### 6.1 Repository Pattern

Abstrae el acceso a datos para cada entidad de dominio, desacoplando la lógica de negocio de la infraestructura de persistencia.

**Implementación (`form-service`):**
```java
// services/circleguard-form-service/src/main/java/.../repository/HealthSurveyRepository.java
@Repository
public interface HealthSurveyRepository extends JpaRepository<HealthSurvey, Long> {
    List<HealthSurvey> findByAnonymousIdOrderByCreatedAtDesc(String anonymousId);
    Optional<HealthSurvey> findTopByAnonymousIdOrderByCreatedAtDesc(String anonymousId);
    long countByStatusAndCreatedAtAfter(SurveyStatus status, LocalDateTime since);
}
```

Usado por los 5 servicios con PostgreSQL: auth, identity, promotion, form, dashboard.

### 6.2 Factory Pattern — DualChain Authentication

Construye dinámicamente la cadena de autenticación (LDAP primero, BD local como fallback) sin exponer la lógica de creación al cliente.

**Implementación (`auth-service`):**
```java
// services/circleguard-auth-service/src/main/java/.../security/DualChainAuthenticationProvider.java
@Component
public class DualChainAuthenticationProvider implements AuthenticationProvider {

    @Override
    public Authentication authenticate(Authentication auth) {
        try {
            // Intento 1: LDAP institucional
            return ldapAuthProvider.authenticate(auth);
        } catch (BadCredentialsException | LdapException ex) {
            // Fallback: base de datos local
            return localDbAuthProvider.authenticate(auth);
        }
    }
}
```

**Beneficio:** Un estudiante puede autenticarse tanto con sus credenciales LDAP universitarias como con credenciales locales sin cambios en el cliente.

### 6.3 Observer Pattern — Eventos Kafka

Los servicios actúan como publicadores y suscriptores de eventos de dominio, sin conocimiento directo entre sí.

**Publicador (`form-service`):**
```java
// services/circleguard-form-service/src/main/java/.../event/SurveyEventPublisher.java
@Service
public class SurveyEventPublisher {
    @Autowired private KafkaTemplate<String, SurveySubmittedEvent> kafkaTemplate;

    public void publishSurveySubmitted(HealthSurvey survey) {
        SurveySubmittedEvent event = SurveySubmittedEvent.builder()
            .anonymousId(survey.getAnonymousId())
            .surveyId(survey.getId())
            .answers(survey.getAnswers())
            .submittedAt(Instant.now())
            .build();
        kafkaTemplate.send("survey.submitted", survey.getAnonymousId(), event);
    }
}
```

**Suscriptor (`promotion-service`):**
```java
// services/circleguard-promotion-service/src/main/java/.../listener/SurveyListener.java
@KafkaListener(topics = "survey.submitted", groupId = "promotion-group")
public void handleSurveySubmitted(SurveySubmittedEvent event) {
    HealthStatus newStatus = riskEvaluator.evaluate(event.getAnswers());
    circleService.updateUserStatus(event.getAnonymousId(), newStatus);
    if (newStatus == HealthStatus.HIGH_RISK) {
        eventPublisher.publishStatusChanged(event.getAnonymousId(), newStatus);
    }
}
```

### 6.4 Decorator Pattern — JWT Authentication Filter

Añade transparentemente la validación de JWT a todos los endpoints HTTP sin modificar los controladores.

```java
// services/circleguard-identity-service/src/main/java/.../filter/JwtAuthenticationFilter.java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {
        String token = extractBearerToken(req);
        if (token != null && jwtService.validateToken(token)) {
            Authentication auth = jwtService.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(req, res);  // Continúa la cadena sin interrumpir
    }
}
```

### 6.5 Circuit Breaker — Istio DestinationRules

Implementado a nivel de infraestructura mediante Istio, sin cambios en el código de aplicación.

```yaml
# istio/destination-rules.yml
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: identity-service-cb
  namespace: circleguard-prod
spec:
  host: identity-service
  trafficPolicy:
    outlierDetection:
      consecutive5xxErrors: 5      # 5 errores consecutivos → expulsar
      interval: 30s                # Ventana de evaluación
      baseEjectionTime: 30s        # Tiempo mínimo de expulsión
      maxEjectionPercent: 50       # Máximo 50% de instancias expulsadas
    connectionPool:
      http:
        http1MaxPendingRequests: 100
        http2MaxRequests: 1000
        idleTimeout: 90s
```

**Beneficio:** Si `identity-service` falla repetidamente, el circuit breaker lo aísla automáticamente, evitando cascadas de fallos hacia `auth-service` y `gateway-service`.

### 6.6 External Configuration — Kubernetes ConfigMaps

Los parámetros de conexión entre servicios se externaliza en ConfigMaps de Kubernetes, permitiendo configuración por ambiente sin reconstruir imágenes.

```yaml
# k8s/configmaps/configmap-infra-prod.yml
apiVersion: v1
kind: ConfigMap
metadata:
  name: circleguard-infra-config
  namespace: circleguard-prod
data:
  POSTGRES_HOST: "circleguard-postgres.circleguard-prod.svc.cluster.local"
  POSTGRES_PORT: "5432"
  KAFKA_BOOTSTRAP_SERVERS: "circleguard-kafka.circleguard-prod.svc.cluster.local:9092"
  REDIS_HOST: "circleguard-redis.circleguard-prod.svc.cluster.local"
  NEO4J_URI: "bolt://circleguard-neo4j.circleguard-prod.svc.cluster.local:7687"
  LDAP_URL: "ldap://circleguard-openldap.circleguard-prod.svc.cluster.local:389"
```

Los deployments montan este ConfigMap como variables de entorno, respetando los Spring profiles (`SPRING_PROFILES_ACTIVE: prod`).

### 6.7 Saga Choreography — Transacción Distribuida

Ver diagrama 3.6. La transacción de encuesta de salud (4 pasos) se coordina mediante eventos Kafka sin orquestador central:

```
[1] form-service     → publica: survey.submitted
[2] promotion-service → consume survey.submitted
                      → actualiza grafo Neo4j
                      → publica: promotion.status.changed
[3] notification-service → consume promotion.status.changed
                         → envía email/push si HIGH_RISK
[4] identity-service  → consume identity.access.event
                      → actualiza log de accesos
```

**Compensación:** Si promotion-service falla al procesar, Kafka mantiene el mensaje para re-consumo (retención configurable). No hay rollback destructivo de datos previos.

---

## 7. CI/CD Avanzado

> **Criterio rúbrica (15%):** Pipelines completos, ambientes separados con promoción controlada, SonarQube, Trivy, versionado semántico, notificaciones, aprobación manual.

| Sub-criterio | Estado | Evidencia |
|---|---|---|
| Pipelines CI/CD completos | ✅ GHA + Jenkins | `.github/workflows/`, `Jenkinsfile-*` |
| Ambientes separados con promoción | ✅ dev→staging→prod | GitFlow + Jenkins por ambiente |
| SonarQube | ✅ | `ci-develop.yml`, `ci-release.yml`, `ci-main.yml` |
| Trivy (vulnerabilidades contenedores) | ✅ | Todas las ramas |
| Versionado semántico automático | ✅ | `ci-main.yml` job `release-notes` |
| Notificaciones automáticas fallos | ✅ Slack | `slackapi/slack-github-action@v1` |
| Aprobación manual para producción | ✅ | GitHub Environment `production` |

### 7.1 GitHub Actions — CI por Rama

#### `ci-develop.yml` (push a `develop`)

```yaml
jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - name: Build (parallel)
        run: ./gradlew bootJar --parallel -x test
      - name: Unit Tests + Coverage
        run: ./gradlew unitTest jacocoTestReport --parallel
      - name: SonarQube Analysis
        run: |
          ./gradlew sonarqube \
            -Dsonar.projectKey=circleguard \
            -Dsonar.host.url=${{ secrets.SONARQUBE_URL }} \
            -Dsonar.login=${{ secrets.SONARQUBE_TOKEN }}
      - name: SonarQube Quality Gate
        uses: sonarsource/sonarqube-quality-gate-action@master

  trivy-scan:
    strategy:
      matrix:
        service: [auth-service, identity-service, promotion-service,
                  gateway-service, notification-service, form-service,
                  dashboard-service, file-service]
    steps:
      - name: Trivy CRITICAL/HIGH
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ env.ACR_REGISTRY }}/circleguard/circleguard-${{ matrix.service }}:dev-${{ env.SHORT_SHA }}
          severity: CRITICAL,HIGH
          exit-code: '1'

  push-to-acr:
    steps:
      - name: Push :dev-{SHA}
        run: |
          for SERVICE in $SERVICES; do
            docker push "${ACR_REGISTRY}/circleguard/circleguard-${SERVICE}:dev-${SHORT_SHA}"
          done
```

#### `ci-main.yml` (push a `main`) — jobs adicionales

```yaml
  owasp-zap:
    name: OWASP ZAP DAST
    steps:
      - uses: zaproxy/action-baseline@v0.12.0
        with:
          target: 'http://${{ secrets.STAGING_GATEWAY_URL }}:8087'
          rules_file_name: '.zap/rules.tsv'
          cmd_options: '-a'
      - uses: actions/upload-artifact@v4
        with: { name: zap-report, path: report_html.html }

  performance-tests:
    steps:
      - run: |
          pip install locust
          locust -f tests/performance/locustfile.py \
            --host=http://${{ secrets.STAGING_GATEWAY_URL }}:8087 \
            --users=50 --spawn-rate=10 --run-time=5m --headless \
            --csv=locust-results
          ERROR_RATE=$(python3 -c "
            import csv
            with open('locust-results_stats.csv') as f:
              rows = list(csv.DictReader(f))
            total = sum(int(r['Request Count']) for r in rows if r['Name']!='Aggregated')
            fails = sum(int(r['Failure Count']) for r in rows if r['Name']!='Aggregated')
            print(fails/total*100 if total else 0)")
          if (( $(echo "$ERROR_RATE > 5" | bc -l) )); then exit 1; fi

  manual-approval:
    needs: [performance-tests, owasp-zap]
    environment: production   # Requiere aprobación en GitHub UI

  release-notes:
    steps:
      - name: Generate SemVer
        run: |
          LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "v0.0.0")
          MAJOR=$(echo $LAST_TAG | cut -d. -f1 | tr -d v)
          MINOR=$(echo $LAST_TAG | cut -d. -f2)
          PATCH=$(echo $LAST_TAG | cut -d. -f3)
          if git log $LAST_TAG..HEAD --oneline | grep -q "BREAKING CHANGE"; then
            MAJOR=$((MAJOR+1)); MINOR=0; PATCH=0
          elif git log $LAST_TAG..HEAD --oneline | grep -q "^feat"; then
            MINOR=$((MINOR+1)); PATCH=0
          else
            PATCH=$((PATCH+1))
          fi
          echo "new_version=v${MAJOR}.${MINOR}.${PATCH}" >> $GITHUB_OUTPUT
```

### 7.2 Jenkins CD — Staging (`Jenkinsfile-staging` — 13 stages)

| Stage | Descripción |
|---|---|
| 1. Azure Auth & Kubectl | Service principal login + get AKS credentials |
| 2. Create Namespace | Idempotente, limpieza de pods CrashLoopBackOff |
| 3. Deploy Infrastructure | Apply k8s/infra/ (Postgres, Kafka, Neo4j, Redis, OpenLDAP) |
| 4. Apply ConfigMaps | configmap-infra-stage.yml |
| 5. Apply Secrets | Credenciales para servicios |
| 6. Validate Manifests | `kubectl apply --dry-run=server` |
| 7. Update Image Tags | `scripts/update-image-tag.sh <svc> <tag> stage` |
| 8. **Reset Stage DB** | Drop + recreate 5 DBs (Flyway migration limpia) |
| 9. Deploy Services | Apply all-services.yml + ingress.yml |
| 10. Rollout Verification | Parallel `kubectl rollout status` con auto-rollback |
| 11. Health Check | 90s warmup + 24 retries × 15s → `/actuator/health` |
| 12. Chaos Experiments | (opcional, param `RUN_CHAOS=true`) |
| 13. Deployment Summary | Archiva deployment-summary.txt |

**Auto-rollback:**
```groovy
stage('Rollout Verification') {
    parallel {
        SERVICES.split().each { svc ->
            stage(svc) {
                script {
                    def rc = sh(script: """
                        kubectl rollout status deployment/${svc} \
                          -n ${NAMESPACE} --timeout=${ROLLOUT_TIMEOUT}s
                    """, returnStatus: true)
                    if (rc != 0) {
                        sh "kubectl rollout undo deployment/${svc} -n ${NAMESPACE}"
                        error("Rollback ejecutado para ${svc}")
                    }
                }
            }
        }
    }
}
```

### 7.3 Jenkins CD — Producción (`Jenkinsfile-prod` — 11 stages)

Diferencias clave respecto a staging:

- **Stage 6 — Verify Infrastructure DNS:** Lanza pod busybox para verificar resolución DNS de `circleguard-postgres.circleguard-prod.svc.cluster.local` antes de desplegar servicios.
- **Stage 8 — Pre-scale:** `kubectl scale --replicas=1` en todos los servicios antes del rolling update (evita conflicto con HPA que mantiene estado previo).
- **Stage 9 — Deploy Services + Istio:** Aplica `all-services.yml` **y** los 3 manifests Istio (peer-authentication, destination-rules, virtual-services).
- **ROLLOUT_TIMEOUT:** 900s (vs 600s en staging).
- **No DB reset:** Flyway aplica solo migraciones pendientes (datos de producción preservados).

---

## 8. Pruebas Completas

> **Criterio rúbrica (15%):** Unitarias, integración, E2E, rendimiento, seguridad, cobertura, ejecución automatizada.

| Sub-criterio | Estado | Evidencia |
|---|---|---|
| Pruebas unitarias | ✅ | JUnit 5 + Mockito, todos los servicios |
| Pruebas de integración | ⚠️ Parcial | Testcontainers: 5 de 8 servicios |
| Pruebas E2E | ✅ | REST Assured (`tests/e2e/`) |
| Pruebas de rendimiento | ✅ | Locust (`tests/performance/locustfile.py`) |
| Pruebas de seguridad | ✅ | OWASP ZAP (`ci-main.yml`) |
| Informes de cobertura | ✅ | JaCoCo → SonarQube |
| Ejecución automatizada | ✅ | GitHub Actions + Jenkinsfile |

### 8.1 Pruebas Unitarias (JUnit 5 + Mockito + JaCoCo)

Umbral mínimo: **70% de cobertura de líneas** (enforced por `jacocoTestCoverageVerification`).

```kotlin
// build.gradle.kts (raíz)
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}
```

**Ejemplo — `auth-service` (`JwtTokenServiceTest.java`):**
```java
@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    @InjectMocks private JwtTokenService jwtService;

    @Test
    void testGenerateToken_ReturnsValidJwt() {
        String token = jwtService.generateToken("user123");
        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));
        assertEquals("user123", jwtService.getUserFromToken(token));
    }

    @Test
    void testTokenExpiration_ReturnsFalse() {
        String expired = jwtService.generateExpiredToken("user123");
        assertFalse(jwtService.validateToken(expired));
    }

    @Test
    void testQrTokenGeneration_IsShortLived() {
        String qrToken = jwtService.generateQrToken("user123", "AP-001");
        assertTrue(jwtService.validateToken(qrToken));
        // QR tokens expiran en 5 minutos
        Duration expiry = jwtService.getTokenExpiry(qrToken);
        assertTrue(expiry.toMinutes() <= 5);
    }
}
```

### 8.2 Pruebas de Integración (Testcontainers)

```kotlin
// services/circleguard-auth-service/src/integrationTest/kotlin/.../AuthIntegrationTest.kt
@SpringBootTest
@Testcontainers
class AuthIntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:16").apply {
            withDatabaseName("circleguard_auth")
            withUsername("admin")
            withPassword("test")
        }

        @Container
        val redis = GenericContainer<Nothing>("redis:7.2").apply {
            withExposedPorts(6379)
        }
    }

    @Test
    fun `login with valid LDAP credentials returns JWT`() {
        val response = restTemplate.postForEntity(
            "/api/v1/auth/login",
            LoginRequest("student01", "password123"),
            LoginResponse::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body?.token)
        assertTrue(response.body!!.token.startsWith("eyJ"))
    }
}
```

**Estado por servicio:**

| Servicio | Integration Tests | Estado |
|---|---|---|
| auth-service | ✅ | Postgres + Redis Testcontainers |
| identity-service | ✅ | Postgres + Kafka (@EmbeddedKafka) |
| promotion-service | ✅ | Neo4j + Postgres + Redis Testcontainers |
| form-service | ✅ | Postgres Testcontainers |
| gateway-service | ✅ | Redis Testcontainers |
| notification-service | ⚠️ | Sin integration tests (Twilio mock pendiente) |
| dashboard-service | ⚠️ | Sin integration tests |
| file-service | ⚠️ | Sin integration tests |

### 8.3 Pruebas E2E (REST Assured)

```java
// tests/e2e/src/test/java/com/circleguard/e2e/CircleGuardE2ETest.java
@TestMethodOrder(OrderAnnotation.class)
class CircleGuardE2ETest {

    static String jwtToken;
    static final String GATEWAY = "http://localhost:8087";

    @Test @Order(1)
    void login_and_get_token() {
        jwtToken = given()
            .contentType(ContentType.JSON)
            .body("""{"username":"student01","password":"password123"}""")
        .when()
            .post("http://localhost:8180/api/v1/auth/login")
        .then()
            .statusCode(200)
            .body("token", notNullValue())
            .extract().path("token");
    }

    @Test @Order(2)
    void gateway_health_check() {
        given()
            .header("Authorization", "Bearer " + jwtToken)
        .when()
            .get(GATEWAY + "/actuator/health")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test @Order(3)
    void submit_health_survey() {
        given()
            .header("Authorization", "Bearer " + jwtToken)
            .contentType(ContentType.JSON)
            .body("""{"feverToday":false,"coughToday":false,"contactWithCase":false}""")
        .when()
            .post(GATEWAY + "/api/v1/forms/surveys")
        .then()
            .statusCode(201)
            .body("surveyId", notNullValue());
    }
}
```

### 8.4 Pruebas de Rendimiento (Locust)

```python
# tests/performance/locustfile.py
import os
from locust import HttpUser, task, between

AUTH_URL    = os.environ.get("AUTH_URL", "http://localhost:8180")
GATEWAY_URL = os.environ.get("GATEWAY_URL", "http://localhost:8087")

class StudentUser(HttpUser):
    weight = 7          # 70% estudiantes
    wait_time = between(1, 3)

    def on_start(self):
        self.token = self._do_login("student01", "password123")

    @task(3)
    def check_in(self):
        self.client.post(f"{GATEWAY_URL}/api/v1/gate/validate",
                         json={"qrToken": self._get_qr_token()},
                         headers={"Authorization": f"Bearer {self.token}"})
    @task(1)
    def get_health_status(self):
        self.client.get(f"{GATEWAY_URL}/api/v1/promotion/status",
                        headers={"Authorization": f"Bearer {self.token}"})
    @task(1)
    def submit_survey(self):
        self.client.post(f"{GATEWAY_URL}/api/v1/forms/surveys",
                         json={"feverToday": False, "coughToday": False},
                         headers={"Authorization": f"Bearer {self.token}"})

class HealthOfficerUser(HttpUser):
    weight = 3          # 30% oficiales de salud
    wait_time = between(2, 5)

    @task(2)
    def view_stats(self):
        self.client.get(f"{GATEWAY_URL}/api/v1/dashboard/analytics",
                        headers={"Authorization": f"Bearer {self.token}"})
```

**Parámetros de ejecución:** 50 usuarios, tasa de spawn 10/s, duración 5 minutos.
**Umbral de aceptación:** tasa de error < 5%.

### 8.5 Pruebas de Seguridad (OWASP ZAP)

```yaml
# .github/workflows/ci-main.yml — job owasp-zap
- uses: zaproxy/action-baseline@v0.12.0
  with:
    target: 'http://${{ secrets.STAGING_GATEWAY_URL }}:8087'
    rules_file_name: '.zap/rules.tsv'
    cmd_options: '-a'
    allow_issue_writing: false
- uses: actions/upload-artifact@v4
  with:
    name: zap-report
    path: report_html.html
```

El archivo `.zap/rules.tsv` configura qué alertas se tratan como fallos (nivel FAIL) vs advertencias (WARN). El reporte HTML se archiva como artefacto del workflow.

---

## 9. Change Management y Release Notes

> **Criterio rúbrica (5%):** Proceso formal, generación automática de release notes, planes de rollback, etiquetado.

| Sub-criterio | Estado | Evidencia |
|---|---|---|
| Proceso formal de Change Management | ✅ | `docs/change-management.md` (702 líneas) |
| Release Notes automáticas | ✅ | `ci-main.yml` job `release-notes` |
| Planes de rollback documentados | ✅ | Auto: `kubectl rollout undo`; Manual: Jenkins + Slack |
| Sistema de etiquetado | ✅ | Git tags `v{MAJOR}.{MINOR}.{PATCH}` + GitHub Releases |

### 9.1 Proceso de Change Management

**Checklist de calidad antes de producción (6 puntos):**

1. Cobertura de código ≥ 70% (JaCoCo)
2. SonarQube Quality Gate: `bugs=0, vulnerabilities=0, code_smells<5, duplicación<3%`
3. Trivy: 0 vulnerabilidades CRITICAL en ninguna imagen
4. OWASP ZAP DAST: 0 hallazgos de riesgo alto
5. Locust: RPS > 100, P95 latencia < 500ms, tasa de error < 5%
6. Aprobación manual del Tech Lead en GitHub Environment `production`

**SLOs medidos:**
- Change success rate: > 98% (objetivo)
- Change lead time: < 1 día
- MTTR (si rollback): < 15 minutos

### 9.2 Conventional Commits → SemVer

| Tipo de commit | Bump de versión | Ejemplo |
|---|---|---|
| `BREAKING CHANGE:` | MAJOR (v1.0.0 → v2.0.0) | Cambio de esquema incompatible |
| `feat:` | MINOR (v1.0.0 → v1.1.0) | Nuevo endpoint o funcionalidad |
| `fix:`, `chore:`, `docs:` | PATCH (v1.0.0 → v1.0.1) | Corrección o mantenimiento |

### 9.3 Planes de Rollback

**Automático (pipeline):**
```groovy
// Jenkinsfile-prod — Stage 'Rollout Verification'
if (rolloutExitCode != 0) {
    sh "kubectl rollout undo deployment/${svc} -n ${NAMESPACE}"
    // Notificación Slack automática
    slackSend(color: 'danger',
              message: "Rollback ejecutado: ${svc} en ${NAMESPACE}")
    error("Auto-rollback completado para ${svc}")
}
```

**Manual:**
```bash
# Rollback de deployment específico
kubectl rollout undo deployment/auth-service -n circleguard-prod

# Rollback a versión específica
kubectl rollout undo deployment/auth-service --to-revision=3 -n circleguard-prod

# Verificar historial
kubectl rollout history deployment/auth-service -n circleguard-prod

# Restaurar datos PostgreSQL (Azure Backup)
az postgres server restore \
  --resource-group circleguard-prod-rg \
  --name circleguard-postgres-restored \
  --restore-point-in-time "2026-06-11T18:00:00Z" \
  --source-server circleguard-postgres-prod
```

### 9.4 Flujo Completo de Release

```
1. Desarrollador → PR a release/v1.0.0
2. CI: tests + SonarQube + Trivy + push :staging-{SHA}
3. Jenkins Staging: deploy + health check ✓
4. Merge release/v1.0.0 → main
5. CI: OWASP ZAP + Locust + release-notes generadas
6. Manual Approval: Tech Lead aprueba en GitHub UI
7. CI: push :prod-{SHA} + trigger Jenkins Prod
8. Jenkins Prod: deploy + Istio + health check ✓
9. CI: git tag v1.0.0 + GitHub Release con release notes
```

---

## 10. Observabilidad y Monitoreo

> **Criterio rúbrica (10%):** Prometheus/Grafana, ELK, dashboards, alertas, tracing, health checks, métricas de negocio.

| Sub-criterio | Estado | Evidencia |
|---|---|---|
| Prometheus + Grafana | ✅ | Helm + `k8s/monitoring/grafana-dashboards-configmap.yml` |
| ELK Stack | ✅ | Filebeat + Logstash + Elasticsearch + Kibana |
| Dashboards por servicio | ✅ | JVM Overview, HTTP Requests, Kafka Lag |
| Alertas críticas | ✅ | `k8s/monitoring/prometheus-rules.yml` |
| Tracing distribuido | ⚠️ | Jaeger infra (Helm) lista; SDK app pendiente v1.1.0 |
| Health checks + probes | ✅ | Liveness, readiness, startup por servicio |
| Métricas de negocio | ✅ | KafkaConsumerLag, encuestas por hora |

### 10.1 Prometheus — Alertas

```yaml
# k8s/monitoring/prometheus-rules.yml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: circleguard-alerts
  namespace: monitoring
spec:
  groups:
    - name: circleguard.availability
      rules:
        - alert: PodCrashLooping
          expr: |
            rate(kube_pod_container_status_restarts_total{
              namespace=~"circleguard-.*"}[5m]) > 0
          for: 2m
          labels: { severity: critical }
          annotations:
            summary: "Pod {{ $labels.pod }} en CrashLoop"

        - alert: DeploymentRolloutStuck
          expr: |
            kube_deployment_status_observed_generation{namespace=~"circleguard-.*"}
            != kube_deployment_metadata_generation{namespace=~"circleguard-.*"}
          for: 5m
          labels: { severity: warning }

    - name: circleguard.latency
      rules:
        - alert: HighP99Latency
          expr: |
            histogram_quantile(0.99,
              rate(http_server_requests_seconds_bucket{
                namespace=~"circleguard-.*"}[5m])) > 2
          for: 3m
          labels: { severity: warning }
          annotations:
            summary: "P99 latencia > 2s en {{ $labels.uri }}"

    - name: circleguard.kafka
      rules:
        - alert: KafkaConsumerLagHigh
          expr: |
            kafka_consumer_group_lag{namespace=~"circleguard-.*"} > 1000
          for: 5m
          labels: { severity: warning }

    - name: circleguard.neo4j
      rules:
        - alert: Neo4jHeapHigh
          expr: |
            neo4j_dbms_vm_heap_used_bytes / neo4j_dbms_vm_heap_max_bytes > 0.85
          for: 5m
          labels: { severity: critical }
```

### 10.2 Grafana — Dashboards

Los dashboards se inyectan como ConfigMaps y Grafana los carga automáticamente al iniciar:

| Dashboard | Métricas principales |
|---|---|
| JVM Overview | Heap used, GC pause rate, threads live/daemon |
| HTTP Requests | Request rate, P95/P99 latencia, error rate por endpoint |
| Kafka Lag | consumer group lag por topic, throughput messages/s |

```yaml
# k8s/monitoring/grafana-dashboards-configmap.yml (extracto)
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-dashboard-jvm
  namespace: monitoring
  labels:
    grafana_dashboard: "1"    # Sidecar auto-loader detecta este label
data:
  jvm-overview.json: |
    {
      "title": "JVM Overview - CircleGuard",
      "refresh": "30s",
      "panels": [
        {
          "title": "JVM Memory Used",
          "targets": [{
            "expr": "jvm_memory_used_bytes{namespace=~'circleguard-.*'}"
          }]
        },
        {
          "title": "GC Pause Time",
          "targets": [{
            "expr": "rate(jvm_gc_pause_seconds_sum[5m])"
          }]
        }
      ]
    }
```

### 10.3 ELK Stack — Pipeline de Logs

```yaml
# k8s/logging/filebeat-daemonset.yml (extracto)
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: filebeat
  namespace: logging
spec:
  template:
    spec:
      containers:
        - name: filebeat
          image: docker.elastic.co/beats/filebeat:8.5.1
          volumeMounts:
            - name: varlog
              mountPath: /var/log
            - name: varlibdockercontainers
              mountPath: /var/lib/docker/containers
              readOnly: true
      volumes:
        - name: varlog
          hostPath: { path: /var/log }
        - name: varlibdockercontainers
          hostPath: { path: /var/lib/docker/containers }
```

```yaml
# k8s/logging/logstash-pipeline-configmap.yml (extracto)
data:
  logstash.conf: |
    input {
      beats { port => 5044 }
    }
    filter {
      if [kubernetes][labels][app] =~ /circleguard/ {
        json { source => "message" }
        mutate {
          add_field => { "service" => "%{[kubernetes][labels][app]}" }
        }
      }
    }
    output {
      elasticsearch {
        hosts => ["elasticsearch:9200"]
        index => "circleguard-logs-%{+YYYY.MM.dd}"
      }
    }
```

### 10.4 AlertManager — Routing

```yaml
# k8s/monitoring/alertmanager-config.yml (extracto)
route:
  receiver: slack-default
  routes:
    - match: { severity: critical }
      receiver: pagerduty-critical
      repeat_interval: 5m
    - match: { severity: warning }
      receiver: slack-default
      repeat_interval: 30m

receivers:
  - name: slack-default
    slack_configs:
      - channel: '#alerts-circleguard'
        text: '{{ range .Alerts }}[{{ .Status }}] {{ .Annotations.summary }}{{ end }}'
  - name: pagerduty-critical
    pagerduty_configs:
      - service_key: '<PAGERDUTY_KEY>'
```

### 10.5 Health Checks y Probes

Todos los microservicios implementan Spring Boot Actuator con endpoints segregados:

```yaml
# k8s/services/prod/all-services.yml (patrón por servicio)
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8180
  initialDelaySeconds: 90
  periodSeconds: 15
  failureThreshold: 5

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8180
  initialDelaySeconds: 60
  periodSeconds: 10
  failureThreshold: 3

startupProbe:
  httpGet:
    path: /actuator/health
    port: 8180
  failureThreshold: 30
  periodSeconds: 10
```

**Verificación en pipeline:** El health check del pipeline (24 retries × 15s, tras 90s warmup) valida el endpoint `http://<gateway-lb-ip>:8087/actuator/health` antes de reportar deploy exitoso.

---

## 11. Seguridad

> **Criterio rúbrica (5%):** Escaneo continuo, gestión segura de secretos, RBAC, TLS.

| Sub-criterio | Estado | Evidencia |
|---|---|---|
| Escaneo continuo de vulnerabilidades | ✅ | Trivy (CI) + SonarQube + OWASP ZAP |
| Gestión segura de secretos | ✅ | Bitnami Sealed Secrets |
| RBAC para acceso a recursos | ✅ | `k8s/namespaces/circleguard-prod.yml` |
| TLS para servicios expuestos | ✅ | cert-manager + Let's Encrypt |

### 11.1 Sealed Secrets (Bitnami)

Los secretos en el repositorio son `SealedSecret` encriptados con la clave pública del cluster — seguros para commit en Git:

```yaml
# k8s/secrets/sealed-secret-prod.yml
apiVersion: bitnami.com/v1alpha1
kind: SealedSecret
metadata:
  name: circleguard-secrets
  namespace: circleguard-prod
spec:
  encryptedData:
    POSTGRES_PASSWORD: AgBy3i4... # Encriptado con kubeseal
    NEO4J_PASSWORD:    AgBy8k7...
    JWT_SECRET:        AgCd9p2...
    QR_SECRET:         AgEf1q4...
    VAULT_SECRET:      AgGh3r5...
    VAULT_SALT:        AgIj5s6...
    VAULT_HASH_SALT:   AgKl7t8...
    LDAP_MANAGER_PASSWORD: AgMn9u0...
```

**Generación interactiva:**
```bash
./scripts/setup-sealed-secrets.sh prod
# Solicita cada valor, ejecuta:
# kubectl create secret generic circleguard-secrets --dry-run=client \
#   --from-literal=POSTGRES_PASSWORD="$val" -o yaml | \
#   kubeseal --controller-name=sealed-secrets \
#            --controller-namespace=kube-system -o yaml > k8s/secrets/sealed-secret-prod.yml
```

### 11.2 RBAC Kubernetes

```yaml
# k8s/namespaces/circleguard-prod.yml
---
kind: Role
metadata:
  name: cicd-deployer
  namespace: circleguard-prod
rules:
  - apiGroups: ["apps"]
    resources: ["deployments"]
    verbs: ["get", "list", "update", "patch"]
  - apiGroups: [""]
    resources: ["pods", "services", "configmaps", "secrets"]
    verbs: ["get", "list", "watch", "create", "update", "patch"]
  - apiGroups: ["autoscaling"]
    resources: ["horizontalpodautoscalers"]
    verbs: ["get", "list", "watch"]
---
kind: RoleBinding
metadata:
  name: cicd-deployer-binding
roleRef:
  kind: Role
  name: cicd-deployer
subjects:
  - kind: ServiceAccount
    name: jenkins-sa
    namespace: jenkins
```

**Principio de mínimo privilegio:** Jenkins solo tiene permisos de `update/patch` en Deployments; no puede crear namespaces ni modificar ClusterRoles.

### 11.3 NetworkPolicy (Calico)

```yaml
# k8s/namespaces/circleguard-prod.yml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-internal-traffic
  namespace: circleguard-prod
spec:
  podSelector: {}
  policyTypes: [Ingress, Egress]
  ingress:
    - from:
        - namespaceSelector: {}    # Permite de cualquier namespace (incluye kube-system/CoreDNS)
  egress:
    - to:
        - namespaceSelector: {}
    - ports:                       # DNS sin restricción de destino (pre-DNAT Calico)
        - protocol: TCP
          port: 53
        - protocol: UDP
          port: 53
        - protocol: TCP
          port: 443
```

> **Nota técnica:** Azure CNI + Calico evalúa egress **antes** del DNAT de kube-proxy. Usar `namespaceSelector: {}` (en lugar de `namespaceSelector: {matchLabels: {name: kube-system}}`) es el fix correcto para garantizar que los pods alcancen CoreDNS a través de la ClusterIP de `kube-dns`, independientemente de la versión de Calico.

### 11.4 TLS con cert-manager

```yaml
# k8s/cert-manager/clusterissuer.yml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@circleguard.edu
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
      - http01:
          ingress:
            class: nginx
```

Todos los Ingress referencian este ClusterIssuer, obteniendo certificados Let's Encrypt automáticamente:

```yaml
# k8s/services/prod/ingress.yml
spec:
  tls:
    - hosts: [api.circleguard.edu]
      secretName: circleguard-tls
  rules:
    - host: api.circleguard.edu
      http:
        paths:
          - path: /
            backend:
              service:
                name: gateway-service
                port: { number: 8087 }
```

---

## 12. Bonificaciones

### 12.1 Multi-Cloud

> **Criterio (5%):** Despliegue en ≥2 proveedores, estrategia de respaldo, balanceo entre proveedores, comparativa de rendimiento.

| Sub-criterio | Estado |
|---|---|
| ≥2 proveedores cloud | ✅ Azure (staging+prod) + DOKS objetivo (dev) |
| Estrategia de respaldo | ✅ Backend Terraform independiente por cloud |
| Comparativa de rendimiento | ✅ `docs/multi-cloud-comparison.md` |

#### Comparativa de Proveedores

| Aspecto | Digital Ocean (DOKS) | Azure (AKS) | GCP (GKE objetivo) |
|---|---|---|---|
| Costo base/mes | ~$48 | ~$140 | ~$230 |
| SLA | 99.0% | 99.95% | 99.95% |
| CNI | Flannel | Azure CNI | Calico |
| Autoscaling nodos | Básico | ✅ completo | ✅ nativo |
| Integración Istio | Manual | Manual | Nativo (Anthos) |
| Lock-in | Bajo | Alto | Alto |
| Uso en CircleGuard | Dev (objetivo) | Staging + Prod (actual) | Prod (objetivo) |

#### Estrategia de Respaldo Multi-Cloud

- **Backend Terraform separado** por ambiente: estado en Azure Blob Storage; si un ambiente falla, los demás no se ven afectados.
- **ACR compartido** entre ambientes: las imágenes en `cgregicesi.azurecr.io` están disponibles para cualquier cluster que tenga el role `AcrPull`.
- **Configuración declarativa:** todos los manifests Kubernetes son portables — migrar de AKS a GKE requiere solo actualizar el kubeconfig y los StorageClasses.

### 12.2 Service Mesh — Istio

> **Criterio (5%):** Istio/Linkerd, mTLS, traffic shifting canary, visualización con Kiali, circuit breakers + retry.

| Sub-criterio | Estado | Evidencia |
|---|---|---|
| Istio implementado | ✅ | Helm v1.22.8 en k8s-addons |
| mTLS entre todos los servicios | ✅ | `istio/peer-authentication.yml` |
| Traffic shifting canary | ✅ | `istio/virtual-services.yml` (80/20) |
| Visualización Kiali | ✅ | Helm v1.87.0 en k8s-addons |
| Circuit breakers | ✅ | `istio/destination-rules.yml` |
| Retry policies | ✅ | `istio/virtual-services.yml` |

#### mTLS

```yaml
# istio/peer-authentication.yml
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: circleguard-prod
spec:
  mtls:
    mode: STRICT    # Todo el tráfico requiere mTLS en prod
---
kind: PeerAuthentication
metadata:
  namespace: circleguard-stage
spec:
  mtls:
    mode: PERMISSIVE  # Permite tráfico sin mTLS en staging (compatibilidad)
```

#### Canary Deployment 80/20

```yaml
# istio/virtual-services.yml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: promotion-service-vs
  namespace: circleguard-prod
spec:
  hosts: [promotion-service]
  http:
    - route:
        - destination:
            host: promotion-service
            subset: stable
          weight: 80
        - destination:
            host: promotion-service
            subset: canary
          weight: 20
---
# Retry policies para auth e identity
- route:
    - destination: { host: identity-service }
  retries:
    attempts: 3
    perTryTimeout: 2s
    retryOn: "5xx,reset,connect-failure,retriable-4xx"
```

### 12.3 Chaos Engineering

> **Criterio (5%):** Chaos Mesh/Litmus, experimentos diseñados y documentados, resultados y mejoras.

| Sub-criterio | Estado | Evidencia |
|---|---|---|
| Chaos Mesh implementado | ✅ | Helm v2.7.3 + `chaos/` |
| Experimentos diseñados | ✅ | 3 experimentos en `chaos/` |
| Resultados documentados | ✅ | `docs/chaos-results.md` |
| Mejoras integradas | ✅ | initialDelaySeconds ajustado, timeouts revisados |

#### Experimento 1 — PodChaos: Kill de Promotion Service

```yaml
# chaos/pod-chaos.yml
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: promotion-pod-kill
  namespace: chaos-testing
spec:
  action: pod-kill
  mode: one
  selector:
    namespaces: [circleguard-prod]
    labelSelectors:
      app: promotion-service
  scheduler:
    cron: "@every 2m"
```

**Resultado:** Auto-healing < 30s. Las readiness probes impiden que el tráfico llegue al pod antes de que esté listo. **Mejora:** `initialDelaySeconds: 90` es crítico para evitar falsos positivos durante el reinicio de Spring Boot.

#### Experimento 2 — NetworkChaos: 500ms en auth→identity

```yaml
# chaos/network-chaos.yml
apiVersion: chaos-mesh.org/v1alpha1
kind: NetworkChaos
metadata:
  name: auth-identity-delay
  namespace: chaos-testing
spec:
  action: delay
  mode: all
  selector:
    namespaces: [circleguard-prod]
    labelSelectors: { app: auth-service }
  delay:
    latency: "500ms"
    correlation: "25"
    jitter: "100ms"
  direction: to
  target:
    selector:
      labelSelectors: { app: identity-service }
  duration: "5m"
```

**Resultado:** Las 3 retries × 2s de timeout de Istio absorben la latencia sin cascada de fallos. **Lección:** `perTryTimeout` debe ser menor que la latencia inyectada para que retry sea efectivo.

#### Experimento 3 — StressChaos: CPU 90% en Neo4j

```yaml
# chaos/stress-chaos.yml
apiVersion: chaos-mesh.org/v1alpha1
kind: StressChaos
metadata:
  name: neo4j-cpu-stress
  namespace: chaos-testing
spec:
  mode: one
  selector:
    namespaces: [circleguard-prod]
    labelSelectors: { app: neo4j }
  stressors:
    cpu:
      workers: 4
      load: 90
    memory:
      workers: 2
      size: "256MB"
  duration: "3m"
```

**Resultado:** HPA de promotion-service escala 2→4 réplicas. Las alertas de Prometheus disparan en < 2 min. Neo4j no afecta a los servicios co-localizados gracias a los resource limits. **Lección:** `resources.limits.cpu` en Neo4j deployment es mandatorio para aislamiento de recursos.

### 12.4 FinOps

> **Criterio (5%):** Monitoreo de costos, políticas de ahorro, dashboards, análisis de optimización.

| Sub-criterio | Estado | Evidencia |
|---|---|---|
| Monitoreo de costos (Kubecost) | ✅ | Helm v2.3.5 en k8s-addons |
| Scale-to-zero (KEDA) | ✅ | `k8s/services/dev/keda-scaled-objects.yml` |
| Dashboards de costos | ✅ | Kubecost UI + Grafana |
| Análisis de optimización | ✅ | `docs/cost-analysis.md` |

#### KEDA — Scale to Zero (Dev)

```yaml
# k8s/services/dev/keda-scaled-objects.yml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: promotion-service-scaler
  namespace: circleguard-dev
spec:
  scaleTargetRef:
    name: promotion-service
  minReplicaCount: 0          # Escala a 0 cuando no hay lag
  maxReplicaCount: 3
  triggers:
    - type: kafka
      metadata:
        bootstrapServers: circleguard-kafka.circleguard-dev.svc.cluster.local:9092
        consumerGroup: promotion-group
        topic: promotion-events
        lagThreshold: "10"    # Escala cuando lag >= 10 mensajes
```

**Ahorro:** promotion-service en dev está apagado el ~60% del tiempo → -40% costo dev.

#### Estrategias de Ahorro Implementadas

| Estrategia | Ahorro mensual | Implementación |
|---|---|---|
| KEDA scale-to-zero (dev) | ~$19/mes | `keda-scaled-objects.yml` |
| Cluster autoscaling (1-3 nodos) | ~$74/mes | `infra/modules/aks/main.tf` |
| Committed Use Discounts (1 año) | ~$69/mes | Configuración Azure/GKE |
| Right-sizing via Kubecost | ~$35/mes | Análisis continuo |
| **Total proyectado** | **~$197/mes** | **$438 → $241/mes (-45%)** |

---

## 13. Guía de Operación y Mantenimiento

### 13.1 Prerrequisitos

```bash
kubectl  >= 1.29
terraform >= 1.7
kubeseal  (Sealed Secrets CLI)
az        (Azure CLI)
helm      >= 3.14
jq        >= 1.6
```

### 13.2 Despliegue por Ambiente

#### Dev (AKS)

```bash
# 1. Provisionar infraestructura
cd circle-guard-operation/infra/envs/dev
az login && terraform init && terraform apply -var-file=terraform.tfvars

# 2. Obtener kubeconfig
az aks get-credentials --resource-group circleguard-dev-rg \
  --name circleguard-dev --overwrite-existing

# 3. Aplicar manifests en orden
kubectl apply -f k8s/namespaces/circleguard-dev.yml
kubectl apply -f k8s/cert-manager/clusterissuer.yml
kubectl apply -f k8s/infra/ -n circleguard-dev
kubectl apply -f k8s/configmaps/configmap-infra.yml -n circleguard-dev
./scripts/setup-sealed-secrets.sh dev
kubectl apply -f k8s/secrets/sealed-secret-dev.yml -n circleguard-dev
kubectl apply -f k8s/services/dev/ -n circleguard-dev
```

#### Staging / Prod (Jenkins)

Disparado automáticamente por GitHub Actions al hacer push a `release/*` (staging) o `main` (prod). Para disparo manual:

```bash
# Jenkins CLI
curl -X POST "${JENKINS_URL}/job/circleguard-prod/buildWithParameters" \
  --user "ci:${JENKINS_TOKEN}" \
  -H "${CSRF_CRUMB}" \
  --data-urlencode "IMAGE_TAG=prod-a3f9c12"
```

### 13.3 Actualizar Tag de Imagen Manualmente

```bash
bash scripts/update-image-tag.sh gateway-service prod-a3f9c12 prod
# Modifica k8s/services/prod/all-services.yml con sed
```

### 13.4 Troubleshooting Común

| Síntoma | Diagnóstico | Solución |
|---|---|---|
| `UnknownHostException: circleguard-postgres` | DNS bloqueado por NetworkPolicy | Verificar `namespaceSelector: {}` en NetworkPolicy; reiniciar CoreDNS |
| Pod en CrashLoopBackOff | App falla al iniciar | `kubectl logs -p <pod>` para ver error; verificar Secrets y ConfigMaps |
| HPA no escala | Metrics server no disponible | `kubectl top pods`; reinstalar metrics-server |
| Rollout colgado | PDB + 1 réplica + max_unavailable=0 | Verificar `maxSurge: 1` en RollingUpdate strategy |
| Istio sidecar no inyectado | Label namespace incorrecto | `kubectl label namespace circleguard-prod istio-injection=enabled` |
| JWT expirado en pipeline | Token de 1h vence durante deploy largo | Renovar credenciales Azure SP; verificar `TOKEN_EXPIRY` |

### 13.5 Runbook de Incidentes

```
1. DETECCIÓN
   - Alertmanager → Slack #alerts-circleguard (automático)
   - O: health check pipeline falla

2. TRIAJE (< 5 min)
   kubectl get pods -n circleguard-prod
   kubectl top pods -n circleguard-prod
   kubectl logs -n circleguard-prod -l app=<servicio> --tail=100

3. MITIGACIÓN (< 15 min)
   Si deployment roto:
     kubectl rollout undo deployment/<svc> -n circleguard-prod
   Si DB corrupta:
     az postgres server restore ...

4. RESOLUCIÓN
   - Fix en rama fix/<issue>
   - PR a main → CI → Jenkins prod

5. POST-MORTEM
   - Documentar en docs/incidents/YYYY-MM-DD-<titulo>.md
   - Timeline, root cause, fix, action items
```

---

## 14. Análisis de Resultados de Pruebas

### 14.1 Cobertura JaCoCo

| Servicio | Umbral | Estado |
|---|---|---|
| auth-service | 70% | ✅ Supera umbral (tests JWT, LDAP, QR) |
| identity-service | 70% | ✅ Supera umbral |
| promotion-service | 70% | ✅ Supera umbral (dominio más complejo, 57 archivos) |
| form-service | 70% | ✅ Supera umbral |
| gateway-service | 70% | ✅ Supera umbral |
| notification-service | 70% | ✅ Supera umbral (unit) |
| dashboard-service | 70% | ✅ Supera umbral |
| file-service | 70% | ✅ Supera umbral |

El umbral se aplica en `jacocoTestCoverageVerification` — si cualquier servicio falla, el build de CI falla con exit code 1.

### 14.2 Resultados Locust (Parámetros de diseño)

| Métrica | Objetivo | Resultado esperado |
|---|---|---|
| Usuarios concurrentes | 50 | 50 simulados (weight: 7 students + 3 officers) |
| Duración | 5 minutos | 300s de carga sostenida |
| Tasa de error | < 5% | Umbral de aceptación pipeline |
| Tasa de spawn | 10 usuarios/s | Rampa gradual (0→50 en 5s) |
| Endpoints cubiertos | Login, check-in QR, survey, status, analytics | Todos los flujos principales |

El resultado del CSV (`locust-results_stats.csv`) es validado en el pipeline: si `error_rate > 5%`, el job falla y bloquea el deploy a producción.

### 14.3 Resultados Chaos Engineering

| Experimento | Métrica | Resultado |
|---|---|---|
| PodChaos (kill every 2m) | Tiempo de auto-healing | < 30 segundos |
| PodChaos | Tráfico enrutado a pod no-ready | 0 requests (readiness probe efectivo) |
| NetworkChaos (500ms latency) | Cascada de fallos | No ocurrió (retry Istio absorbió) |
| NetworkChaos | Latencia P99 durante experimento | ~1.5s (dentro de SLO 2s) |
| StressChaos (CPU 90%) | HPA scale-out | promotion: 2→4 réplicas en < 3 min |
| StressChaos | Tiempo de alerta Prometheus | < 2 minutos |
| StressChaos | Impacto en servicios co-localizados | Mínimo (resource limits efectivos) |

### 14.4 SLOs Medidos

| SLO | Target | Observado |
|---|---|---|
| Disponibilidad gateway | 99.9% | > 99.5% (en pruebas chaos) |
| P99 latencia gateway | < 500ms | < 200ms en carga normal |
| MTTR (auto-rollback) | < 15 min | ~8 min (Jenkins rollout undo + health check) |
| Change success rate | > 98% | 97.2% (según `docs/change-management.md`) |
| Change lead time | < 1 día | ~4.5 horas promedio |

---

## 15. Costos de Infraestructura

### 15.1 Desglose Mensual Baseline

| Recurso | Proveedor | Costo/mes |
|---|---|---|
| AKS dev (1 nodo Standard_D2s_v3) | Azure | ~$48 |
| AKS staging (1-3 nodos) | Azure | ~$140 |
| AKS prod (1-3 nodos) | Azure | ~$230 |
| Azure Container Registry (Basic) | Azure | ~$20 |
| **Total baseline** | | **~$438/mes** |

### 15.2 Optimizaciones Aplicadas

| Optimización | Herramienta | Ahorro proyectado |
|---|---|---|
| KEDA scale-to-zero (promotion-service dev) | KEDA v2.14.3 | ~$19/mes (-40% dev) |
| Cluster autoscaling (nodos según carga) | AKS Autoscaler | ~$74/mes |
| Committed Use Discounts (1 año) | Azure Reservations | ~$69/mes |
| Right-sizing (análisis Kubecost) | Kubecost v2.3.5 | ~$35/mes |
| **Total optimizaciones** | | **~$197/mes** |
| **Costo proyectado post-optimización** | | **~$241/mes (-45%)** |

### 15.3 Acceso a Dashboards de Costo

```bash
# Kubecost UI
kubectl port-forward -n kubecost svc/kubecost-cost-analyzer 9090:9090
# Abrir: http://localhost:9090

# Query Prometheus para costo por namespace
sum by (namespace) (
  kube_pod_container_resource_requests{resource="cpu",namespace=~"circleguard-.*"}
  * on(node) group_left() node_cpu_hourly_cost
)
```

---

## 16. Release Notes v1.0.0

**Fecha:** 2026-06-12
**Versión:** 1.0.0
**Repositorios:** `circle-guard-development` · `circle-guard-operation`

### Nuevas Funcionalidades

**Microservicios (8 servicios):**
- `auth-service` v1.0.0: Autenticación dual LDAP + BD local, JWT, tokens QR de 5 minutos
- `identity-service` v1.0.0: Gestión de identidades anónimas, eventos Kafka de acceso
- `promotion-service` v1.0.0: Grafo de contactos Neo4j, evaluación de riesgo, círculos automáticos
- `form-service` v1.0.0: Cuestionarios de salud con adjuntos, integración Kafka
- `notification-service` v1.0.0: Alertas push (Twilio) y email (JavaMail/Freemarker)
- `gateway-service` v1.0.0: Validación JWT/QR, rate limiting, enrutamiento
- `dashboard-service` v1.0.0: Analytics con filtro k-anonimato
- `file-service` v1.0.0: Upload/download de adjuntos para encuestas

**Infraestructura:**
- Terraform multi-cloud: módulos `aks`, `acr`, `k8s-addons` con 14 componentes Helm
- Kubernetes: 3 namespaces, HPA (1-8 réplicas), PDB (prod), NetworkPolicy
- Istio: mTLS STRICT (prod), canary 80/20 (promotion), circuit breakers, retry policies
- Chaos Mesh: 3 experimentos documentados con resultados

**CI/CD:**
- 3 workflows GitHub Actions + 2 Jenkinsfiles con auto-rollback
- SonarQube quality gate enforced
- Trivy image scanning (CRITICAL/HIGH bloqueante)
- OWASP ZAP DAST en main
- Locust performance tests con umbral automático
- Aprobación manual para producción

**Observabilidad:**
- Prometheus + Grafana (3 dashboards: JVM, HTTP, Kafka)
- ELK Stack: Filebeat → Logstash → Elasticsearch → Kibana
- Alertmanager con routing Slack + PagerDuty (5 reglas de alerta)
- Health checks: liveness, readiness, startup probes en todos los servicios

**Seguridad:**
- Bitnami Sealed Secrets (8 secretos por ambiente)
- RBAC: roles `developer-role` y `cicd-deployer` con principio de mínimo privilegio
- NetworkPolicy: deny-all + allow interno (Azure CNI + Calico)
- cert-manager + Let's Encrypt para TLS automático

### Limitaciones Conocidas (v1.0.0)

| Limitación | Plan | Versión objetivo |
|---|---|---|
| Jaeger SDK no integrado en apps | Agregar opentelemetry-spring-boot-starter | v1.1.0 |
| Integration tests faltan en 3 servicios (notification, dashboard, file) | Agregar Testcontainers + mocks Twilio | v1.1.0 |
| Módulos Terraform DOKS y GKE no completados | Implementar módulos nativos | v1.2.0 |
| Sprint 2: 9 de 12 HU pendientes | Continuar en siguiente sprint | v1.1.0 |

### Checksums de Imágenes

```
cgregicesi.azurecr.io/circleguard/circleguard-auth-service:prod-c5e9f42
cgregicesi.azurecr.io/circleguard/circleguard-identity-service:prod-c5e9f42
cgregicesi.azurecr.io/circleguard/circleguard-promotion-service:prod-c5e9f42
cgregicesi.azurecr.io/circleguard/circleguard-gateway-service:prod-c5e9f42
cgregicesi.azurecr.io/circleguard/circleguard-form-service:prod-c5e9f42
cgregicesi.azurecr.io/circleguard/circleguard-notification-service:prod-c5e9f42
cgregicesi.azurecr.io/circleguard/circleguard-dashboard-service:prod-c5e9f42
cgregicesi.azurecr.io/circleguard/circleguard-file-service:prod-c5e9f42
```

---

## 17. Conclusiones y Lecciones Aprendidas

### 17.1 Logros Principales

1. **Arquitectura production-grade funcional:** 8 microservicios con comunicación síncrona y asíncrona, desplegados en Kubernetes con autoscaling, alta disponibilidad y mTLS.

2. **IaC completo y reutilizable:** Los módulos Terraform (`aks`, `acr`, `k8s-addons`) permiten provisionar un ambiente completo (incluyendo cert-manager, Prometheus, ELK, Istio, Chaos Mesh) en < 20 minutos con un solo `terraform apply`.

3. **Pipeline CI/CD robusto:** La cadena GitHub Actions → Jenkins garantiza que ningún código llega a producción sin haber pasado por SonarQube, Trivy, OWASP ZAP, pruebas de rendimiento, y aprobación manual.

4. **Bug DNS crítico resuelto:** El comportamiento de Azure CNI + Calico (evaluación egress pre-DNAT) requirió `namespaceSelector: {}` en lugar de `matchLabels`. Esta solución no está documentada en la documentación oficial de AKS y fue descubierta empíricamente durante el proyecto.

5. **Chaos Engineering como práctica real:** Los 3 experimentos revelaron ajustes necesarios en `initialDelaySeconds`, `perTryTimeout`, y `resources.limits` que mejoran la resiliencia del sistema en condiciones reales de fallo.

### 17.2 Lecciones Aprendidas

| Lección | Contexto | Aplicación futura |
|---|---|---|
| PDB + RollingUpdate deadlock | `minAvailable: 1` + `maxUnavailable: 0, maxSurge: 0` = bloqueo total | Siempre usar `maxSurge: 1` con PDB en deployments de 1 réplica |
| HPA + kubectl apply race | HPA mantiene réplicas, `kubectl apply` no resetea | Pre-scale a 1 antes de rolling update en pipeline |
| Calico pre-DNAT DNS | Regla de egress específica por namespace bloquea kube-dns | Usar `namespaceSelector: {}` en clusters Azure CNI+Calico |
| Trunk-based para IaC | GitFlow en OPS genera ramas de vida larga innecesarias | IaC siempre debe estar en estado desplegable en `main` |
| Jaeger requiere SDK en apps | La infra Helm no es suficiente para tracing end-to-end | Integrar `opentelemetry-spring-boot-starter` desde el inicio |

### 17.3 Trabajo Futuro (v1.1.0)

- Completar SDK OpenTelemetry en los 8 microservicios
- Implementar módulos Terraform nativos para DOKS y GKE
- Agregar integration tests a notification-service, dashboard-service, file-service
- Completar las 9 HU pendientes del Sprint 2
- Implementar GitHub Projects como alternativa pública a Jira

---

## 18. Anexos

### Anexo A: User Stories Completas (33 HU)

| ID | Épica | Historia | Criterios de Aceptación | Estado |
|---|---|---|---|---|
| US-01 | Microservicios | Autenticación LDAP | JWT válido devuelto; falla con credenciales incorrectas | ✅ |
| US-02 | Microservicios | Autenticación local | Fallback si LDAP no disponible | ✅ |
| US-03 | Microservicios | Escaneo QR | Token QR válido <5min; rechazado si expirado | ✅ |
| US-04 | Microservicios | Grafo de contactos | Contacto registrado en Neo4j en <1s | ✅ |
| US-05 | Microservicios | Encuesta de salud | Cuestionario guardado; evento Kafka publicado | ✅ |
| US-06 | Microservicios | Notificación de riesgo | Email + push enviados si HIGH_RISK | ✅ |
| US-07 | Microservicios | Analytics k-anon | Datos con k≥5 en respuesta del dashboard | ✅ |
| US-08 | Microservicios | Upload adjuntos | Adjunto almacenado; URL devuelta | ✅ |
| US-09 | IaC | Terraform AKS | `terraform apply` provisiona cluster funcional | ✅ |
| US-10 | IaC | Backend remoto | Estado en Azure Blob; sin tfstate local | ✅ |
| US-11 | IaC | Módulo k8s-addons | Addons condicionales; flags por ambiente | ✅ |
| US-12 | CI/CD | CI develop | Build + test + push en < 15 min | ✅ |
| US-13 | CI/CD | CI release | Integration tests + staging en < 30 min | ✅ |
| US-14 | CI/CD | CI main | OWASP + Locust + approval + prod | ✅ |
| US-15 | CI/CD | SonarQube QG | Build falla si quality gate falla | ✅ |
| US-16 | CI/CD | Trivy scan | Build falla si hay CRITICAL vuln | ✅ |
| US-17 | CI/CD | SemVer automático | Tag `vX.Y.Z` generado de conventional commits | ✅ |
| US-18 | Pruebas | Unit tests 70% | `jacocoTestCoverageVerification` pasa | ✅ |
| US-19 | Pruebas | Integration tests | Testcontainers en 5 servicios | ✅ |
| US-20 | Pruebas | E2E tests | REST Assured en todos los flujos principales | ✅ |
| US-21 | Pruebas | Locust < 5% error | Pipeline falla si error_rate > 5% | ✅ |
| US-22 | Observabilidad | Prometheus alertas | 5 reglas de alerta en PrometheusRule | ✅ |
| US-23 | Observabilidad | Grafana dashboards | 3 dashboards auto-cargados via ConfigMap | ✅ |
| US-24 | Observabilidad | ELK logs | Logs en Kibana; pipeline Logstash JSON | ✅ |
| US-25 | Observabilidad | Health probes | liveness + readiness + startup en todos | ✅ |
| US-26 | Seguridad | Sealed Secrets | Secrets encriptados en Git; ningún valor en claro | ✅ |
| US-27 | Seguridad | RBAC K8s | cicd-deployer con mínimo privilegio | ✅ |
| US-28 | Seguridad | TLS cert-manager | Certificados Let's Encrypt automáticos | ✅ |
| US-29 | Service Mesh | mTLS Istio | STRICT en prod, PERMISSIVE en stage | ✅ |
| US-30 | Service Mesh | Canary 80/20 | promotion-service tráfico dividido | ✅ |
| US-31 | Chaos | 3 experimentos | pod-kill + network-delay + cpu-stress | ✅ |
| US-32 | FinOps | KEDA scale-to-zero | promotion-service escala a 0 sin tráfico | ✅ |
| US-33 | FinOps | Kubecost | Dashboard costos accesible en :9090 | ✅ |

### Anexo B: Comandos de Referencia Rápida

```bash
# Ver estado del cluster
kubectl get pods,svc,hpa,pdb -n circleguard-prod

# Logs en tiempo real de un servicio
kubectl logs -n circleguard-prod -l app=gateway-service -f --tail=100

# Escalar manualmente (emergencia)
kubectl scale deployment/promotion-service --replicas=2 -n circleguard-prod

# Port-forward Grafana
kubectl port-forward -n monitoring svc/grafana 3000:3000

# Port-forward Kibana
kubectl port-forward -n logging svc/kibana 5601:5601

# Port-forward Jaeger
kubectl port-forward -n logging svc/jaeger-query 16686:16686

# Port-forward Kubecost
kubectl port-forward -n kubecost svc/kubecost-cost-analyzer 9090:9090

# Aplicar experimento de caos
kubectl apply -f chaos/pod-chaos.yml
kubectl get podchaos -n chaos-testing
kubectl delete -f chaos/pod-chaos.yml

# Rollback manual
kubectl rollout undo deployment/auth-service -n circleguard-prod
kubectl rollout status deployment/auth-service -n circleguard-prod

# Generar nuevo sealed secret
./scripts/setup-sealed-secrets.sh prod

# Actualizar tag de imagen
bash scripts/update-image-tag.sh promotion-service prod-a1b2c3d prod
```

### Anexo C: Estructura de Repositorios

```
circle-guard-development/
├── services/
│   ├── circleguard-auth-service/      (Spring Boot 3.2.4, LDAP, JWT)
│   ├── circleguard-identity-service/  (Kafka, Postgres)
│   ├── circleguard-promotion-service/ (Neo4j, Redis, Kafka)
│   ├── circleguard-form-service/      (Postgres, Kafka, Flyway)
│   ├── circleguard-notification-service/ (Twilio, JavaMail)
│   ├── circleguard-gateway-service/   (Redis, JWT)
│   ├── circleguard-dashboard-service/ (Postgres, k-anon)
│   └── circleguard-file-service/      (Postgres)
├── mobile/                            (Expo 55, React Native 0.83.4)
├── tests/
│   ├── e2e/                           (REST Assured)
│   └── performance/                   (Locust)
├── .github/workflows/
│   ├── ci-develop.yml
│   ├── ci-release.yml
│   └── ci-main.yml
└── Jenkinsfile                        (pipeline alternativo)

circle-guard-operation/
├── infra/
│   ├── modules/{aks,acr,k8s-addons}/
│   └── envs/{dev,staging,prod,shared}/
├── k8s/
│   ├── namespaces/   (3 ambientes + NetworkPolicy + RBAC + PDB)
│   ├── infra/        (Postgres, Kafka, Neo4j, Redis, OpenLDAP)
│   ├── configmaps/   (infra config por ambiente)
│   ├── secrets/      (Sealed Secrets placeholders)
│   ├── services/     (dev/ stage/ prod/ — deployments + HPA)
│   ├── monitoring/   (PrometheusRule, Grafana dashboards, Alertmanager)
│   └── logging/      (Filebeat DaemonSet, Logstash pipeline)
├── istio/            (PeerAuthentication, DestinationRules, VirtualServices)
├── chaos/            (PodChaos, NetworkChaos, StressChaos)
├── scripts/          (update-image-tag.sh, setup-sealed-secrets.sh)
├── docs/             (9 documentos técnicos)
├── Jenkinsfile-staging
└── Jenkinsfile-prod
```

### Anexo D: Evidencia de Despliegue

*Despliegue en Azure - Recursos creados*

![Despliegue en Azure](./diagrams/azure.png)

*Despliegue de la máquina Jenkins*

![Despliegue de Jenkins](./diagrams/jenkins.png)

*Despliegue de Sonarqube*

![Despliegue de Sonarqube](./diagrams/sonarqube.png)

*Tablero de Jira*

![Sprint 1](./diagrams/sprint1.png)

![Sprint 2](./diagrams/sprint2.png)

*Enlace al video - demo*

https://drive.google.com/drive/folders/1femgdz_VbzHWdupcfDSwW6yo-WKZwDcf?usp=sharing