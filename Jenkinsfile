pipeline {
    agent any

    tools {
        maven 'M3'
        jdk 'jdk17'
    }

    environment {
        CHANGED_SERVICES = 'none'
    }

    stages {
        stage('Checkout & Detect Changes') {
            steps {
                checkout scm
                script {
                    def changes = ''
                    for (changeLog in currentBuild.changeSets) {
                        for (entry in changeLog.items) {
                            for (file in entry.affectedFiles) {
                                changes += file.path + '\n'
                            }
                        }
                    }

                    echo "Detected changed files:\n${changes}"
                    env.CHANGED_SERVICES = getChangedServices(changes)

                    echo "Detected changed services: ${env.CHANGED_SERVICES}"

                    if (env.CHANGED_SERVICES == 'none') {
                        currentBuild.result = 'NOT_BUILT'
                        error "No changes detected in any services - skipping build"
                    }
                }
            }
        }

        stage('Build Changed Services') {
            when {
                expression { return env.CHANGED_SERVICES != 'all' && env.CHANGED_SERVICES != 'none' }
            }
            steps {
                script {
                    def services = env.CHANGED_SERVICES.split(',')
                    services.each { service ->
                        echo "Building ${service}"
                        sh "./mvnw clean package -DskipTests -pl ${service} -am"
                    }
                }
            }
        }

        stage('Build All') {
            when {
                expression { return env.CHANGED_SERVICES == 'all' }
            }
            steps {
                sh './mvnw clean package -DskipTests'
            }
        }

        stage('Test Changed Services') {
            when {
                expression { return env.CHANGED_SERVICES != 'all' && env.CHANGED_SERVICES != 'none' }
            }
            steps {
                script {
                    def services = env.CHANGED_SERVICES.split(',')
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
                expression { return env.CHANGED_SERVICES == 'all' }
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
                if (env.CHANGED_SERVICES != 'none') {
                    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true

                    def coveragePattern = (env.CHANGED_SERVICES == 'all') ?
                        '**/target/site/jacoco/jacoco.xml' :
                        env.CHANGED_SERVICES.split(',').collect {
                            "**/${it}/target/site/jacoco/jacoco.xml"
                        }.join(',')

                    recordCoverage(
                        tools: [[
                            parser: 'JACOCO',
                            pattern: coveragePattern
                        ]],
                        sourceFileResolver: [
                            [projectDir: "$WORKSPACE"],
                            [projectDir: "$WORKSPACE", subDir: "spring-petclinic-*"]
                        ]
                    )

                    publishHTML(
                        target: [
                            reportDir: 'target/site/jacoco-aggregate',
                            reportFiles: 'index.html',
                            reportName: 'JaCoCo Coverage Report',
                            keepAll: true
                        ]
                    )
                }
            }

            cleanWs()
        }
    }
}

def getChangedServices(String changes) {
    if (!changes?.trim()) {
        return 'none'
    }

    def serviceMap = [
        'spring-petclinic-api-gateway'    : 'spring-petclinic-api-gateway',
        'spring-petclinic-customers-service' : 'spring-petclinic-customers-service',
        'spring-petclinic-vets-service'   : 'spring-petclinic-vets-service',
        'spring-petclinic-visits-service' : 'spring-petclinic-visits-service',
        'spring-petclinic-config-server'  : 'spring-petclinic-config-server',
        'spring-petclinic-discovery-server': 'spring-petclinic-discovery-server',
        'spring-petclinic-admin-server'   : 'spring-petclinic-admin-server'
    ]

    def changedServices = []
    changes.split('\n').each { change ->
        change = change.trim()
        serviceMap.each { dir, service ->
            if (change.contains(dir) && !changedServices.contains(service)) {
                changedServices.add(service)
            }
        }
    }

    echo changedServices

    if (changes.contains('pom.xml') || changes.contains('Jenkinsfile')) {
        return 'all'
    }

    return changedServices.isEmpty() ? 'none' : changedServices.join(',')
}
