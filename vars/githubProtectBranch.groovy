// vars/githubProtectBranch.groovy
def call(branches, apiUrl, credentialsId, org, repo) {
    // The map to store the parallel steps in before executing them.
    def stepsForParallel = [:]

    // The standard 'for (String s: stringsToEcho)' syntax also doesn't work, so we
    // need to use old school 'for (int i = 0...)' style for loops.
    echo "updating protected status on ${org}/${repo}"
    for (int i = 0; i < branches.size(); i++) {
        // Get the actual string here.
        def s = branches.get(i)

        // Transform that into a step and add the step to the map as the value, with
        // a name for the parallel step as the key. Here, we'll just use something
        // like "echoing (string)"
        def stepName = "echoing ${s}"
    
        stepsForParallel[stepName] = {
            echo "setting protected status for ${s} branch"
            node('docker-cloud') {
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                  sh """curl -u ${env.USERNAME}:${env.PASSWORD} '${apiUrl}/repos/${org}/${repo}/branches/${s}' \
                    -XPATCH \
                    -H 'Content-Type: application/json' \
                    -H 'Accept: application/vnd.github.loki-preview+json' \
                    -d '{"protection":{"enabled": true,"required_status_checks": {"enforcement_level": "everyone","contexts": ["Jenkins"]}}}'
                  """
                }
            }
        }
    }

    // Actually run the steps in parallel - parallel takes a map as an argument,
    // hence the above.
    parallel stepsForParallel
}