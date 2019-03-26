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

## Building the binaries
- Install the dependencies
- Build each component separately
#### Web Server
```
$ cd VideoSurveillanceCloudComputingProject
$ mvn package
```
This will generate a war which can be deploy using apache tomcat.

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
- Just you need to copy the war inside ```/webapps``` directory and then start tomcat server.

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
- This application accepts some command line arguments which are described below.
```
```
- This jar has few optional commandline parameters. The application can be using following command:
```
$ java -jar app.jar -requestQueueName <request-queue-name> \
-responseQueueName <response-queue-name> -outputBucket <bucket-name> \
-keyName <key-name> -iamInstanceRole <role-name> -clusterIdentifier <id> \
-binarySourceBucket <bucket-name>
```
- This command prints the IP of the web server at the end of execution. You can application at http://<ip-of-webserver>:8080/webTier/
