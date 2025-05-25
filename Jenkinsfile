pipeline {
    agent any

    environment {
        DOCKERHUB_CREDENTIALS = 'dockerhub-credentials'
        DOCKERHUB_USERNAME = 'andyng195'
        GIT_CREDENTIALS_ID = 'github-credentials'
        BRANCH_NAME = 'main'
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
                        'spring-petclinic-api-gateway'       : 'spring-petclinic-api-gateway',
                        'spring-petclinic-customers-service' : 'spring-petclinic-customers-service',
                        'spring-petclinic-vets-service'      : 'spring-petclinic-vets-service',
                        'spring-petclinic-visits-service'    : 'spring-petclinic-visits-service',
                        'spring-petclinic-config-server'     : 'spring-petclinic-config-server',
                        'spring-petclinic-discovery-server'  : 'spring-petclinic-discovery-server',
                        'spring-petclinic-admin-server'      : 'spring-petclinic-admin-server'
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

    stage('Update values.dev.yaml and Push to GitHub') {
            steps {
                script {
                    def changedServices = readJSON file: 'changedServices.json'

                    changedServices.each { svc ->
                        def fullName = svc.name                         // ví dụ: spring-petclinic-vets-service
                        def yamlKey = fullName.replace('spring-petclinic-', '')  // ví dụ: vets-service

                        echo "Updating tag of ${yamlKey} in values.dev.yaml to ${COMMIT_ID}"

                        sh """
                            awk -v svc="${yamlKey}:" -v tag="${COMMIT_ID}" '
                                \$1 == svc { in_block = 1; print; next }
                                in_block && /tag:/ { sub(/tag: .*/, "tag: " tag); in_block = 0 }
                                { print }
                            ' environment/values.dev.yaml > environment/values.dev.yaml.tmp && mv environment/values.dev.yaml.tmp environment/values.dev.yaml
                        """
                }


                    // Cấu hình Git để commit và push
                    withCredentials([usernamePassword(credentialsId: GIT_CREDENTIALS_ID, usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
                        sh """
                            git config user.name "${GIT_USER}"
                            git config user.email "${GIT_USER}@jenkins"
                            git add environment/values.dev.yaml
                            git commit -m "Update image tags in values.dev.yaml to ${COMMIT_ID}"
                            git push https://${GIT_USER}:${GIT_PASS}@github.com/andyng195/argocd-devops.git ${BRANCH_NAME}
                        """
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
