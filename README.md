# workflowLibs
Pipeline Global Library for SA Jenkins demo environments. 

For more information on setting up and using Pipeline Global Libraries please see the documentation on GitHub: https://github.com/jenkinsci/workflow-cps-global-lib-plugin.

It is good practice to maintain your Pipeline Global Libraries in an external SCM, in addition to pushing to the Jenkins hosted workflowLibs Pipeline Global Library Git repoisitory. This also helps to manage sharing a Pipeline Global Library across multipe masters. Also, you could use a script such at [this one](https://github.com/cloudbees/jenkins-scripts/blob/master/pipeline-global-lib-init.groovy) to pull in externally managed Pipeline Global Libraries to the embedded Pipeline Global Library Git repository.

## Global Steps
#### mavenProject
Provides a template for maven builds. Additionally, it provides automated creation/updates of customized build images using `docker commit` to include caching all maven dependencies inside of the repo specific custom build image; dramatically speeding up build times.
###### configuration:
- `mavenProject`: provides simple config as Pipeline for maven based projects
  - org: GitHub organization or user repo is under
  - repo: GitHub repository being built
  - hipChatRoom: id or name of HipChat room to send build messages
  - jdk: version of JDK to use for build as string value
  - maven: version of Maven to use for build as string value
  - rebuildBuildImage: boolean that controls whether or not to refresh existing repo specific Docker build image based on the `maven' image
  - protectedBranches: allows to specify name(s) of branch(es) to protected and use Jenkins to control status, uses the `githubProtrectBranch` step documented below

######*Example:*
```groovy
	mavenProject {
		org = 'sa-team'
		repo = 'todo-api'
		hipChatRoom = '1613593'
		jdk = '8'
		maven = '3.3.3'
		rebuildBuildImage = true
		protectedBranches = ['master']
	}
```
#### githubProtectBranch
Uses the GitHub API to set up protected branches on repository being built.
###### configuration:
- `githubProtectBranch`: sets protection status of rep branch(es)
  - branches: list of strings specifying branches to set protected status on
  - API URL: GitHub API URL to use
  - Credentials ID: ID of GitHub username/password credentials set to use from Jenkins
  - org: org/user of repo - for example sa-demo in `sa-demo/todo-api`
  - repo: name of repo of branch

###### *Example:*
```groovy
githubProtectBranch(['master','feature-one'],
  'https://github.enterprise.com/api/v3',
  '3ebff2f8-1013-42ff-a1e4-6d74e99f4ca1',
  'sa-team',
  'todo-api')
```
