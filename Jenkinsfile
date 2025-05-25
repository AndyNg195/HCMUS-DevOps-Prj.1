pipeline {
    agent any

    environment {
        DOCKERHUB_CREDENTIALS = 'dockerhub-credentials'
        DOCKERHUB_USERNAME = 'andyng195'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Get Commit ID') {
            steps {
                script {
                    COMMIT_ID = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    echo "Commit ID: ${COMMIT_ID}"
                }
            }
        }

        stage('Detect Changed Services') {
            steps {
                script {
                    // So sánh với commit cha gần nhất (bất kể nhánh nào)
                    def changedFiles = sh(script: 'git diff --name-only HEAD^ HEAD', returnStdout: true).trim().split('\n')

                    def serviceMap = [
                        'spring-petclinic-api-gateway'       : 'api-gateway',
                        'spring-petclinic-customers-service' : 'customers-service',
                        'spring-petclinic-vets-service'      : 'vets-service',
                        'spring-petclinic-visits-service'    : 'visits-service',
                        'spring-petclinic-config-server'     : 'config-server',
                        'spring-petclinic-discovery-server'  : 'discovery-server',
                        'spring-petclinic-admin-server'      : 'admin-server',
                        'spring-petclinic-genai-service'     : 'genai-service'
                    ]

                    def changedServices = []

                    serviceMap.each { dir, service ->
                        if (changedFiles.any { it.startsWith(dir + '/') }) {
                            changedServices.add([dir: dir, name: service])
                        }
                    }

                    if (changedServices.isEmpty()) {
                        echo "No relevant service changed in this commit. Skipping build and push."
                        currentBuild.result = 'SUCCESS'
                        // Dừng pipeline
                        return
                    }

                    // Ghi lại services bị thay đổi để dùng trong bước sau
                    writeFile file: 'changedServices.json', text: groovy.json.JsonOutput.toJson(changedServices)
                    echo "Changed services: ${changedServices.collect { it.dir }.join(', ')}"
                }
            }
        }

        stage('Build and Push Changed Services') {
            steps {
                script {
                    def changedServices = readJSON file: 'changedServices.json'

                    docker.withRegistry('https://index.docker.io/v1/', DOCKERHUB_CREDENTIALS) {
                        for (svc in changedServices) {
                            def dir = svc.dir
                            def artifactName = svc.name

                            echo "Building service: ${dir}"

                            // Build JAR
                            sh "./mvnw -pl ${dir} -am clean package -DskipTests"

                            // Copy JAR vào thư mục docker để build
                            sh "cp ${dir}/target/*.jar docker/${artifactName}.jar"

                            def image = docker.build("${DOCKERHUB_USERNAME}/${artifactName}:${COMMIT_ID}", "--build-arg ARTIFACT_NAME=${artifactName} docker/")

                            // Push tag theo commit
                            image.push()

                            // Push thêm tag latest
                            sh "docker tag ${DOCKERHUB_USERNAME}/${artifactName}:${COMMIT_ID} ${DOCKERHUB_USERNAME}/${artifactName}:latest"
                            sh "docker push ${DOCKERHUB_USERNAME}/${artifactName}:latest"
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Changed services built and pushed successfully with tag ${COMMIT_ID}."
        }
        failure {
            echo "Build failed!"
        }
    }
}
