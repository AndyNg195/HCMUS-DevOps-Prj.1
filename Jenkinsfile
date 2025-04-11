pipeline {
    agent any

    stages {
        stage('Detect Changes') {
            steps {
                script {
                    def changedFiles = getChangedFiles()
                    env.CHANGED_SERVICES = changedFiles.collect { 
                        // Sử dụng hàm findServiceDir đã sửa đổi
                        findServiceDir(it) 
                    }.unique().join(',')
                }
            }
        }

        stage('Build & Test') {
            when { expression { env.CHANGED_SERVICES?.trim() } }
            steps {
                script {
                    def services = env.CHANGED_SERVICES.split(',') as List
                    def parallelBuilds = [:]
                    
                    services.each { service ->
                        parallelBuilds[service] = {
                            dir(service) {
                                // Run Maven build and tests; ensure your Maven configuration
                                // generates the jacoco XML report as expected.
                                sh 'mvn clean verify -pl . -am'
                            }
                        }
                    }
                    parallel parallelBuilds
                    
                    // Record and enforce code coverage
                    recordCoverage(
                        // Use the updated parser name (e.g. COVERAGE_JACOCO) required by the integrated coverage plugin.
                        tools: [[parser: 'COVERAGE_JACOCO']],
                        sourceFileResolver: [[projectDir: "$WORKSPACE"]],
                        // Adjust the includes if your Maven setup produces the XML file at target/site/jacoco.xml.
                        includes: services.collect { "$it/target/site/jacoco.xml" }.join(','),
                        // Setting thresholds for line coverage: healthy, unstable, and unhealthy percentages are all set to 70%.
                        thresholds: [
                            line: [healthy: 70.0, unhealthy: 70.0, unstable: 70.0]
                        ],
                        // This option ensures that the build will fail if the recorded coverage is below the threshold.
                        failBuildIfCoverageIsLow: true
                    )
                }
            }
        }
    }
}

// Hàm helper tìm service dir bằng string manipulation và fileExists
def findServiceDir(String filePath) {
    def currentPath = filePath
    while (true) {
        // Kiểm tra pom.xml trong thư mục hiện tại
        if (fileExists("${currentPath}/pom.xml")) {
            return currentPath
        }
        // Di chuyển lên thư mục cha
        int lastSlash = currentPath.lastIndexOf('/')
        if (lastSlash == -1) break
        currentPath = currentPath.substring(0, lastSlash)
    }
    // Kiểm tra thư mục gốc
    if (fileExists("pom.xml")) {
        return ""
    }
    return null
}

// Hàm helper lấy danh sách file thay đổi (giữ nguyên)
def getChangedFiles() {
    def changedFiles = []
    currentBuild.changeSets.each { changeSet ->
        changeSet.items.each { commit ->
            commit.affectedFiles.each { file ->
                changedFiles.add(file.path)
            }
        }
    }
    return changedFiles
}