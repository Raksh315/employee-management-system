
pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        timestamps()
    }

    environment {
        AWS_REGION = 'us-east-1'
        ECR_REPOSITORY_URI = '342940030457.dkr.ecr.us-east-1.amazonaws.com/employee-management'
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
    }

    post {
        always {
            sh 'docker logout ${ECR_REPOSITORY_URI%/*} || true'
        }

        success {
            echo 'Pipeline completed successfully. Docker image pushed to ECR!'
        }

        failure {
            echo 'Pipeline failed. Check the console output.'
        }
    }
}
