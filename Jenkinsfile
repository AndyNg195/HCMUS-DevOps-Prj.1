pipeline {
    agent any

    environment {
        DOCKERHUB_CREDENTIALS = 'dockerhub-credentials-id'
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

        stage('Build and Push All Services') {
            steps {
                script {
                    def services = [
                        'spring-petclinic-vets-service',
                        'spring-petclinic-visits-service',
                        'spring-petclinic-customers-service',
                        'spring-petclinic-api-gateway',
                        'spring-petclinic-discovery-server',
                        'spring-petclinic-config-server',
                        'spring-petclinic-admin-server',
                        'spring-petclinic-genai-service'
                        
                    ]

                    docker.withRegistry('https://index.docker.io/v1/', DOCKERHUB_CREDENTIALS) {
                        for (service in services) {
                            echo "📦 Building service: ${service}"

                            def artifactName = service
                            def jarPath = "${service}/target/${artifactName}.jar"

                            sh "./mvnw -pl ${service} -am clean package -DskipTests"

                            sh "cp ${service}/target/*.jar docker/${artifactName}.jar"

                            def dockerImage = docker.build("${DOCKERHUB_USERNAME}/${artifactName}:${COMMIT_ID}", "--build-arg ARTIFACT_NAME=${artifactName} docker/")

                            dockerImage.push()
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo "All images built and pushed successfully with tag ${COMMIT_ID}."
        }
        failure {
            echo "Build failed!"
        }
    }
}
