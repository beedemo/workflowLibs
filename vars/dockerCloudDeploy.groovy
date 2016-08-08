// vars/dockerCloudDeploy.groovy
import groovy.json.*

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurper().parseText(json)
}

def call(nodeLabel, imageTag, name, innerPort, outerPort, httpRequestAuthId) {

  node(nodeLabel) {
      def getServiceResp = httpRequest acceptType: 'APPLICATION_JSON', httpMode: 'GET', authentication: "$httpRequestAuthId", url: "https://cloud.docker.com/api/app/v1/service/?name=$name", validResponseCodes: '100:500'
      def getServiceRespObj = jsonParse(getServiceResp.content)
      println("Status: "+getServiceResp.status)
      println("Content: "+getServiceRespObj) 
      def uuid = null
      if(getServiceRespObj.meta.total_count == 0 || getServiceRespObj.objects[0].state=='Terminated') {
        def createServiceJson = """
            {
              "image": "$imageTag",
              "name": "$name",
              "target_num_containers": 1,
              "container_ports": [{"protocol": "tcp", "inner_port": $innerPort, "outer_port": $outerPort}],
              "autorestart": "ALWAYS"
            }
        """
        println("createServiceJson: "+createServiceJson)
        def resp = httpRequest acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_JSON', httpMode: 'POST', requestBody: createServiceJson, authentication: "$httpRequestAuthId", url: 'https://cloud.docker.com/api/app/v1/service/', validResponseCodes: '100:500'
        def respObj = jsonParse(resp.content)
        println("Status: "+resp.status)
        println("Content: "+respObj) 
        uuid = respObj.uuid
        println "uuid: $uuid"
      } else {
        uuid = getServiceRespObj.objects[0].uuid
        println "uuid: $uuid"
        //need to update to use latest image version
        def updateServiceJson = """
            {
              "image": "$imageTag",
              "container_ports": [{"protocol": "tcp", "inner_port": $innerPort, "outer_port": $outerPort}]
            }
        """
        println("updateServiceJson: "+updateServiceJson)
        def updateServiceResp = httpRequest acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_JSON', httpMode: 'PATCH', requestBody: updateServiceJson, authentication: "$httpRequestAuthId", url: "https://cloud.docker.com/api/app/v1/service/$uuid/", validResponseCodes: '100:500'
        def updateServiceObj = jsonParse(updateServiceResp.content)
        println("Status: "+updateServiceResp.status)
        println("Content: "+updateServiceObj) 
        println("Headers: "+updateServiceResp.headers) 
      }
      //deploy the service
      //POST /api/app/v1/service/(uuid)/redeploy/
      def deployServiceResp = httpRequest acceptType: 'APPLICATION_JSON', httpMode: 'POST', authentication: "$httpRequestAuthId", url: "https://cloud.docker.com/api/app/v1/service/$uuid/redeploy/", validResponseCodes: '100:500'
      def deployServiceObj = jsonParse(deployServiceResp.content)
      println("Status: "+deployServiceResp.status)
      println("Content: "+deployServiceObj) 
      println("service endpoint: "+deployServiceObj.container_ports[0].endpoint_uri)
      def deployActionEndpoint = deployServiceResp.headers['X-DockerCloud-Action-URI']
      println("X-DockerCloud-Action-URI: "+deployActionEndpoint)
      // WAIT FOR DOCKER CLOUD DEPLOYMENT
      timeout(time: 2, unit: 'MINUTES') {
        waitUntil {
            def deployActionResp = httpRequest acceptType: 'APPLICATION_JSON', httpMode: 'GET', authentication: "$httpRequestAuthId", url: "https://cloud.docker.com$deployActionEndpoint", validResponseCodes: '100:500'
            def deployActionObj = jsonParse(deployActionResp.content)
            println("Status: "+deployActionResp.status)
            println("Deployment Action State: "+deployActionObj.state) 
            if(deployActionObj.state == "Failed") {
                error "Deployment failed"
            }
            return deployActionObj.state == "Success"
        }
      }
  }
}
