# Java Autoscaler
Simple Restfull Cloud Foundry Appplication Autoscaler written in Spring/Java  

### Prerequisites
1. Cloud Foundry installation
2. An AMQP service installed named **rabbit** (This can be changed by altering the **manifest.yml** under **services**)
3. A queue enabled on the AMPQ service

### Usage
1. Download/clone the project
2. Edit the **src/main/resources/application.properties** file to match your Cloud Foundry and queue information
3. Run the maven wrapper `$ ./mvn clean package`
4. Push the applicaiton to Cloud Foundry `$ cf push`

Once the application is running on Cloud Foundry it will issue scale commands for the application defined in the 
**application.properties** file based on the current size of the queue that is defined in the properites file. There are three 
restfull requests to use the autoscaler.

+ AUTOSCALER_HOSTNAME.CF_APP_DOMAIN/scaleup

   The autoscaler will check the depth of the queue. If that is larger than the 
current number of instances of the monitored application, it will scale up by the value defined in **application.properties** under `cf.scale`.  
+ AUTOSCALER_HOSTNAME.CF_APP_DOMAIN/scaledown

   The autoscaler will scaled the monitored application down to the default value defined in the **applicaiton.properties** file under `cf.scalemin`

+ AUTOSCALER_HOSTNAME.CF_APP_DOMAIN/stop

   The autoscaler will issue a stop command to the monitored application, shutting down all running instances. 
   This will **not** scale the application down. If resumed, the application will maintain it's previous number of instances.
