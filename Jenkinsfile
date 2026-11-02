
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
                echo 'Building Employee Management Application'
                sh 'mvn -B clean package -DskipTests'
            }
        }

        stage('Build Docker Image') {
            steps {
                echo 'Building Docker Image'
                sh 'docker build -t employee-management:${BUILD_NUMBER} .'
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
                    docker tag employee-management:${BUILD_NUMBER} ${ECR_REPOSITORY_URI}:${BUILD_NUMBER}
                    docker push ${ECR_REPOSITORY_URI}:${BUILD_NUMBER}
                '''
            }
        }

        stage('Register ECS Task Definition') {
            steps {
                sh '''
                    set -e

                    echo "Getting current ECS task definition..."

                    CURRENT_TASK_DEFINITION=$(aws ecs describe-services \
                        --cluster ${ECS_CLUSTER} \
                        --services ${ECS_SERVICE} \
                        --region ${AWS_REGION} \
                        --query 'services[0].taskDefinition' \
                        --output text)

                    echo "Current task definition: ${CURRENT_TASK_DEFINITION}"

                    aws ecs describe-task-definition \
                        --task-definition "$CURRENT_TASK_DEFINITION" \
                        --region ${AWS_REGION} \
                        --query 'taskDefinition' \
                        --output json > task-definition.json

                    echo "Updating Docker image in task definition..."

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

                    echo "Registering new ECS task definition revision..."

                    aws ecs register-task-definition \
                        --cli-input-json file://task-definition-rendered.json \
                        --region ${AWS_REGION} \
                        --query 'taskDefinition.taskDefinitionArn' \
                        --output text > task-definition-arn.txt

                    echo "Registered task definition:"
                    cat task-definition-arn.txt
                '''
            }
        }

        stage('Deploy to ECS') {
            steps {
                sh '''
                    set -e

                    NEW_TASK_DEFINITION=$(cat task-definition-arn.txt)

                    echo "Deploying ${NEW_TASK_DEFINITION}..."

                    aws ecs update-service \
                        --cluster ${ECS_CLUSTER} \
                        --service ${ECS_SERVICE} \
                        --task-definition "$NEW_TASK_DEFINITION" \
                        --region ${AWS_REGION}

                    echo "Waiting for ECS service to become stable..."

                    aws ecs wait services-stable \
                        --cluster ${ECS_CLUSTER} \
                        --services ${ECS_SERVICE} \
                        --region ${AWS_REGION}

                    echo "ECS deployment completed!"

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
            sh 'docker logout ${ECR_REPOSITORY_URI%/*} || true'
        }

        success {
            echo 'CI/CD pipeline completed! Application deployed to ECS.'
        }

        failure {
            echo 'Pipeline failed. Check the console output.'
        }
    }
}
