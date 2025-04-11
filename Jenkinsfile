pipeline {
    agent any

    tools {
        maven 'M3' // Cần cấu hình Maven trong Global Tool Configuration
        jdk 'jdk17' // Cần cấu hình JDK trong Global Tool Configuration
    }

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
                                // Run Maven build and tests.
                                sh 'mvn clean verify -pl . -am'
                            }
                        }
                    }
                    parallel parallelBuilds
                    
                    // Construct a comma-separated pattern for all services.
                    def patterns = services.collect { "$it/target/site/jacoco.xml" }.join(',')
                    
                    // Record code coverage using the integrated coverage plugin.
                    // Use the correct parser ('JACOCO') and supported parameters.
                    recordCoverage(
                        tools: [[ parser: 'JACOCO', pattern: patterns ]],
                        globalThresholds: [ line: 70 ]
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