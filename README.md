# deepcrawl-test

## Introduction

Deepcrawl Automation Hub is a Jenkins plugin that runs a build on Deepcrawl Automation Hub.

## Getting started

The plugin can be used by executing it as follows in your Jenkinsfile:

```yaml
pipeline {
    agent any

    // Can use environment variables for userKeyId and userKeySecret
    // environment {
    //     DEEPCRAWL_AUTOMATION_HUB_USER_KEY_ID = ''
    //     DEEPCRAWL_AUTOMATION_HUB_USER_KEY_SECRET = ''
    // }

    stages {
        stage('Hello') {
            steps {
                runAutomationHubBuild testSuiteId: '' userKeyId: '' userKeySecret: ''
            }
        }
    }
}
```

It can also be configured as a Build Step using the Jenkins GUI.

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)
