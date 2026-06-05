pipeline {
    agent any

    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        ACR_REGISTRY      = 'cgregistry.azurecr.io'
        ACR_REPO          = 'circleguard'
        SERVICES          = 'auth-service identity-service promotion-service notification-service form-service gateway-service dashboard-service file-service'
        GKE_CLUSTER       = 'circleguard-stage'
        GKE_ZONE          = 'us-east1-b'
        PROJECT_ID        = 'ingesoft-v'
        JENKINS_OPS_URL   = 'http://jenkins-ops.circleguard.internal:8080'
        JENKINS_OPS_JOB   = 'circle-guard-operation/main'
        JENKINS_OPS_TOKEN = 'circleguard-cd-trigger'
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
        // TRIGGER OPS STAGING + WAIT RESULT — release/*
        // Espera que el OPS job inicie (4 min) y complete (45 min)
        // ════════════════════════════════════════════════════════════════

        stage('Trigger OPS — STAGING') {
            when { branch pattern: 'release/.*', comparator: 'REGEXP' }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'jenkins-ops-api-credentials',
                    usernameVariable: 'OPS_USER',
                    passwordVariable: 'OPS_PASS'
                )]) {
                    sh """
                        CRUMB=\$(curl -s -u "\${OPS_USER}:\${OPS_PASS}" \\
                            "\${JENKINS_OPS_URL}/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\\\":\\\",//crumb)")

                        echo "==> Disparando OPS pipeline para staging (IMAGE_TAG=${env.IMAGE_TAG})..."
                        QUEUE_URL=\$(curl -s -X POST \\
                            -u "\${OPS_USER}:\${OPS_PASS}" \\
                            -H "\${CRUMB}" \\
                            -D - -o /dev/null \\
                            "\${JENKINS_OPS_URL}/job/\${JENKINS_OPS_JOB}/buildWithParameters?token=\${JENKINS_OPS_TOKEN}&IMAGE_TAG=${env.IMAGE_TAG}&ENVIRONMENT=staging" \\
                            | grep -i "^location:" | awk '{print \$2}' | tr -d '\\r\\n')

                        echo "==> Queue item: \${QUEUE_URL}"

                        BUILD_URL=""
                        for i in \$(seq 1 24); do
                            ITEM_JSON=\$(curl -sf -u "\${OPS_USER}:\${OPS_PASS}" "\${QUEUE_URL}api/json" 2>/dev/null || echo "{}")
                            BUILD_URL=\$(echo "\${ITEM_JSON}" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('executable',{}).get('url',''))" 2>/dev/null || echo "")
                            if [ -n "\${BUILD_URL}" ]; then
                                echo "==> OPS build iniciado: \${BUILD_URL}"
                                break
                            fi
                            echo "==> Esperando que OPS build empiece... intento \${i}/24"
                            sleep 10
                        done

                        if [ -z "\${BUILD_URL}" ]; then
                            echo "ERROR: OPS build no inició en 4 minutos"
                            exit 1
                        fi

                        echo "==> Esperando que OPS build complete (max 45 min)..."
                        OPS_RESULT=""
                        for i in \$(seq 1 90); do
                            OPS_RESULT=\$(curl -sf -u "\${OPS_USER}:\${OPS_PASS}" "\${BUILD_URL}api/json" \\
                                | python3 -c "import sys,json; d=json.load(sys.stdin); r=d.get('result'); print(r if r else '')" 2>/dev/null || echo "")
                            if [ -n "\${OPS_RESULT}" ]; then
                                echo "==> OPS build completó: \${OPS_RESULT}"
                                break
                            fi
                            echo "==> OPS en progreso... intento \${i}/90"
                            sleep 30
                        done

                        if [ "\${OPS_RESULT}" != "SUCCESS" ]; then
                            echo "ERROR: OPS pipeline falló (resultado: \${OPS_RESULT})"
                            exit 1
                        fi
                        echo "==> OPS staging completado exitosamente."
                    """
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        // E2E TESTS — release/* (staging debe estar desplegado)
        // Stability check + port-forward + ./gradlew e2eTest
        // ════════════════════════════════════════════════════════════════

        stage('E2E Tests') {
            when { branch pattern: 'release/.*', comparator: 'REGEXP' }
            steps {
                withCredentials([file(
                    credentialsId: 'gcp-service-account-key',
                    variable: 'GCP_KEY_FILE'
                )]) {
                    sh """
                        gcloud auth activate-service-account --key-file=\$GCP_KEY_FILE
                        gcloud container clusters get-credentials \${GKE_CLUSTER} \\
                          --zone \${GKE_ZONE} --project \${PROJECT_ID}

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
        // GKE port-forward + run_locust.sh + publishHTML
        // ════════════════════════════════════════════════════════════════

        stage('Performance Tests') {
            when {
                anyOf { branch 'main'; branch 'master' }
            }
            steps {
                withCredentials([file(
                    credentialsId: 'gcp-service-account-key',
                    variable: 'GCP_KEY_FILE'
                )]) {
                    sh """
                        gcloud auth activate-service-account --key-file=\$GCP_KEY_FILE
                        gcloud container clusters get-credentials \${GKE_CLUSTER} \\
                          --zone \${GKE_ZONE} --project \${PROJECT_ID}

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
        // TRIGGER OPS PROD + WAIT — main/master
        // ════════════════════════════════════════════════════════════════

        stage('Trigger OPS — PROD') {
            when {
                anyOf { branch 'main'; branch 'master' }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'jenkins-ops-api-credentials',
                    usernameVariable: 'OPS_USER',
                    passwordVariable: 'OPS_PASS'
                )]) {
                    sh """
                        CRUMB=\$(curl -s -u "\${OPS_USER}:\${OPS_PASS}" \\
                            "\${JENKINS_OPS_URL}/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\\\":\\\",//crumb)")

                        echo "==> Disparando OPS pipeline para producción (IMAGE_TAG=${env.SHORT_SHA})..."
                        curl -f -X POST \\
                            -u "\${OPS_USER}:\${OPS_PASS}" \\
                            -H "\${CRUMB}" \\
                            "\${JENKINS_OPS_URL}/job/\${JENKINS_OPS_JOB}/buildWithParameters?token=\${JENKINS_OPS_TOKEN}&IMAGE_TAG=${env.SHORT_SHA}&ENVIRONMENT=production"

                        echo "==> OPS prod disparado. El deploy a producción continúa en Jenkinsfile-prod."
                    """
                }
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
