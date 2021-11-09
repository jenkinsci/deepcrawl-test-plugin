# Deepcrawl Automation Hub Jenkins Plugin

## Introduction

Deepcrawl Automation Hub is a Jenkins plugin that runs a build on Deepcrawl Automation Hub.

## Getting started

The plugin can be used by executing it as follows in your Jenkinsfile:

```yaml
pipeline {
    agent any

    environment {
        DEEPCRAWL_AUTOMATION_HUB_USER_KEY_ID = ''
        DEEPCRAWL_AUTOMATION_HUB_USER_KEY_SECRET = credentials('automation-hub-user-key-secret')
    }

    stages {
        stage('Hello') {
            steps {
                runAutomationHubBuild testSuiteId: ''
            }
        }
    }
}
```

Note: You need to create a secret text named 'automation-hub-user-key-secret' using the Credentials Plugin in Jenkins.

It can also be configured as a Build Step using the Jenkins GUI.

**For other ways to trigger an Automation Hub Build please see: https://github.com/deepcrawl/deepcrawl-test**

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)
