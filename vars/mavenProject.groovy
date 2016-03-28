// vars/mavenProject.groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    stage 'build'
    // now build, based on the configuration provided
    node('docker-cloud') {
        checkout scm
        docker.image('kmadel/maven:3.3.3-jdk-8').inside(){
            sh "mvn clean package"
            mail to: "${config.email}", subject: "${config.repo} plugin build", body: "Email body"
        }
    }
}