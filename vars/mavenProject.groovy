// vars/mavenProject.groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    //default to jdk 8
    def jdkVersion = config.jdk ?: 8
    echo "building with JDK ${jdkVersion}"
    def rebuildBuildImage = config.rebuildBuildImage ?: false
    properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '5', daysToKeepStr: '', numToKeepStr: '5']]])
    stage 'update protected branches'
    if(config.protectedBranches!=null && !config.protectedBranches.empty){
        //set up GitHub protected branches for specified branches
        def apiUrl = 'https://github.beescloud.com/api/v3'
        def credentialsId = '3ebff2f8-1013-42ff-a1e4-6d74e99f4ca1'
        githubProtectBranch(config.protectedBranches, apiUrl, credentialsId, config.org, config.repo)
    } else {
        echo 'no branches set to protect'
    }
    stage 'set up build image'
    node('docker-cloud') {
        def buildImage
        try {
            buildImage = docker.image("kmadel/${config.repo}-build").pull()
            echo "buildImage already built for ${config.repo}"
            if(rebuildBuildImage){
                echo "rebuild of buildImage ${config.repo}-build requested"
                error "rebuild of buildImage ${config.repo}-build requested"
            }
        } catch (e) {
            echo "buildImage needs to be built and pushed for ${config.repo}"
            def workspaceDir = pwd()
            checkout scm
            //docker.image('maven:3.3.3-jdk-8').run("-w ${workspaceDir} ")
            sh "docker run --name maven-build -v ${workspaceDir}:${workspaceDir} -w ${workspaceDir} kmadel/maven:3.3.3-jdk-${jdkVersion} mvn -Dmaven.repo.local=/maven-repo clean install"
            sh "docker commit maven-build kmadel/${config.repo}-build"
            sh "docker rm -f maven-build"
            withDockerRegistry(registry: [credentialsId: 'docker-registry-login']) { 
                sh "docker push kmadel/${config.repo}-build"
            }
        }
    }
    stage 'build'
    // now build, based on the configuration provided
    node('docker-cloud') {
        checkout scm
        docker.image("kmadel/${config.repo}-build").inside(){
            sh "mvn -Dmaven.repo.local=/maven-repo clean install"
        }
        mail to: "${config.email}", subject: "${config.repo} plugin build", body: "The build for ${config.org}/${config.repo} was successful"
    }
}