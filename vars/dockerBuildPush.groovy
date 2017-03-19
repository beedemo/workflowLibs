//must be called from an agent that supports Docker
def call(org, name, tag, dir, pushCredId) {
    dockerImage = docker.build("$org/$name:$tag", "$dir")
    withDockerRegistry(registry: [credentialsId: "$pushCredId"]) { 
        dockerImage.push()
    }
}