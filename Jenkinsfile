def DOCKER_IMAGE_SHA = ''

pipeline {
  agent any

  options {
    buildDiscarder(logRotator(numToKeepStr: '20'))
    disableConcurrentBuilds(abortPrevious: true)
    timeout(time: 30, unit: 'MINUTES')
    timestamps()
  }

  environment {
    IMAGE_NAME = "federation-search-aggregator"
    REGISTRY = "docker.madgik.di.uoa.gr"
    REGISTRY_CRED = 'docker-registry'
    DOCKER_TAG = ''
  }

  stages {
    stage('Determine Docker Tag') {
      steps {
        script {
          DOCKER_TAG = sh(script: "./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout", returnStdout: true).trim()
          echo "Docker tag: ${DOCKER_TAG}"
          currentBuild.displayName = "${currentBuild.displayName}-${DOCKER_TAG}"
        }
      }
    }

    stage('Test and Build Image') {
      parallel {

        stage('Test') {
          when { expression { return env.TAG_NAME == null } }
          steps {
            catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
              sh './mvnw -B verify'
            }
          }
          post {
            always {
              junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml, **/target/failsafe-reports/TEST-*.xml'
              recordCoverage(
                tools: [[parser: 'JACOCO', pattern: '**/target/site/jacoco/jacoco.xml, **/target/site/jacoco-it/jacoco.xml']],
                sourceDirectories: [[path: 'src/main/java']]
              )
            }
          }
        }

        stage('Dependency Check') {
          steps {
            catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
              withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
                sh './mvnw -B dependency-check:check -DnvdApiKey=$NVD_API_KEY'
              }
            }
          }
          post {
            always {
              archiveArtifacts allowEmptyArchive: true, artifacts: '**/dependency-check-report.*'
              dependencyCheckPublisher(
                pattern: '**/dependency-check-report.xml',
                unstableTotalCritical: 1,
                unstableTotalHigh: 3
              )
            }
          }
        }

        stage('Build Image') {
          when {
            expression {
              return env.TAG_NAME != null || env.BRANCH_NAME == 'develop' || env.BRANCH_NAME == 'main'
            }
          }
          steps {
            script {
              sh "./mvnw -pl search-aggregator-service -am spring-boot:build-image -DskipTests"
              DOCKER_IMAGE_SHA = sh(script: "docker inspect --format='{{.Id}}' ${REGISTRY}/${IMAGE_NAME}:${DOCKER_TAG} 2>/dev/null || true", returnStdout: true).trim()
            }
          }
        }

      }
    }

    stage('Deploy Artifacts') {
      when {
        anyOf {
          expression { return DOCKER_TAG.endsWith('-SNAPSHOT') } // deploy all snapshots
          expression { return env.TAG_NAME != null } // deploy only tag build as release
        }
      }
      steps {
        sh './mvnw deploy -DskipTests'
      }
    }

    stage('Upload Image') {
      when {
        expression {
          return env.TAG_NAME != null || env.BRANCH_NAME == 'develop' || env.BRANCH_NAME == 'main'
        }
      }
      steps {
        script {
          def primaryImage = "${REGISTRY}/${IMAGE_NAME}:${DOCKER_TAG}"
          withCredentials([usernamePassword(credentialsId: "${REGISTRY_CRED}", usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
            sh """
              echo "\$DOCKER_PASS" | docker login ${REGISTRY} -u "\$DOCKER_USER" --password-stdin
              docker push ${primaryImage}
            """
            if (env.TAG_NAME) {
              def minorTag = DOCKER_TAG.tokenize('.').take(2).join('.')
              def minorImage = "${REGISTRY}/${IMAGE_NAME}:${minorTag}"
              def latestImage = "${REGISTRY}/${IMAGE_NAME}:latest"
              sh """
                docker tag ${primaryImage} ${minorImage}
                docker push ${minorImage}
                docker tag ${primaryImage} ${latestImage}
                docker push ${latestImage}
              """
            } else if (env.BRANCH_NAME == 'develop') {
              def devImage = "${REGISTRY}/${IMAGE_NAME}:dev"
              sh """
                docker tag ${primaryImage} ${devImage}
                docker push ${devImage}
              """
            }
          }
        }
      }
    }

    stage('Handle Releases') {
      when {
        allOf {
          branch 'main'
          not { changeRequest() } // skip PR builds
        }
      }
      steps {
        lock(resource: "release-${IMAGE_NAME}") {
          retry(5) {
            script {
              try {
                withCredentials([string(credentialsId: 'jenkins-github-pat', variable: 'GH_TOKEN')]) {
                  sh '''
                    [ -f /etc/profile.d/load_nvm.sh ] || { echo "ERROR: /etc/profile.d/load_nvm.sh not found. NVM is required on this agent."; exit 1; }
                    . /etc/profile.d/load_nvm.sh
                    nvm install --lts
                    npx release-please@17 github-release --repo-url ${GIT_URL} --token ${GH_TOKEN}

                    npx release-please@17 release-pr --repo-url ${GIT_URL} --token ${GH_TOKEN}
                  '''
                }
              } catch (e) {
                sleep time: 45, unit: 'SECONDS'
                throw e
              }
            }
          }
        }
      }
    }

  }

  post {
    always {
      script {
        if (DOCKER_IMAGE_SHA) {
          sh "docker rmi -f ${DOCKER_IMAGE_SHA} || true"
        }
      }
    }
    failure {
      emailext(
        subject: "FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
        body: """<p>Build <b>${env.JOB_NAME} #${env.BUILD_NUMBER}</b> failed.</p>
                 <p>Branch: <b>${env.BRANCH_NAME}</b></p>
                 <p>Check the details: <a href="${env.BUILD_URL}">${env.BUILD_URL}</a></p>""",
        mimeType: 'text/html',
        recipientProviders: [[$class: 'DevelopersRecipientProvider']],
        to: '$DEFAULT_RECIPIENTS'
      )
    }
    fixed {
      emailext(
        subject: "FIXED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
        body: """<p>Build <b>${env.JOB_NAME} #${env.BUILD_NUMBER}</b> is back to normal.</p>
                 <p>Branch: <b>${env.BRANCH_NAME}</b></p>
                 <p>Check the details: <a href="${env.BUILD_URL}">${env.BUILD_URL}</a></p>""",
        mimeType: 'text/html',
        recipientProviders: [[$class: 'DevelopersRecipientProvider']],
        to: '$DEFAULT_RECIPIENTS'
      )
    }
  }
}
