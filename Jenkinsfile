/*
 * IMPORTANTE: Este Jenkinsfile es un pipeline ALTERNATIVO a los workflows de GitHub Actions.
 * NO ejecutar este pipeline en paralelo con .github/workflows/ci-develop.yml,
 * ci-release.yml o ci-main.yml sobre el mismo ambiente, ya que puede causar
 * conflictos de deploy y condiciones de carrera en los clusters.
 * Uso recomendado: onboarding, pruebas locales o cuando GitHub Actions no esté disponible.
 */

pipeline {
    agent any

    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        ACR_REGISTRY         = 'cgregistry.azurecr.io'
        ACR_REPO             = 'circleguard'
        SERVICES             = 'auth-service identity-service promotion-service notification-service form-service gateway-service dashboard-service file-service'
        AKS_RG_STAGING       = 'circleguard-stage-rg'
        AKS_CLUSTER_STAGING  = 'circleguard-stage'
        AKS_RG_PROD          = 'circleguard-prod-rg'
        AKS_CLUSTER_PROD     = 'circleguard-prod'
    }

    stages {

        // ════════════════════════════════════════════════════════════════
        // CHECKOUT
        // ════════════════════════════════════════════════════════════════

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // ════════════════════════════════════════════════════════════════
        // BUILD — parallel por servicio (del monorepo ci/stage/Jenkinsfile)
        // ════════════════════════════════════════════════════════════════

        stage('Build') {
            parallel {
                stage('auth-service') {
                    steps { sh './gradlew :services:circleguard-auth-service:bootJar -x test --no-daemon' }
                }
                stage('identity-service') {
                    steps { sh './gradlew :services:circleguard-identity-service:bootJar -x test --no-daemon' }
                }
                stage('promotion-service') {
                    steps { sh './gradlew :services:circleguard-promotion-service:bootJar -x test --no-daemon' }
                }
                stage('notification-service') {
                    steps { sh './gradlew :services:circleguard-notification-service:bootJar -x test --no-daemon' }
                }
                stage('form-service') {
                    steps { sh './gradlew :services:circleguard-form-service:bootJar -x test --no-daemon' }
                }
                stage('gateway-service') {
                    steps { sh './gradlew :services:circleguard-gateway-service:bootJar -x test --no-daemon' }
                }
                stage('dashboard-service') {
                    steps { sh './gradlew :services:circleguard-dashboard-service:bootJar -x test --no-daemon' }
                }
                stage('file-service') {
                    steps { sh './gradlew :services:circleguard-file-service:bootJar -x test --no-daemon' }
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        // UNIT TESTS — parallel por servicio + JaCoCo
        // ════════════════════════════════════════════════════════════════

        stage('Unit Tests') {
            parallel {
                stage('test-auth') {
                    steps { sh './gradlew :services:circleguard-auth-service:unitTest jacocoTestReport --no-daemon' }
                }
                stage('test-identity') {
                    steps { sh './gradlew :services:circleguard-identity-service:unitTest jacocoTestReport --no-daemon' }
                }
                stage('test-promotion') {
                    steps { sh './gradlew :services:circleguard-promotion-service:unitTest jacocoTestReport --no-daemon' }
                }
                stage('test-notification') {
                    steps { sh './gradlew :services:circleguard-notification-service:unitTest jacocoTestReport --no-daemon' }
                }
                stage('test-form') {
                    steps { sh './gradlew :services:circleguard-form-service:unitTest jacocoTestReport --no-daemon' }
                }
                stage('test-gateway') {
                    steps { sh './gradlew :services:circleguard-gateway-service:unitTest jacocoTestReport --no-daemon' }
                }
                stage('test-dashboard') {
                    steps { sh './gradlew :services:circleguard-dashboard-service:unitTest jacocoTestReport --no-daemon' }
                }
                stage('test-file') {
                    steps { sh './gradlew :services:circleguard-file-service:unitTest jacocoTestReport --no-daemon' }
                }
            }
            post {
                always {
                    junit '**/build/test-results/unitTest/*.xml'
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        // SONARQUBE — develop, release/*, main
        // ════════════════════════════════════════════════════════════════

        stage('SonarQube Analysis') {
            when {
                anyOf {
                    branch 'develop'
                    branch pattern: 'release/.*', comparator: 'REGEXP'
                    branch 'main'
                    branch 'master'
                }
            }
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh './gradlew sonarqube --no-daemon'
                }
            }
        }

        stage('Quality Gate') {
            when {
                anyOf {
                    branch 'develop'
                    branch pattern: 'release/.*', comparator: 'REGEXP'
                    branch 'main'
                    branch 'master'
                }
            }
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        // INTEGRATION TESTS — release/*, main/master
        // ════════════════════════════════════════════════════════════════

        stage('Integration Tests') {
            when {
                anyOf {
                    branch pattern: 'release/.*', comparator: 'REGEXP'
                    branch 'main'
                    branch 'master'
                }
            }
            steps {
                sh 'docker compose -f docker-compose.test.yml up -d --wait'
                sh './gradlew integrationTest --no-daemon'
            }
            post {
                always {
                    junit '**/build/test-results/integrationTest/*.xml'
                    sh 'docker compose -f docker-compose.test.yml down -v'
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        // DETERMINE IMAGE TAG — según branch
        // ════════════════════════════════════════════════════════════════

        stage('Determine Image Tag') {
            steps {
                script {
                    env.SHORT_SHA = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()

                    if (env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'master') {
                        env.IMAGE_TAG  = "prod-${env.SHORT_SHA}"
                        env.TAG_LATEST = 'true'
                    } else if (env.BRANCH_NAME =~ /^release\/.*/) {
                        env.IMAGE_TAG  = "staging-${env.SHORT_SHA}"
                        env.TAG_LATEST = 'false'
                    } else {
                        env.IMAGE_TAG  = "dev-${env.SHORT_SHA}"
                        env.TAG_LATEST = 'false'
                    }
                    echo "==> Image tag: ${env.IMAGE_TAG} | Branch: ${env.BRANCH_NAME}"
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        // DOCKER BUILD + TRIVY SCAN
        // ════════════════════════════════════════════════════════════════

        stage('Docker Build & Trivy Scan') {
            steps {
                script {
                    def servicesList = env.SERVICES.tokenize(' ')
                    for (int i = 0; i < servicesList.size(); i++) {
                        def svc = servicesList[i]
                        def jarPath = sh(
                            script: "find services/circleguard-${svc}/build/libs -name '*.jar' ! -name '*-plain.jar' | head -1",
                            returnStdout: true
                        ).trim()
                        if (!jarPath) {
                            error("No JAR encontrado para circleguard-${svc}")
                        }
                        sh """
                            docker build --build-arg JAR_FILE=${jarPath} \\
                              -t ${ACR_REGISTRY}/${ACR_REPO}/circleguard-${svc}:${env.IMAGE_TAG} \\
                              services/circleguard-${svc}/
                        """
                        def trivyExit = sh(
                            script: "trivy image --exit-code 1 --severity CRITICAL --no-progress ${ACR_REGISTRY}/${ACR_REPO}/circleguard-${svc}:${env.IMAGE_TAG}",
                            returnStatus: true
                        )
                        if (trivyExit != 0) {
                            error("Trivy encontró vulnerabilidades CRÍTICAS en circleguard-${svc}")
                        }
                    }
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        // PUSH TO ACR
        // ════════════════════════════════════════════════════════════════

        stage('Push to ACR') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'acr-credentials',
                    usernameVariable: 'ACR_USER',
                    passwordVariable: 'ACR_PASS'
                )]) {
                    script {
                        sh "docker login ${ACR_REGISTRY} -u ${ACR_USER} -p ${ACR_PASS}"
                        def servicesList = env.SERVICES.tokenize(' ')
                        for (int i = 0; i < servicesList.size(); i++) {
                            def svc = servicesList[i]
                            sh "docker push ${ACR_REGISTRY}/${ACR_REPO}/circleguard-${svc}:${env.IMAGE_TAG}"
                            if (env.TAG_LATEST == 'true') {
                                sh """
                                    docker tag ${ACR_REGISTRY}/${ACR_REPO}/circleguard-${svc}:${env.IMAGE_TAG} \\
                                               ${ACR_REGISTRY}/${ACR_REPO}/circleguard-${svc}:latest
                                    docker push ${ACR_REGISTRY}/${ACR_REPO}/circleguard-${svc}:latest
                                """
                            }
                        }
                    }
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        // TRIGGER CD STAGING — release/*
        // Dispara el job circleguard-staging en este mismo Jenkins
        // ════════════════════════════════════════════════════════════════

        stage('Trigger OPS — STAGING') {
            when { branch pattern: 'release/.*', comparator: 'REGEXP' }
            steps {
                build job: 'circleguard-staging',
                      parameters: [
                          string(name: 'IMAGE_TAG',   value: env.IMAGE_TAG),
                          string(name: 'ENVIRONMENT', value: 'staging')
                      ],
                      wait: true
            }
        }

        // ════════════════════════════════════════════════════════════════
        // E2E TESTS — release/* (staging debe estar desplegado)
        // Stability check + port-forward + ./gradlew e2eTest
        // ════════════════════════════════════════════════════════════════

        stage('E2E Tests') {
            when { branch pattern: 'release/.*', comparator: 'REGEXP' }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'azure-sp-credentials',
                    usernameVariable: 'AZ_CLIENT_ID',
                    passwordVariable: 'AZ_CLIENT_SECRET'
                )]) {
                    sh """
                        az login --service-principal \
                          -u \$AZ_CLIENT_ID -p \$AZ_CLIENT_SECRET \
                          --tenant \$(az account show --query tenantId -o tsv 2>/dev/null || echo \$AZ_TENANT_ID)
                        az aks get-credentials \
                          --resource-group \${AKS_RG_STAGING} \
                          --name \${AKS_CLUSTER_STAGING} \
                          --overwrite-existing

                        echo "==> Esperando estabilidad de servicios E2E en circleguard-stage (max 25 min)..."
                        STABLE=0
                        for i in \$(seq 1 50); do
                            AUTH_READY=\$(kubectl get deployment auth-service       -n circleguard-stage -o jsonpath='{.status.availableReplicas}' 2>/dev/null || echo "0")
                            GW_READY=\$(kubectl get deployment gateway-service      -n circleguard-stage -o jsonpath='{.status.availableReplicas}' 2>/dev/null || echo "0")
                            FORM_READY=\$(kubectl get deployment form-service       -n circleguard-stage -o jsonpath='{.status.availableReplicas}' 2>/dev/null || echo "0")
                            PROM_READY=\$(kubectl get deployment promotion-service  -n circleguard-stage -o jsonpath='{.status.availableReplicas}' 2>/dev/null || echo "0")
                            if [ "\${AUTH_READY:-0}" -ge 1 ] && [ "\${GW_READY:-0}" -ge 1 ] && [ "\${FORM_READY:-0}" -ge 1 ] && [ "\${PROM_READY:-0}" -ge 1 ]; then
                                STABLE=\$((STABLE + 1))
                                echo "==> Intento \$i: disponibles (auth=\${AUTH_READY}, gw=\${GW_READY}, form=\${FORM_READY}, prom=\${PROM_READY}) — estabilidad \${STABLE}/3"
                                if [ \$STABLE -ge 3 ]; then
                                    echo "==> Servicios estables. Iniciando E2E..."
                                    break
                                fi
                                sleep 20
                            else
                                STABLE=0
                                echo "==> Intento \$i/50 (auth=\${AUTH_READY:-0}, gw=\${GW_READY:-0}, form=\${FORM_READY:-0}, prom=\${PROM_READY:-0}) — esperando 30s..."
                                sleep 30
                            fi
                        done

                        kubectl port-forward svc/auth-service      8180:8180 -n circleguard-stage &
                        kubectl port-forward svc/gateway-service   8087:8087 -n circleguard-stage &
                        kubectl port-forward svc/promotion-service 8088:8088 -n circleguard-stage &
                        kubectl port-forward svc/form-service      8086:8086 -n circleguard-stage &
                        sleep 90

                        ./gradlew e2eTest \\
                          -De2e.auth.url=http://localhost:8180 \\
                          -De2e.gateway.url=http://localhost:8087 \\
                          -De2e.promotion.url=http://localhost:8088 \\
                          -De2e.form.url=http://localhost:8086 \\
                          --no-daemon

                        kill %1 %2 %3 %4 2>/dev/null || true
                    """
                }
            }
            post {
                always {
                    junit 'tests/e2e/build/test-results/test/*.xml'
                    publishHTML(target: [
                        reportDir: 'tests/e2e/build/reports/tests/test',
                        reportFiles: 'index.html',
                        reportName: 'E2E Test Report',
                        keepAll: true
                    ])
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        // PERFORMANCE TESTS — main/master
        // AKS port-forward + run_locust.sh + publishHTML
        // ════════════════════════════════════════════════════════════════

        stage('Performance Tests') {
            when {
                anyOf { branch 'main'; branch 'master' }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'azure-sp-credentials',
                    usernameVariable: 'AZ_CLIENT_ID',
                    passwordVariable: 'AZ_CLIENT_SECRET'
                )]) {
                    sh """
                        az login --service-principal \
                          -u \$AZ_CLIENT_ID -p \$AZ_CLIENT_SECRET \
                          --tenant \$(az account show --query tenantId -o tsv 2>/dev/null || echo \$AZ_TENANT_ID)
                        az aks get-credentials \
                          --resource-group \${AKS_RG_STAGING} \
                          --name \${AKS_CLUSTER_STAGING} \
                          --overwrite-existing

                        kubectl port-forward svc/auth-service      8180:8180 -n circleguard-stage &
                        kubectl port-forward svc/gateway-service   8087:8087 -n circleguard-stage &
                        kubectl port-forward svc/promotion-service 8088:8088 -n circleguard-stage &
                        kubectl port-forward svc/form-service      8086:8086 -n circleguard-stage &
                        sleep 15

                        export AUTH_URL=http://localhost:8180 \\
                               GATEWAY_URL=http://localhost:8087 \\
                               PROMOTION_URL=http://localhost:8088 \\
                               FORM_URL=http://localhost:8086

                        bash tests/performance/run_locust.sh

                        kill %1 %2 %3 %4 2>/dev/null || true
                    """
                }
            }
            post {
                always {
                    publishHTML(target: [
                        reportDir: 'tests/performance/reports',
                        reportFiles: '*.html',
                        reportName: 'Locust Performance Report',
                        keepAll: true
                    ])
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        // RELEASE NOTES — main/master
        // ════════════════════════════════════════════════════════════════

        stage('Release Notes') {
            when {
                anyOf { branch 'main'; branch 'master' }
            }
            steps {
                script {
                    def lastTag = sh(
                        script: "git describe --tags --abbrev=0 2>/dev/null || echo ''",
                        returnStdout: true
                    ).trim()
                    def commits = sh(
                        script: lastTag
                            ? "git log ${lastTag}..HEAD --pretty=format:'- %s (%an)'"
                            : "git log --pretty=format:'- %s (%an)' -20",
                        returnStdout: true
                    ).trim()
                    def servicesList = env.SERVICES.split(' ')
                    def imageLines = servicesList.collect { svc ->
                        "- ${ACR_REGISTRY}/${ACR_REPO}/circleguard-${svc}:${env.IMAGE_TAG}"
                    }.join('\n')

                    def notes = """\
## Release v${BUILD_NUMBER} — ${new Date().format('yyyy-MM-dd')}

### Cambios incluidos
${commits ?: '(sin commits nuevos desde el último tag)'}

### Imágenes desplegadas
${imageLines}

### Métricas
- Build: v${BUILD_NUMBER}
- Commit: ${env.SHORT_SHA}
- Performance report: ver artefacto Locust adjunto
- Build URL: ${env.BUILD_URL}
"""
                    writeFile file: 'RELEASE_NOTES.md', text: notes
                    archiveArtifacts artifacts: 'RELEASE_NOTES.md'
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        // APROBACIÓN MANUAL — main/master
        // ════════════════════════════════════════════════════════════════

        stage('Aprobación Manual — PRODUCCIÓN') {
            when {
                anyOf { branch 'main'; branch 'master' }
            }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    input(
                        message: "¿Desplegar v${BUILD_NUMBER} (${env.SHORT_SHA}) a PRODUCCIÓN?",
                        ok: 'Aprobar despliegue',
                        submitterParameter: 'APPROVER'
                    )
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        // TRIGGER CD PROD — main/master
        // Dispara el job circleguard-prod en este mismo Jenkins
        // ════════════════════════════════════════════════════════════════

        stage('Trigger OPS — PROD') {
            when {
                anyOf { branch 'main'; branch 'master' }
            }
            steps {
                build job: 'circleguard-prod',
                      parameters: [
                          string(name: 'IMAGE_TAG',   value: env.IMAGE_TAG),
                          string(name: 'ENVIRONMENT', value: 'production')
                      ],
                      wait: true
            }
        }

        // ════════════════════════════════════════════════════════════════
        // TAG RELEASE — main/master
        // ════════════════════════════════════════════════════════════════

        stage('Tag Release') {
            when {
                anyOf { branch 'main'; branch 'master' }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'token-circle-guard-ingesoft-v',
                    usernameVariable: 'GIT_USER',
                    passwordVariable: 'GIT_PASS'
                )]) {
                    sh """
                        git config user.email "jenkins@circleguard.edu"
                        git config user.name "Jenkins CI"
                        git tag v${BUILD_NUMBER}
                        git push https://\${GIT_USER}:\${GIT_PASS}@github.com/\${GIT_USER}/circle-guard-development.git \\
                          v${BUILD_NUMBER}
                        echo "==> Tag v${BUILD_NUMBER} publicado exitosamente"
                    """
                }
            }
        }

    }

    post {
        success {
            echo "==> Pipeline exitoso — Imagen: ${ACR_REGISTRY}/${ACR_REPO}/*:${env.IMAGE_TAG ?: 'N/A'} | Build: v${BUILD_NUMBER}"
        }
        failure {
            echo "==> Pipeline FALLIDO en branch '${env.BRANCH_NAME}' — Build v${BUILD_NUMBER}"
            echo "==> Para rollback manual: kubectl rollout undo deployment/<servicio> -n circleguard-<env>"
        }
        always {
            sh 'docker system prune -f || true'
            cleanWs()
        }
    }
}
