//must be called from within a node block that supports Docker
def call(org, name, pushCredId) {
    echo "creating mvn cache image"
    try {
        sh "docker rm -f mvn-cache"
    } catch (e) {
        echo "nothing to clean up"
    }
    sh "apk --update add git"
    sh "docker run --name mvn-cache -v ${WORKSPACE}:${WORKSPACE} -w ${WORKSPACE} maven:3.3.9-jdk-1.8-alpine mvn -Dmaven.repo.local=/usr/share/maven/ref clean package"
    try {
        //create a repo specific build image based on previous run
        sh "docker commit mvn-cache ${org}/${name}"
        sh "docker rm -f mvn-cache"
    } catch (e) {
        echo "error stopping and removing container"
    }
    //sign in to registry
    withDockerRegistry(registry: [credentialsId: "$pushCredId"]) { 
        //push repo specific image to Docker registry (DockerHub in this case)
        sh "docker push ${org}/${name}"
    }
}