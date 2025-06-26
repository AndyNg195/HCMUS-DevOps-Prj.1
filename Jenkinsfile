def changeFiles = []
def changedServices = []

pipeline {
    agent any

    tools {
        maven 'M3'
        jdk 'jdk17'
    }

    stages {
        stage('Checkout & Detect Changes') {
            steps {
                checkout scm
                script {
                    // Collect changed file paths
                    for (changeLog in currentBuild.changeSets) {
                        for (entry in changeLog.items) {
                            for (file in entry.affectedFiles) {
                                changeFiles << file.path.trim()
                            }
                        }
                    }

                    echo "Changed files:\n${changeFiles.join('\n')}"

                    changedServices = getChangedServices(changeFiles)
                    echo "Changed services: ${changedServices}"

                    if (changedServices.isEmpty()) {
                        currentBuild.result = 'NOT_BUILT'
                        error "No changes detected in any services - skipping build"
                    }
                }
            }
        }

        stage('Build Changed Services') {
            when {
                expression { return !isFullBuild(changedServices) && !changedServices.isEmpty() }
            }
            steps {
                script {
                    changedServices.each { service ->
                        echo "Building ${service}"
                        sh "./mvnw clean package -DskipTests -pl ${service} -am"
                    }
                }
            }
        }

        stage('Build All') {
            when {
                expression { return isFullBuild(changedServices) }
            }
            steps {
                sh './mvnw clean package -DskipTests'
            }
        }

        stage('Test Changed Services') {
            when {
                expression { return !isFullBuild(changedServices) && !changedServices.isEmpty() }
            }
            steps {
                script {
                    changedServices.each { service ->
                        echo "Testing ${service}"
                        sh "./mvnw test -pl :${service}"
                        junit "**/${service}/target/surefire-reports/*.xml"
                        // jacoco execPattern: "**/${service}/target/jacoco.exec"
                    }
                }
            }
        }

        stage('Test All') {
            when {
                expression { return isFullBuild(changedServices) }
            }
            steps {
                sh './mvnw test'
                junit '**/target/surefire-reports/*.xml'
                // jacoco execPattern: '**/target/jacoco.exec'
            }
        }
    }

    post {
        always {
            script {
                if (!changedServices.isEmpty()) {
                    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true

                    def coveragePattern = isFullBuild(changedServices) ?
                        '**/target/site/jacoco/jacoco.xml' :
                        changedServices.collect {
                            "**/${it}/target/site/jacoco/jacoco.xml"
                        }.join(',')

                    recordCoverage(
                        tools: [[parser: 'JACOCO', pattern: coveragePattern]],
                        sourceFileResolver: [
                            [projectDir: "$WORKSPACE"]
                        ]
                    )

                    // publishHTML(
                    //     target: [
                    //         reportDir: 'target/site/jacoco-aggregate',
                    //         reportFiles: 'index.html',
                    //         reportName: 'JaCoCo Coverage Report',
                    //         keepAll: true
                    //     ]
                    // )
                }
            }
            // cleanWs()
        }
    }
}

// Return List<String> of changed services
def getChangedServices(List changes) {
    def serviceMap = [
        'spring-petclinic-api-gateway'      : 'spring-petclinic-api-gateway',
        'spring-petclinic-customers-service': 'spring-petclinic-customers-service',
        'spring-petclinic-vets-service'     : 'spring-petclinic-vets-service',
        'spring-petclinic-visits-service'   : 'spring-petclinic-visits-service',
        'spring-petclinic-config-server'    : 'spring-petclinic-config-server',
        'spring-petclinic-discovery-server' : 'spring-petclinic-discovery-server',
        'spring-petclinic-admin-server'     : 'spring-petclinic-admin-server'
    ]

    def services = []
    changes.each { change ->
        serviceMap.each { dir, service ->
            if (change.contains(dir) && !services.contains(service)) {
                services << service
            }
        }
    }

    if (changes.any { it.contains('pom.xml') || it.contains('Jenkinsfile') }) {
        // Special case: full build
        return ['ALL']
    }

    return services
}

// Helper function to detect full build flag
def isFullBuild(List services) {
    return services.size() == 1 && services[0] == 'ALL'
}
