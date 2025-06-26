// Global variables for changes
def changedServices = 'none'
def changeFiles = []

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
                    // Extract changed files from SCM
                    for (changeLog in currentBuild.changeSets) {
                        for (entry in changeLog.items) {
                            for (file in entry.affectedFiles) {
                                def filePath = file.path.trim()
                                changeFiles.add(filePath)
                            }
                        }
                    }

                    echo "Detected changed files:\n${changeFiles.join('\n')}"

                    // Determine which services changed
                    changedServices = getChangedServices(changeFiles)
                    echo "Detected changed services: ${changedServices}"

                    // Stop early if no changes detected
                    if (changedServices == 'none') {
                        currentBuild.result = 'NOT_BUILT'
                        error "No changes detected in any services - skipping build"
                    }
                }
            }
        }

        stage('Build Changed Services') {
            when {
                expression { return changedServices != 'all' && changedServices != 'none' }
            }
            steps {
                script {
                    def services = changedServices.split(',')
                    services.each { service ->
                        echo "Building ${service}"
                        sh "./mvnw clean package -DskipTests -pl ${service} -am"
                    }
                }
            }
        }

        stage('Build All') {
            when {
                expression { return changedServices == 'all' }
            }
            steps {
                sh './mvnw clean package -DskipTests'
            }
        }

        stage('Test Changed Services') {
            when {
                expression { return changedServices != 'all' && changedServices != 'none' }
            }
            steps {
                script {
                    def services = changedServices.split(',')
                    services.each { service ->
                        echo "Testing ${service}"
                        sh "./mvnw test -pl :${service}"
                        junit "**/${service}/target/surefire-reports/*.xml"
                        jacoco execPattern: "**/${service}/target/jacoco.exec"
                    }
                }
            }
        }

        stage('Test All') {
            when {
                expression { return changedServices == 'all' }
            }
            steps {
                sh './mvnw test'
                junit '**/target/surefire-reports/*.xml'
                jacoco execPattern: '**/target/jacoco.exec'
            }
        }
    }

    post {
        always {
            script {
                if (changedServices != 'none') {
                    // Publish test and coverage results
                    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true

                    def coveragePattern = (changedServices == 'all') ?
                        '**/target/site/jacoco/jacoco.xml' :
                        changedServices.split(',').collect {
                            "**/${it}/target/site/jacoco/jacoco.xml"
                        }.join(',')

                    recordCoverage(
                        tools: [[
                            parser: 'JACOCO',
                            pattern: coveragePattern
                        ]],
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
            cleanWs()
        }
    }
}

// Helper function to detect changed services
def getChangedServices(List changes) {
    if (!changes || changes.isEmpty()) {
        return 'none'
    }

    def serviceMap = [
        'spring-petclinic-api-gateway'      : 'spring-petclinic-api-gateway',
        'spring-petclinic-customers-service': 'spring-petclinic-customers-service',
        'spring-petclinic-vets-service'     : 'spring-petclinic-vets-service',
        'spring-petclinic-visits-service'   : 'spring-petclinic-visits-service',
        'spring-petclinic-config-server'    : 'spring-petclinic-config-server',
        'spring-petclinic-discovery-server' : 'spring-petclinic-discovery-server',
        'spring-petclinic-admin-server'     : 'spring-petclinic-admin-server'
    ]

    def changedServices = []

    changes.each { change ->
        serviceMap.each { dir, service ->
            if (change.contains(dir) && !changedServices.contains(service)) {
                changedServices.add(service)
            }
        }
    }

    // Trigger full build if root files are changed
    if (changes.any { it.contains('pom.xml') || it.contains('Jenkinsfile') }) {
        return 'all'
    }

    return changedServices.isEmpty() ? 'none' : changedServices.join(',')
}
