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
                    
                    // Record code coverage using a glob that searches all service directories.
                    // This assumes that each service produces a JaCoCo XML report at target/site/jacoco.xml.
                    recordCoverage(
                        tools: [[ parser: 'JACOCO', pattern: '**/target/site/jacoco.xml' ]]
                    )
                    
                    // Use the shell to find coverage reports
                    def reportsOutput = sh(script: "find . -type f -name 'jacoco.xml'", returnStdout: true).trim()
                    if (reportsOutput) {
                        def reports = reportsOutput.split("\n")
                        def reportFile = reports[0].trim()  // You could also iterate or aggregate reports if needed.
                        echo "Using JaCoCo report: ${reportFile}"
                        
                        // Read the JaCoCo XML report
                        def coverageData = readFile(file: reportFile)
                        def jacocoXml = new XmlSlurper().parseText(coverageData)
                        
                        // Extract line coverage information.
                        def lineCounter = jacocoXml.counter.find { it.@type == 'LINE' }
                        if (lineCounter) {
                            int covered = lineCounter.@covered.toInteger()
                            int missed = lineCounter.@missed.toInteger()
                            int total = covered + missed
                            def coveragePercent = (covered / total * 100).round(2)
                            echo "Line Coverage: ${coveragePercent}%"
                            if (coveragePercent < 70.0) {
                                error("Coverage is below threshold: ${coveragePercent}% < 70%")
                            }
                        } else {
                            echo "No line coverage data found in the report."
                        }
                    } else {
                        echo "No JaCoCo XML reports found!"
                    }
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