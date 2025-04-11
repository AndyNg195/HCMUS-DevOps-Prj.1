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
                    
                    // Use a glob pattern to look for any JaCoCo reports in the workspace.
                    recordCoverage(
                        tools: [[ parser: 'JACOCO', pattern: '**/target/site/jacoco.xml' ]]
                    )
                    
                    // Optionally, add a post-build step to enforce 70% coverage.
                    // For example, if you want to process one of the XML reports:
                    def reports = findFiles(glob: '**/target/site/jacoco.xml')
                    if (reports.size() > 0) {
                        def reportFile = reports[0].path  // Adjust if you need to aggregate multiple reports.
                        def coverageData = readFile file: reportFile
                        def jacocoXml = new XmlSlurper().parseText(coverageData)
                        
                        // Example: Assuming there's a <counter type="LINE" covered="X" missed="Y" /> element
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
                            echo "No line coverage data found in the report"
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