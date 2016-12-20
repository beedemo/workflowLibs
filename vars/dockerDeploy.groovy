//simple docker deployment via Docker Pipeline plugin and `docker.image.run`

@NonCPS
def getDockerHost() {
    return System.getenv().DOCKER_DEPLOY_PROD_HOST   
}

@NonCPS
def getCertId() {
    return System.getenv().DOCKER_DEPLOY_PROD_CERT_ID  
}

def call(org, name, innerPort, outerPort, imageTag) {
  node {
    def dockerHost = getDockerHost()
    def certId = getCertId()
    docker.withServer("$dockerHost", "$certId"){
      try {
        sh "docker stop $name"
        sh "docker rm $name"
      } catch (Exception _) {
        echo "no container with name $name to stop"        
      }
      //will pull specified image:tag and then run on $dockerHost
      docker.image("$org/$name:$imageTag").run("--name $name -p $innerPort:$outerPort")
    }
  }
}
