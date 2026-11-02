
pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        timestamps()
    }

    environment {
        AWS_REGION = 'us-east-1'

        ECR_REPOSITORY_URI = '342940030457.dkr.ecr.us-east-1.amazonaws.com/employee-management'

        ECS_CLUSTER = 'employee-management-cluster'
        ECS_SERVICE = 'employee-management-service'
        ECS_TASK_DEFINITION_FAMILY = 'employee-management-task'
        CONTAINER_NAME = 'employee-management-container'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                echo 'Compiling Employee Management Application'
                sh 'mvn -B clean compile'
            }
        }

        stage('Unit Tests') {
            steps {
                echo 'Running JUnit and Mockito Unit Tests'
                sh 'mvn -B -Dtest=EmployeeServiceImplTest test'
            }
        }

        stage('Package Application') {
            steps {
                echo 'Packaging Spring Boot Application'
                sh 'mvn -B package -DskipTests'
            }
        }

        stage('Build Docker Image') {
            steps {
                echo 'Building Docker Image'

                sh '''
                    docker build \
                      -t employee-management:${BUILD_NUMBER} .
                '''
            }
        }

        stage('Verify AWS Identity') {
            steps {
                sh 'aws sts get-caller-identity'
            }
        }

        stage('Login to ECR') {
            steps {
                sh '''
                    aws ecr get-login-password --region ${AWS_REGION} |
                    docker login --username AWS --password-stdin ${ECR_REPOSITORY_URI%/*}
                '''
            }
        }

        stage('Push Image to ECR') {
            steps {
                sh '''
                    docker tag employee-management:${BUILD_NUMBER} \
                        ${ECR_REPOSITORY_URI}:${BUILD_NUMBER}

                    docker push ${ECR_REPOSITORY_URI}:${BUILD_NUMBER}
                '''
            }
        }

        stage('Register ECS Task Definition') {
            steps {
                sh '''
                    set -eu

                    echo "Retrieving current ECS task definition..."

                    CURRENT_TASK_DEFINITION=$(aws ecs describe-services \
                        --cluster ${ECS_CLUSTER} \
                        --services ${ECS_SERVICE} \
                        --region ${AWS_REGION} \
                        --query 'services[0].taskDefinition' \
                        --output text)

                    if [ -z "$CURRENT_TASK_DEFINITION" ] || \
                       [ "$CURRENT_TASK_DEFINITION" = "None" ]; then
                        echo "ERROR: Could not find current task definition."
                        exit 1
                    fi

                    echo "Current revision: $CURRENT_TASK_DEFINITION"

                    aws ecs describe-task-definition \
                        --task-definition "$CURRENT_TASK_DEFINITION" \
                        --region ${AWS_REGION} \
                        --query 'taskDefinition' \
                        --output json > task-definition.json

                    echo "Updating container image..."

                    jq --arg IMAGE "${ECR_REPOSITORY_URI}:${BUILD_NUMBER}" \
                       --arg CONTAINER "${CONTAINER_NAME}" '
                        if any(.containerDefinitions[]; .name == $CONTAINER)
                        then
                            del(
                                .taskDefinitionArn,
                                .revision,
                                .status,
                                .requiresAttributes,
                                .compatibilities,
                                .registeredAt,
                                .registeredBy,
                                .deregisteredAt
                            )
                            | .containerDefinitions |= map(
                                if .name == $CONTAINER
                                then .image = $IMAGE
                                else .
                                end
                            )
                        else
                            error("Expected container not found")
                        end
                    ' task-definition.json > task-definition-rendered.json

                    echo "Registering new ECS revision..."

                    aws ecs register-task-definition \
                        --cli-input-json file://task-definition-rendered.json \
                        --region ${AWS_REGION} \
                        --query 'taskDefinition.taskDefinitionArn' \
                        --output text > task-definition-arn.txt

                    echo "New task definition:"
                    cat task-definition-arn.txt
                '''
            }
        }

        stage('Deploy to ECS') {
            steps {
                sh '''
                    set -eu

                    NEW_TASK_DEFINITION=$(cat task-definition-arn.txt)

                    echo "Deploying ${NEW_TASK_DEFINITION}..."

                    aws ecs update-service \
                        --cluster ${ECS_CLUSTER} \
                        --service ${ECS_SERVICE} \
                        --task-definition "$NEW_TASK_DEFINITION" \
                        --region ${AWS_REGION}

                    echo "Monitoring ECS deployment..."

                    START_TIME=$(date +%s)
                    TIMEOUT_SECONDS=1200
                    POLL_INTERVAL=15

                    while true; do

                        SERVICE_JSON=$(aws ecs describe-services \
                            --cluster ${ECS_CLUSTER} \
                            --services ${ECS_SERVICE} \
                            --region ${AWS_REGION} \
                            --output json)

                        SERVICE_STATUS=$(echo "$SERVICE_JSON" | jq -r '.services[0].status')
                        DESIRED=$(echo "$SERVICE_JSON" | jq -r '.services[0].desiredCount')
                        RUNNING=$(echo "$SERVICE_JSON" | jq -r '.services[0].runningCount')
                        PENDING=$(echo "$SERVICE_JSON" | jq -r '.services[0].pendingCount')

                        PRIMARY_TASK=$(echo "$SERVICE_JSON" | jq -r \
                            '[.services[0].deployments[] | select(.status=="PRIMARY")][0].taskDefinition // "NONE"')

                        ROLLOUT_STATE=$(echo "$SERVICE_JSON" | jq -r \
                            '[.services[0].deployments[] | select(.status=="PRIMARY")][0].rolloutState // "UNKNOWN"')

                        ACTIVE_DEPLOYMENTS=$(echo "$SERVICE_JSON" | jq \
                            '[.services[0].deployments[] | select(.status=="PRIMARY" or .status=="ACTIVE")] | length')

                        echo "Service: ${SERVICE_STATUS}"
                        echo "Rollout: ${ROLLOUT_STATE}"
                        echo "Desired: ${DESIRED}, Running: ${RUNNING}, Pending: ${PENDING}"
                        echo "Active deployments: ${ACTIVE_DEPLOYMENTS}"

                        if [ "$SERVICE_STATUS" != "ACTIVE" ]; then
                            echo "ERROR: ECS service is not ACTIVE."
                            exit 1
                        fi

                        if [ "$ROLLOUT_STATE" = "FAILED" ]; then
                            echo "ERROR: ECS deployment failed."
                            exit 1
                        fi

                        if [ "$PRIMARY_TASK" != "$NEW_TASK_DEFINITION" ] && \
                           [ "$ROLLOUT_STATE" = "COMPLETED" ]; then
                            echo "ERROR: ECS rolled back to another task definition."
                            exit 1
                        fi

                        if [ "$PRIMARY_TASK" = "$NEW_TASK_DEFINITION" ] && \
                           [ "$ROLLOUT_STATE" = "COMPLETED" ] && \
                           [ "$RUNNING" -eq "$DESIRED" ] && \
                           [ "$PENDING" -eq 0 ] && \
                           [ "$ACTIVE_DEPLOYMENTS" -eq 1 ]; then

                            echo "SUCCESS: ECS deployment completed!"
                            break
                        fi

                        CURRENT_TIME=$(date +%s)
                        ELAPSED=$((CURRENT_TIME - START_TIME))

                        if [ "$ELAPSED" -ge "$TIMEOUT_SECONDS" ]; then
                            echo "ERROR: ECS deployment exceeded 20 minutes."
                            exit 1
                        fi

                        echo "Deployment in progress. Checking again in 15 seconds..."
                        sleep "$POLL_INTERVAL"

                    done

                    aws ecs describe-services \
                        --cluster ${ECS_CLUSTER} \
                        --services ${ECS_SERVICE} \
                        --region ${AWS_REGION} \
                        --query 'services[0].{Status:status,Desired:desiredCount,Running:runningCount,TaskDefinition:taskDefinition}' \
                        --output table
                '''
            }
        }
    }

    post {
        always {
            junit testResults: '**/target/surefire-reports/*.xml',
                  allowEmptyResults: true

            sh 'docker logout ${ECR_REPOSITORY_URI%/*} || true'
        }

        success {
            echo 'CI/CD pipeline completed! Unit tests passed and application deployed to ECS.'
        }

        failure {
            echo 'Pipeline failed. Check the console output and test reports.'
        }
    }
}
