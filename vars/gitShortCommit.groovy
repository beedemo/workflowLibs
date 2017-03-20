//will set an environmental variable to short commit, must of git repo in workspace
def call(size) {
    size = size ?: 7
    git_commit = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    short_commit=git_commit.take(size)
    env.SHORT_COMMIT = short_commit
}