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

        stage('Get Commit ID and Git Tag (if any)') {
            steps {
                script {
                    COMMIT_ID = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    echo "Commit ID: ${COMMIT_ID}"

                    GIT_TAG = sh(script: 'git describe --tags --exact-match || echo ""', returnStdout: true).trim()
                    if (GIT_TAG) {
                        echo "Git tag detected: ${GIT_TAG}"
                    } else {
                        echo "No Git tag found for this commit."
                    }
                }
            }
        }

        stage('Detect Changed Services') {
            steps {
                script {
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

                    if (changedServices.isEmpty() && !GIT_TAG) {
                        echo "No relevant service changed in this commit. Skipping build and push."
                        currentBuild.result = 'SUCCESS'
                        return
                    }

                    writeFile file: 'changedServices.json', text: groovy.json.JsonOutput.toJson(changedServices)
                    echo "Changed services: ${changedServices.collect { it.dir }.join(', ')}"
                }
            }
        }

        stage('Build and Push Changed Services') {
            steps {
                script {

                    def serviceMap = [
                        'spring-petclinic-api-gateway'       : 'spring-petclinic-api-gateway',
                        'spring-petclinic-customers-service' : 'spring-petclinic-customers-service',
                        'spring-petclinic-vets-service'      : 'spring-petclinic-vets-service',
                        'spring-petclinic-visits-service'    : 'spring-petclinic-visits-service',
                        'spring-petclinic-config-server'     : 'spring-petclinic-config-server',
                        'spring-petclinic-discovery-server'  : 'spring-petclinic-discovery-server',
                        'spring-petclinic-admin-server'      : 'spring-petclinic-admin-server'
                    ]

                    def changedServices = readJSON file: 'changedServices.json'

                    docker.withRegistry('https://index.docker.io/v1/', DOCKERHUB_CREDENTIALS) {
                        // 1) Build & push COMMIT_ID and latest
                        for (svc in changedServices) {
                            def dir          = svc.dir
                            def artifactName = svc.name

                            echo "Building service: ${dir}"
                            sh "./mvnw -pl ${dir} -am clean package -DskipTests"
                            sh "cp ${dir}/target/*.jar docker/${artifactName}.jar"

                            // build with COMMIT_ID tag
                            def image = docker.build(
                                "${DOCKERHUB_USERNAME}/${artifactName}:${COMMIT_ID}",
                                "--build-arg ARTIFACT_NAME=${artifactName} docker/"
                            )
                            image.push()

                            // retag to latest and push
                            sh """
                                docker tag ${DOCKERHUB_USERNAME}/${artifactName}:${COMMIT_ID} \
                                        ${DOCKERHUB_USERNAME}/${artifactName}:latest
                                docker push ${DOCKERHUB_USERNAME}/${artifactName}:latest
                            """
                        }

                        // 2) Once all are pushed as latest, tag them all with GIT_TAG
                        if (GIT_TAG) {
                            echo "Tagging all :latest images with ${GIT_TAG}"
                            serviceMap.values().each { artifactName ->
                                sh """
                                docker pull ${DOCKERHUB_USERNAME}/${artifactName}:latest
                                docker tag  ${DOCKERHUB_USERNAME}/${artifactName}:latest \
                                            ${DOCKERHUB_USERNAME}/${artifactName}:${GIT_TAG}
                                docker push ${DOCKERHUB_USERNAME}/${artifactName}:${GIT_TAG}
                                """
                            }
                        }
                    }
                }
            }
        }

        stage('Update values.dev.yaml, values.staging.yaml and Push to GitHub') {
            steps {
                script {
                    def changedServices = readJSON file: 'changedServices.json'

                    withCredentials([usernamePassword(credentialsId: GIT_CREDENTIALS_ID, usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
                        sh """
                            # Clone repo argocd-devops (về thư mục riêng)
                            rm -rf argocd-devops
                            git clone https://${GIT_USER}:${GIT_PASS}@github.com/andyng195/argocd-devops.git
                        """
                        
                        // Cập nhật tag cho các service trong values.dev.yaml
                        changedServices.each { svc ->
                            def fullName = svc.name
                            def yamlKey = fullName.replace('spring-petclinic-', '')
                            echo "Updating tag of ${yamlKey} in argocd-devops/environments/values.dev.yaml to ${COMMIT_ID}"

                            sh """
                                awk -v svc="${yamlKey}:" -v tag="${COMMIT_ID}" '
                                    \$1 == svc { in_block = 1; print; next }
                                    in_block && /tag:/ { sub(/tag: .*/, "tag: " tag); in_block = 0 }
                                    { print }
                                ' argocd-devops/environments/values.dev.yaml > argocd-devops/environments/values.dev.yaml.tmp && \
                                mv argocd-devops/environments/values.dev.yaml.tmp argocd-devops/environments/values.dev.yaml
                            """
                        }

                        if(GIT_TAG) {  
                            // Cập nhật tag cho các service trong values.staging.yaml
                            echo "Updating tag in argocd-devops/environments/values.staging.yaml to ${GIT_TAG}"

                            sh """
                                cat argocd-devops/environments/values.staging.yaml 
                                sed -i 's|^imageTag: &tag .*|imageTag: \\&tag ${GIT_TAG}|' argocd-devops/environments/values.staging.yaml
                                cat argocd-devops/environments/values.staging.yaml
                            """
                        }

                        // Commit và push
                        sh """
                            cd argocd-devops
                            git config user.name Jenkins
                            git config user.email email@jenkins
                            git add environments
                            git commit -m "Update image tags to ${COMMIT_ID} by Jenkins"
                            git push https://${GIT_USER}:${GIT_PASS}@github.com/andyng195/argocd-devops.git
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Changed services built and pushed successfully."
        }
        failure {
            echo "Build failed!"
        }
    }
}
