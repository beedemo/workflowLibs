//simple docker deployment via Docker Pipeline plugin and `docker.image.run`

def call(org, name, innerPort, outerPort, imageTag) {
  node {
    //DOCKER_DEPLOY_PROD_HOST and DOCKER_DEPLOY_PROD_CERT_ID must be set as environment properties (master, folder or job level)
    docker.withServer("$DOCKER_DEPLOY_PROD_HOST", "$DOCKER_DEPLOY_PROD_CERT_ID"){
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
