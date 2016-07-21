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
    org = tokens[list.size()-3]
    repo = tokens[list.size()-2]
    branch = tokens[list.size()-1]
    def tagAsLatest = config.tagAsLatest ?: true
    def dockerUserOrg = config.dockerUserOrg ?: org
    def dockerRepoName = config.dockerRepoName ?: repo
    def dockerTag = config.dockerTag ?: branch
    
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
              dockerImage.push("${dockerUserOrg}/${dockerRepoName}:latest")
            }
          }
    }
}
