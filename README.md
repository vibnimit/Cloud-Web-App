# Cloud-Web-App
Creating a cloud web application to recognize video from a raspberry Pi cluster

## Components
- VideoSurveillanceCloudComputingProject: Springboot based web application to trigger user requests
- app-tier: Java based application for object detection
- autoscaling_controller: Java based applicaiton to auto scale the app server instances
- infrastructureBootstrap: Java code for creating entire infrastructure from scratch

## Dependencies
- Java 8
- Maven build system
- Various Java libraries (Maven will manage the libraries)

## Testing the application
1. Current running stack
    1. We have one web server running. You can send request to it at http://13.57.252.180:8080/webTier/recognizeObject
    2. An IAM role is created for TA, and details are shared over the mail. Also the access key and secret key can be found in a csv file inside the zip archive.
2. Creating new stack
    1. Configure AWS credentials at the machine which you are using to create the stack (Shared over email or you can use your AWS own account).
    2. Create new stack using infrastructure bootstrap utility listed at the end of README

## Building the binaries
- Install the dependencies
- Build each component separately
#### Web Server
```
$ cd VideoSurveillanceCloudComputingProject
$ mvn package
```
This will generate a ".war" which can be deployed using apache tomcat.

#### App Server
```
$ cd app-tier
$ mvn package assembly:single
```
This command will generate a single jar file which can be run on app server.

#### Autoscaling controller
```
$ cd autoscaling_controller
$ mvn package assembly:single
```
This command will generate a single jar file which can be run on any machine that has JRE installed.

#### Infrastructure Bootstrap
```
$ cd infrastructureBootstrap
$ mvn package assembly:single
```
This command will generate a single jar file which can be run on any machine that has JRE installed.

## Running the applications
- Make sure that you have built the code to generate application binaries.

#### Web Server
- As we are generating a war file, it can be run using apache tomcat.
- You need to copy the war inside ```/webapps``` directory and then start tomcat server.
- This application reads from a properties file. Create properties file in <TOMCAT_HOME>/webapps/<APP_NAME>/WEB_INF/classes/application.properties
```
request.queue.url=<url-for-request-queue>
response.queue.url=<>url-for-response-queue>
```
- After creating this file restart the tomcat service.

#### App Server
- The build step mentioned above would generate a single jar file which can be directly run.
- This application accepts some command line arguments which are described below.
```
usage: Bootstrap
 -outputBucket <arg>       Bucket name for storing darknet output
 -requestQueueUrl <arg>    Request queue URL
 -responseQueueUrl <arg>   Response queue URL
```
- The application can be using following command:
```
$ java -jar app.jar -requestQueueUrl <request-queue-url> -responseQueueUrl <response-queue-url> -outputBucket <bucket-name>
```
#### Autoscaling controller
- The build step mentioned above would generate a single jar file which can be directly run.
- This application accepts some command line arguments which are described below.
```
usage: autoscalingController
 -allowedInstances <arg>     Number of allowed app instances (Default: 19)
 -appAMIID <arg>             AMI ID for appserver instance (Default:
                             ami-0e355297545de2f82)
 -appInstanceType <arg>      Instance type for appserver instance
                             (Default: t2.micro)
 -appSecurityGroups <arg>    Comma seperated list of security groups for
                             appserver instance (Default: default)
 -appSubnetID <arg>          Subnet ID for appserver instance (Default:
                             one of the default subnet in region)
 -binarySourceBucket <arg>   Bucket name in which application binary are
                             stored
 -clusterIdentifier <arg>    Unique cluster identifier used to tag all the
                             resources created under this process
 -iamInstanceRole <arg>      Unique cluster identifier used to tag all the
                             resources created under this process
 -keyName <arg>              Key name for server instance
 -outputBucket <arg>         Bucket name for storing darknet output
 -requestQueueUrl <arg>      Request queue URL
 -responseQueueUrl <arg>     Response queue URL
```
- This jar has few optional commandline parameters. The application can be using following command:
```
$ java -jar app.jar -requestQueueUrl <request-queue-url> \
-responseQueueUrl <response-queue-url> -outputBucket <bucket-name> \
-keyName <key-name> -iamInstanceRole <role-name> -clusterIdentifier <id> \
-binarySourceBucket <bucket-name>
```

#### Infrastructure Bootstrap
- Make sure you have generated binaries for all of the above applications and pushed them to S3 bucket.
- The build step mentioned above would generate a single jar file which can be directly run.
- This jar has few optional commandline parameters. The application can be using following command:
```
$ java -jar app.jar -requestQueueName <request-queue-name> \
-responseQueueName <response-queue-name> -outputBucket <bucket-name> \
-keyName <key-name> -iamInstanceRole <role-name> -clusterIdentifier <id> \
-binarySourceBucket <bucket-name>
```
- This command prints the IP of the web server at the end of execution. You can access the UI for application at http://<ip-of-webserver>:8080/webTier/
