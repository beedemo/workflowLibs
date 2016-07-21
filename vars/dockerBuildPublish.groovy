// vars/dockerBuildPublish.groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    //tagAsLatest defaults to latest
    // github-organization-plugin jobs are named as 'org/repo/branch'
    tokens = "${env.JOB_NAME}".tokenize('/')
    org = tokens[tokens.size()-3]
    repo = tokens[tokens.size()-2]
    tag = tokens[tokens.size()-1]
    
    def d = [org: org, repo: repo, tag: tag]
    def props = readProperties defaults: d, file: 'dockerBuildPublish.properties', text: 'other=Override'
        assert props['test'] == 'One'
    
    def tagAsLatest = config.tagAsLatest ?: true
    def dockerUserOrg = props['org']
    def dockerRepoName = props['repo']
    def dockerTag = props['tag']
    
    //config.dockerHubCredentialsId is required
    if(!config.dockerHubCredentialsId) {
        error 'dockerHubCredentialsId is required'
    }

    def dockerImage
    node('docker-cloud') {
      stage 'Build Docker Image'
        checkout scm
        dockerImage = docker.build "${dockerUserOrg}/${dockerRepoName}:${dockerTag}"
    
      stage 'Publish Docker Image'
          withDockerRegistry(registry: [credentialsId: "${config.dockerHubCredentialsId}"]) {
            dockerImage.push()
            if(tagAsLatest) {
              dockerImage.push("latest")
            }
          }
    }
}
