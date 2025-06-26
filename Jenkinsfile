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
                script {
                    // Full-depth checkout
                    checkout([
                        $class: 'GitSCM',
                        branches: scm.branches,
                        userRemoteConfigs: scm.userRemoteConfigs,
                        extensions: [
                            [$class: 'CloneOption', shallow: false, depth: 0]
                        ]
                    ])

                    def targetBranch = env.CHANGE_TARGET ?: env.BRANCH_NAME
                    sh "git fetch origin ${targetBranch}"

                    if (env.CHANGE_TARGET) {
                        // Pull request build using merge strategy
                        echo "Detected PR build to '${env.CHANGE_TARGET}'. Comparing HEAD~2 (PR head) with HEAD (merged result)."
                        def diffOutput = sh(script: "git diff --name-only HEAD~2 HEAD", returnStdout: true).trim()
                        changeFiles = diffOutput ? diffOutput.split("\n").collect { it.trim() } : []
                    } else {
                        // Normal push to a branch
                        echo "Detected branch push to '${env.BRANCH_NAME}'. Comparing previous commit (HEAD~1) with current (HEAD)."
                        def diffOutput = sh(script: "git diff --name-only HEAD~1 HEAD", returnStdout: true).trim()
                        changeFiles = diffOutput ? diffOutput.split("\n").collect { it.trim() } : []
                    }

                    echo "Changed files:\n${changeFiles.join('\n')}"

                    changedServices = getChangedServices(changeFiles)
                    echo "Changed services: ${changedServices}"

                    if (changedServices.isEmpty()) {
                        skipBuild = true
                        currentBuild.result = 'NOT_BUILT'
                        echo "No changes detected - skipping build and test stages"
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
                }
            }
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
        return ['ALL'] // trigger full build
    }

    return services
}

// Helper function to detect full build flag
def isFullBuild(List services) {
    return services.size() == 1 && services[0] == 'ALL'
}
