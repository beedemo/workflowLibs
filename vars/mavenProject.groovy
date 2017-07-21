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
    properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '5', daysToKeepStr: '', numToKeepStr: '5']]])
    stage 'update protected branches'
    if(config.protectedBranches!=null && !config.protectedBranches.empty){
        //set up GitHub protected branches for specified branches
        def apiUrl = 'https://github.beescloud.com/api/v3'
        def credentialsId = 'beedemo-user-github-token'
        githubProtectBranch(config.protectedBranches, apiUrl, credentialsId, config.org, config.repo)
    } else {
        echo 'no branches set to protect'
    }
    stage 'create/update build image'
    node('docker-cloud') {
        def buildImage
        try {
            buildImage = docker.image("beedemo/${config.repo}-build").pull()
            echo "buildImage already built for ${config.repo}"
            if(rebuildBuildImage){
                echo "rebuild of buildImage ${config.repo}-build requested"
                error "rebuild of buildImage ${config.repo}-build requested"
            } else if(updateBuildImage) {
                echo "buildImage to be updated and pushed for ${config.repo}"
                def workspaceDir = pwd()
                checkout scm
                sh('git rev-parse HEAD > GIT_COMMIT')
                git_commit=readFile('GIT_COMMIT')
                short_commit=git_commit.take(7)
                //refreshed image, useful if there are one or more new dependencies
                sh "docker run --name maven-build -v ${workspaceDir}:${workspaceDir} -w ${workspaceDir} beedemo/${config.repo}-build mvn -Dmaven.repo.local=/maven-repo ${mvnBuildCmd}"
                            //create a repo specific build image based on previous run
                sh "docker commit maven-build beedemo/${config.repo}-build"
                sh "docker rm -f maven-build"
                //sign in to registry
                withDockerRegistry(registry: [credentialsId: 'docker-registry-login']) { 
                    //push repo specific image to Docker registry (DockerHub in this case)
                    sh "docker push beedemo/${config.repo}-build"
                }
                //stash an set skip build
                stash name: "target-stash", includes: "target/*"
                doBuild = false
            }
        } catch (e) {
            echo "buildImage needs to be built and pushed for ${config.repo}"
            def workspaceDir = pwd()
            checkout scm
            //using specific maven repo directory '/maven-repo' to cache dependencies for later builds
            def shCmd = "docker run --name maven-build -v ${workspaceDir}:${workspaceDir} -w ${workspaceDir} kmadel/maven:${mavenVersion}-jdk-${jdkVersion} mvn -Dmaven.repo.local=/maven-repo ${mvnBuildCmd}"
            echo shCmd
            sh shCmd
            //create a repo specific build image based on previous run
            sh "docker commit maven-build beedemo/${config.repo}-build"
            sh "docker rm -f maven-build"
            //sign in to registry
            withDockerRegistry(registry: [credentialsId: 'docker-hub-beedemo']) { 
                //push repo specific image to Docker registry (DockerHub in this case)
                sh "docker push beedemo/${config.repo}-build"
            }
            //stash an set skip build
            stash name: "target-stash", includes: "target/*"
            doBuild = false
        }
    }
    stage 'build'
    // now build, based on the configuration provided, 
    //if not already built as part of creating or upgrading custom Docker build image
    if(doBuild) {
        node('docker-cloud') {
            //get github repo team to use as hipchat room
            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                def hipchatRoom = sh(returnStdout: true, script: "curl -s -u ${env.USERNAME}:${env.PASSWORD} 'https://api.github.com/repos/beedemo/${config.repo}/teams' | jq -r '.[0] | .name' | tr -d '\n'")
            }
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
                hipchatSend color: 'GREEN', textFormat: true, message: "(super) Pipeline for ${config.org}/${config.repo} complete - Job Name: ${env.JOB_NAME} Build Number: ${env.BUILD_NUMBER} status: ${currentBuild.result} ${env.BUILD_URL}", room: hipchatRoom, server: 'cloudbees.hipchat.com', token: 'A6YX8LxNc4wuNiWUn6qHacfO1bBSGXQ6E1lELi1z', v2enabled: true
            } catch (e) {
                currentBuild.result = "failure"
                hipchatSend color: 'RED', textFormat: true, message: "(angry) Pipeline for ${config.org}/${config.repo} complete - Job Name: ${env.JOB_NAME} Build Number: ${env.BUILD_NUMBER} status: ${currentBuild.result} ${env.BUILD_URL}", room: hipchatRoom, server: 'cloudbees.hipchat.com', token: 'A6YX8LxNc4wuNiWUn6qHacfO1bBSGXQ6E1lELi1z', v2enabled: true
            }
        }
    } else {
        echo "already completed build in 'create/update build image' stage"
    }
    if(env.BRANCH_NAME==config.deployBranch){
        stage name: 'Deploy to Prod', concurrency: 1
        if(config.deployType=='websphereLibertyContainer') {
            //build and push deployment image
            node('docker-cloud') {
                unstash "target-stash"
                dockerBuildPush("beedemo", config.repo, "${BUILD_NUMBER}", "target", "docker-hub-beedemo")
                dockerDeploy("docker-cloud","beedemo", config.repo, 9080, 9080, "${BUILD_NUMBER}")
            }
        }
    }
}
