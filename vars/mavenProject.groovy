// vars/mavenProject.groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    //used for analytics indexing
    def short_commit
    //don't need to build again if done as part of creating or updating custom Docker build image
    def doBuild = true
    //default to 'clean install'
    def mvnBuildCmd = config.mavenBuildCommand ?: 'clean install'
    //default to jdk 8
    def jdkVersion = config.jdk ?: 8
    def mavenVersion = config.maven ?: '3.3.3'
    echo "building with JDK ${jdkVersion}"
    //if any maven dependency changes are detected, then will auto-update
    def autoUpdateBuildImage = config.autoUpdateBuildImage ?: true
    def rebuildBuildImage = config.rebuildBuildImage ?: false
    //will use docker commit and push to update custom build image
    def updateBuildImage = config.updateBuildImage ?: false
    //build Docker image from mvn package, default to false
    def isDockerDeploy = config.isDockerDeploy ?: false
    properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '5', daysToKeepStr: '', numToKeepStr: '5']]])
    stage('create/update build image') {
        node {
            def buildImage
            def workspaceDir = pwd()
            //will use volumes-from for detected containerId
            def nodeContainerId = sh returnStdout: true, script: "cat /proc/1/cgroup | grep \'docker/\' | tail -1 | sed \'s/^.*\\///\' | cut -c 1-12"
            nodeContainerId = nodeContainerId.trim()
            try {
                buildImage = docker.image("beedemo/${config.repo}-build").pull()
                echo "buildImage already built for ${config.repo}"
                if(rebuildBuildImage){
                    echo "rebuild of buildImage ${config.repo}-build requested"
                    error "rebuild of buildImage ${config.repo}-build requested"
                } else if(updateBuildImage) {
                    echo "buildImage to be updated and pushed for ${config.repo}"
                    checkout scm
                    sh('git rev-parse HEAD > GIT_COMMIT')
                    git_commit=readFile('GIT_COMMIT')
                    short_commit=git_commit.take(7)
                    //refreshed image, useful if there are one or more new dependencies
                    sh "docker run --name maven-build -w ${workspaceDir} --volumes-from ${nodeContainerId} beedemo/${config.repo}-build mvn -Dmaven.repo.local=/maven-repo ${mvnBuildCmd}"
                                //create a repo specific build image based on previous run
                    sh "docker commit maven-build beedemo/${config.repo}-build"
                    sh "docker rm -f maven-build"
                    //sign in to registry
                    //withDockerRegistry(registry: [credentialsId: 'docker-registry-login']) { 
                        //push repo specific image to Docker registry (DockerHub in this case)
                        //sh "docker push beedemo/${config.repo}-build"
                    //}
                    //stash an set skip build
                    stash name: "target-stash", includes: "target/*"
                    doBuild = false
                }
            } catch (e) {
                echo "buildImage needs to be built and pushed for ${config.repo}"
                checkout scm
                //using specific maven repo directory '/maven-repo' to cache dependencies for later builds
                def shCmd = "docker run --name maven-build -w ${workspaceDir} --volumes-from ${nodeContainerId} kmadel/maven:${mavenVersion}-jdk-${jdkVersion} mvn -Dmaven.repo.local=/maven-repo ${mvnBuildCmd}"
                echo shCmd
                sh shCmd
                //create a repo specific build image based on previous run
                sh "docker commit maven-build beedemo/${config.repo}-build"
                sh "docker rm -f maven-build"
                //sign in to registry
                //withDockerRegistry(registry: [credentialsId: 'docker-registry-login']) { 
                    //push repo specific image to Docker registry (DockerHub in this case)
                    //sh "docker push beedemo/${config.repo}-build"
                //}
                //stash an set skip build
                stash name: "target-stash", includes: "target/*"
                doBuild = false
            }
        }
    }
    stage('build') {
        // now build, based on the configuration provided, 
        //if not already built as part of creating or upgrading custom Docker build image
        if(doBuild) {
            node {
                try {
                    checkout scm
                    sh('git rev-parse HEAD > GIT_COMMIT')
                    git_commit=readFile('GIT_COMMIT')
                    short_commit=git_commit.take(7)
                    //build with repo specific build image
                    docker.image("beedemo/${config.repo}-build").inside(){
                        sh "mvn -Dmaven.repo.local=/maven-repo ${mvnBuildCmd}"
                    }
                    echo 'stashing target directory'
                    stash name: "target-stash", includes: "target/*"
                    currentBuild.result = "success"
                } catch (e) {
                    currentBuild.result = "failure"
                }
            }
        } else {
            echo "already completed build in 'create/update build image' stage"
        }
    }
    if(env.BRANCH_NAME.startsWith("master")) {
        stage('Deploy to Prod') {
            if(isDockerDeploy) {
                node {
                    //first must stop any previous running image for ${config.repo}
                    try{
                        sh "docker stop ${config.repo}"
                        sh "docker rm ${config.repo}"
                    } catch (Exception _) {
                        echo "no container to stop"
                    }
                    //tag to use for docker deploy image
                    def dockerTag = "${env.BUILD_NUMBER}-${short_commit}"
                    def deployImage
                    //unstash JAR and Dockerfile
                    unstash 'target-stash'
                    dir('target') {
                        deployImage = docker.build "beedemo/${config.repo}:${dockerTag}"
                    }
                    //just going to run the image
                    deployImage.run("--name ${config.repo} -p ${config.port}:8080")
                }
            }
        }
    }
}
