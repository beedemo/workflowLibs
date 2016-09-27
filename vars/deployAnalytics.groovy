import groovy.json.*

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

def call(esHost, esHttpReqAuthId, environment, applicationName, artifact, deployUrl, completed, deployId) {
  //set up path for index
  def dateSuffix = new Date().format( 'yyyy-MM' )
  def esIndex = "deploy-$dateSuffix"

  def tokens = "${env.JOB_NAME}".tokenize('/')
  def name = tokens[tokens.size()-1]
  def url = "${esHost}/${esIndex}/${name}/${deployId}"

    def deployJson = """
        {
            "project_name": "$name",
            "job_name": "$env.JOB_NAME",
            "job_url": "$env.JOB_URL",
            "build_url": "$env.BUILD_URL",
            "environment": "$environment",
            "application_name": "$applicationName",
            "artifact": "$artifact",
            "deploy_url": "$deployUrl",
            "result": "$currentBuild.result",
            "completed": "$completed"
        }
     """
    def resp = httpRequest acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_JSON', httpMode: 'PUT', requestBody: deployJson, authentication: "$esHttpReqAuthId", url: "$url", validResponseCodes: '100:500'
    def respObj = jsonParse(resp.content)
    println "es resp: ${respObj}"
}
