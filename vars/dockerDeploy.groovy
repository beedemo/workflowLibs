//simple docker deployment via Docker Pipeline plugin and `docker.image.run`

def call(org, name, innerPort, outerPort, imageTag) {
  node {
    def dockerHost = env.DOCKER_DEPLOY_PROD_HOST
    def certId = env.DOCKER_DEPLOY_PROD_CERT_ID
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
