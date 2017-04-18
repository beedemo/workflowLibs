// vars/dockerBuildPublish.groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def dockerImage
    node('docker-cloud') {
      stage 'Configure Properties'
        //need to check for properties file from SCM
        checkout scm
        //tagAsLatest defaults to latest
        // github-organization-plugin jobs are named as 'org/repo/branch'
        tokens = "${env.JOB_NAME}".tokenize('/')
        org = tokens[tokens.size()-3]
        repo = tokens[tokens.size()-2]
        tag = tokens[tokens.size()-1]
    
        def d = [org: org, repo: repo, tag: tag, passTag: false, useTriggerTag: false, pushBranch: false]
        def props = readProperties defaults: d, file: 'dockerBuildPublish.properties'
    
        def tagAsLatest = config.tagAsLatest ?: true
        def dockerUserOrg = props['org']
        def dockerRepoName = props['repo']
        def dockerTag = props['tag']
        def dockerHubTriggerImage = props['dockerHubTriggerImage']
        def dockerBuildArgs = ' .'
        def tagArg = ''
        def pushBranch = props['pushBranch']
        echo "push non master branch: $pushBranch"
        if(dockerHubTriggerImage) {
            properties([pipelineTriggers(triggers: [[$class: 'DockerHubTrigger', options: [[$class: 'TriggerOnSpecifiedImageNames', repoNames: [dockerHubTriggerImage] as Set]]]]), 
                [$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5']]])
            //check if trigger tag should be passed in as build argument
            if(props['passTag'] && env.DOCKER_TRIGGER_TAG) {
                echo "passing trigger tags as build-arg: ${env.DOCKER_TRIGGER_TAG}"
                dockerBuildArgs = "--build-arg TAG_FROM_TRIGGER=${env.DOCKER_TRIGGER_TAG}" + dockerBuildArgs
            }
            if(props['useTriggerTag'] && env.DOCKER_TRIGGER_TAG) {
                echo "using trigger tags as image tag: ${env.DOCKER_TRIGGER_TAG}"
                dockerTag = env.DOCKER_TRIGGER_TAG
            }
        }
    
        //config.dockerHubCredentialsId is required
        if(!config.dockerHubCredentialsId) {
            error 'dockerHubCredentialsId is required'
        }
      stage 'Build Docker Image'
        dockerImage = docker.build("${dockerUserOrg}/${dockerRepoName}:${dockerTag}", dockerBuildArgs)
        if(env.BRANCH_NAME=="master" || pushBranch) {
          stage 'Publish Docker Image'
              withDockerRegistry(registry: [credentialsId: "${config.dockerHubCredentialsId}"]) {
                dockerImage.push()
                if(tagAsLatest) {
                  dockerImage.push("latest")
                }
              }
        } else {
            echo "Skipped push for non-master branch"
        }
    }
}
