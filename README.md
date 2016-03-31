# workflowLibs
Global Pipeline Libraries for SA Jenkins demo environments.

##Steps
####mavenProject
- `mavenProject`: provides simple config as Pipeline for maven based projects
  - org: GitHub organization or user repo is under
  - repo: GitHub repository being built
  - hipChatRoom: id or name of HipChat room to send build messages
  - jdk: version of JDK to use for build as string value
  - maven: version of Maven to use for build as string value
  - rebuildBuildImage: boolean that controls whether or not to refresh existing repo specific Docker build image based on the `maven' image
  - protectedBranches: allows to specify name(s) of branch(es) to protected and use Jenkins to control status, uses the `githubProtrectBranch` step documented below
  
*Example:*
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
####githubProtectBranch
- `githubProtectBranch`: sets protection status of rep branch(es)
  - branches: list of strings specifying branches to set protected status on
  - API URL: GitHub API URL to use
  - Credentials ID: ID of GitHub username/password credentials set to use from Jenkins
  - org: org/user of repo - for example sa-demo in `sa-demo/todo-api`
  - repo: name of repo of branch

*Example:*
```
githubProtectBranch(['master','feature-one'],
'https://github.enterprise.com/api/v3',
'3ebff2f8-1013-42ff-a1e4-6d74e99f4ca1',
'sa-team',
'todo-api')
```
