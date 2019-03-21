package com.example.demo;

import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.web.bind.annotation.RequestMapping;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;

import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.AmazonEC2;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.lang.*;
import com.amazonaws.services.ec2.*;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;


@RestController
public class HelloController extends Thread{
	
	final static int total_instances_allowed = 19;
	HashMap<Long, String> requestResult = new HashMap<Long, String>();
	public HelloController(HashMap<Long, String> requestResult) {
		// TODO Auto-generated constructor stub
		this.requestResult = requestResult;
	}
	
	
    public HelloController() {
		// TODO Auto-generated constructor stub
	}


	@RequestMapping("/vibhu")
    public String index() {
        return "Greetings from Spring Boot! You have successfully hit the EC2 instance";
    }
    
    @RequestMapping("/recognizeObject")
    public String handleRequests() {
    	String result = "";
    	String id = "shdgbsdjbiuasndkjhfudsb";
    	HelloController worker = new HelloController(requestResult);
//    	requestResult.put(worker.getId(), "");
//    	new Thread(worker);
    	worker.start();
    	
    	try {
			worker.join();
			long wid = worker.getId();
			result = requestResult.get(wid);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			result = "Error Occured in thread";
		}
   	   	
    	return result;
    }
    
    public String registerUserRequest(String message) {
    	AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
    	String queue_url = "https://sqs.us-west-1.amazonaws.com/411110494130/RequestQueue";
    	
    	Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    	String requestId = "request_"+timestamp.getTime();
    	SendMessageRequest send_msg_request = new SendMessageRequest()
    	        .withQueueUrl(queue_url)
    	        .withMessageBody(message)
    	        .withDelaySeconds(0);
    	return sqs.sendMessage(send_msg_request).toString();
    }
    
    @RequestMapping("/getSize")
    public String readMessageTest() {
    	AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
    	String queue_url = "https://sqs.us-west-1.amazonaws.com/411110494130/ResponseQueue";
    	GetQueueAttributesRequest sqsRequest = new GetQueueAttributesRequest(queue_url);

    	sqsRequest.setAttributeNames(Arrays.asList("ApproximateNumberOfMessages"));;
		GetQueueAttributesResult result = sqs.getQueueAttributes(sqsRequest);
		String qCount = result.getAttributes().get("ApproximateNumberOfMessages");

		int q_Count = Integer.parseInt(qCount);

    	return qCount;	
    }
	
    @RequestMapping("/readMessage")
    public String readMessageQ() {
    	String queue_url = "https://sqs.us-west-1.amazonaws.com/411110494130/ResponseQueue";
    	AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
//    	String queue_url = "https://sqs.us-west-1.amazonaws.com/411110494130/RequestQueue";
    	final ReceiveMessageRequest receiveMessageRequest =
                new ReceiveMessageRequest(queue_url);    	
//    	while()
    	List<Message> messages = sqs.receiveMessage(receiveMessageRequest)
                .getMessages();
    	String messages_received = "";
    	List<String> response = new ArrayList<String>();
//    	while(messages.size()!=0) {
//    		messages_received = "";
    	
    	String receipt_handle = "";
    	for(Message message: messages) {
    		messages_received += message.getBody()+"\n";
    		response.add(message.getBody());
    		response.add(message.getReceiptHandle());
    		receipt_handle = message.getReceiptHandle();
    	}
    	
//    	System.out.println("Size "+messages.size());
    	int timeout = 30;
    	sqs.changeMessageVisibility(queue_url, receipt_handle, timeout);
    	
    	return response.get(0);
    }
    
    public List<String> readMessage(String queue_url) {
    	AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
//    	String queue_url = "https://sqs.us-west-1.amazonaws.com/411110494130/RequestQueue";
    	final ReceiveMessageRequest receiveMessageRequest =
                new ReceiveMessageRequest(queue_url);    	
//    	while()
    	List<Message> messages = sqs.receiveMessage(receiveMessageRequest)
                .getMessages();
    	String messages_received = "";
    	List<String> response = new ArrayList<String>();
//    	while(messages.size()!=0) {
//    		messages_received = "";
	    	for(Message message: messages) {
	    		messages_received += message.getBody()+"\n";
	    		response.add(message.getBody());
	    		response.add(message.getReceiptHandle());
	    	}

    	return response;
    	
    	
    }
    
    int getNumberOfMessagesInQueue(String queue_url) {
//    	AmazonSQS sqs = AWSClientFactory.CreateAmazonSQSClient();
    	AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
    	GetQueueAttributesRequest sqsRequest = new GetQueueAttributesRequest(queue_url);
//    	int qCount = 0;
//    	GetQueueAttributesRequest gqar = new GetQueueAttributesRequest();
    	sqsRequest.setAttributeNames(Arrays.asList("ApproximateNumberOfMessages"));;
		GetQueueAttributesResult result = sqs.getQueueAttributes(sqsRequest);
		String qCount = result.getAttributes().get("ApproximateNumberOfMessages");
//    	GetQueueAttributesResult sqsResponse = sqs.getQueueAttributes(queue_url, );
		int q_Count = Integer.parseInt(qCount);
//    	qCount = sqsResponse.Approximate;
//    	System.out.println("total Size "+qCount);
    	return q_Count;
    }
    
    
    public String createInstanceWithUserData() {
    	String str = "";
    	List<String> sec_grp = new ArrayList<String>();
    	sec_grp.add("allow-ssh");
    	final AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard()
				.withRegion(Regions.US_WEST_1)
		        .build();
    	
		System.out.println("create an Instance");
		String imageId = "ami-0e355297545de2f82";
		int min =1;
		int max =1;
		IamInstanceProfileSpecification iam = new IamInstanceProfileSpecification().withName("ProjectInstanceRole");
		RunInstancesRequest request = new RunInstancesRequest(imageId, min, max);
		request.setInstanceType("t2.micro");
		request.setUserData(getUserDataScript());
		request.setKeyName("cc_trio");
		request.setSecurityGroups(sec_grp);
		request.setIamInstanceProfile(iam);
//		DescribeInstancesRequest request = new DescribeInstancesRequest();	
//        RunInstancesRequest rir = new RunInstancesRequest(imageId,
//                min, max);
//        rir.setInstanceType("t2.micro"); //set instance type
//		ec2.startInstances(startInstancesRequest)
        
		RunInstancesResult result = ec2.runInstances(request);
        Instance resultInstance = result.getReservation().getInstances().get(0);
//		RunInstancesResult res = ec2.runInstances(request);
//		List<Instance> results = res.getReservation().getInstances();
//		for(Instance instance:resultInstance) {
		System.out.println("New Instance has been created "+resultInstance.getInstanceId());
		str = resultInstance.getInstanceId();
		CreateTagsRequest createTagsRequest = new CreateTagsRequest()
                .withResources(resultInstance.getInstanceId())
                .withTags(new Tag("Stack", "1"));
        ec2.createTags(createTagsRequest);
		
		resultInstance = result.getReservation().getInstances().get(0);
		DescribeInstanceStatusRequest describeInstanceRequest = new DescribeInstanceStatusRequest().withInstanceIds(resultInstance.getInstanceId());
        DescribeInstanceStatusResult describeInstanceResult = ec2.describeInstanceStatus(describeInstanceRequest);
        
        List<InstanceStatus> state = describeInstanceResult.getInstanceStatuses();
        
		while(true) {
//			System.out.println("Status: "+resultInstance.getState().getName());
//			count++;
//			try {
////				Thread.sleep(30000);
//				Thread.sleep(500);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			
//            System.out.println("state "+state);
            if(!state.isEmpty()) {
//            	System.out.println("statussssssssss: "+state.get(0).getInstanceStatus().getStatus());
            	if(state.get(0).getInstanceState().getCode() == 0 || state.get(0).getInstanceState().getCode() == 16 ) {
//            		System.out.println("");
            		break;
            	}
            	System.out.println("s "+state.get(0).getInstanceState().getCode());
            }
            describeInstanceResult = ec2.describeInstanceStatus(describeInstanceRequest);
            state = describeInstanceResult.getInstanceStatuses();

		}
		 
		
//		}
		
    	return str;
    }
    
    private static String getUserDataScript(){
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("#! /bin/bash");
//        lines.add("curl http://www.google.com > google.html");
        lines.add("add-apt-repository -y ppa:webupd8team/java");
        lines.add("echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections");
        lines.add("apt update && apt install -y oracle-java8-installer");
        lines.add("apt-get install -y awscli xvfb");
        lines.add("aws s3 cp s3://cse-546-app-repository/app.jar /home/ubuntu/app.jar");
        lines.add("curl https://pjreddie.com/media/files/yolov3-tiny.weights -o /home/ubuntu/darknet/yolov3-tiny.weights");
        lines.add("java -jar /home/ubuntu/app.jar");
        
//        lines.add("shutdown -h 0");
        String str = new String(Base64.encodeBase64(join(lines, "\n").getBytes()));
        return str;
    }
    
    
    static String join(Collection<String> s, String delimiter) {
        StringBuilder builder = new StringBuilder();
        Iterator<String> iter = s.iterator();
        while (iter.hasNext()) {
            builder.append(iter.next());
            if (!iter.hasNext()) {
                break;
            }
            builder.append(delimiter);
        }
        return builder.toString();
    }

    
	@Override
	public void run() {
		// TODO Auto-generated method stub
		UUID uuid = UUID.randomUUID();
		Long threadId = Thread.currentThread().getId();
		System.out.println("thread: "+threadId);
        String randomUUIDString = uuid.toString();
        String responseQueueUrl = "https://sqs.us-west-1.amazonaws.com/411110494130/ResponseQueue";
		registerUserRequest(threadId+"_"+randomUUIDString);
		String my_id = threadId+"_"+randomUUIDString;
		
		checkAndCreateInstance();
		
//		
		try {
			Thread.sleep(20000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		int numOfResponsesGenerated = getNumberOfMessagesInQueue(responseQueueUrl);
		List<String> msg = new ArrayList<String>();
		while(true) {
			System.out.println(" response found= "+numOfResponsesGenerated);
			int flag = 0;
			if(numOfResponsesGenerated == 0) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			else {
				msg = readMessage(responseQueueUrl);
				for(int i = 0; i< numOfResponsesGenerated; i++) {
					if(!msg.isEmpty() && isMyResponse(my_id, msg.get(0))) { //Improve here**************
						flag = 1;
						break;
					}
					msg = readMessage(responseQueueUrl);
				}
			}
			if(flag == 1) {
				break;
			}
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//			if(!msg.isEmpty() && msg.get(0).contains(threadId.toString())) {
//				break;
//			}
			numOfResponsesGenerated = getNumberOfMessagesInQueue(responseQueueUrl);
//			msg = readMessage(responseQueueUrl);
		}
		String[] decrypt_response = msg.get(0).split(" %% ");
//		System.out.println("Response after decrypting "+decrypt_response[0]+"  "+decrypt_response[1]);	
		requestResult.put(threadId, decrypt_response[1]);
		deleteRequest(responseQueueUrl, msg.get(1), msg.get(0));
	}
	
	Boolean isMyResponse(String threadUniqueId, String response) {
		Boolean result = false;
		String[] decrypt_response = response.split(" %% ");
		System.out.println("Response after decrypting "+decrypt_response[0]+"  "+decrypt_response[1]);
		if(threadUniqueId.equals(decrypt_response[0]))
			result = true;
		return result;	
	}
	
	public synchronized static void checkAndCreateInstance() {
		System.out.println(Thread.currentThread().getId()+" Entered the check&createInstance block ");
		
		int num_of_instances = AppTierRunningIns();
		System.out.println("num of intances running: "+num_of_instances);
		HelloController obj = new HelloController();
		if(num_of_instances == 0) {
			
			System.out.println("First App Instance creating...");
			
			obj.createInstanceWithUserData();
		}
		else {
			if(num_of_instances < total_instances_allowed) {
//				try {
//					Thread.sleep(3000);
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
				int num_of_messages_in_request_queue = 
						obj.getNumberOfMessagesInQueue("https://sqs.us-west-1.amazonaws.com/411110494130/RequestQueue");
				System.out.println("num of messages in request queue= "+num_of_messages_in_request_queue);
				if(num_of_messages_in_request_queue > 0) {
					obj.createInstanceWithUserData();
				}
			}
			else {
				System.out.println("Max instance created "+Thread.currentThread().getId());
			}
			System.out.println(Thread.currentThread().getId()+" Exiting the check&createInstance block ");
		}
	}
	
	
    public void deleteRequest(String queueUrl, String receipt_handle, String msg) {
    	AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
    	DeleteMessageRequest dmr = new DeleteMessageRequest();
        dmr.withQueueUrl(queueUrl).withReceiptHandle(receipt_handle);
        sqs.deleteMessage(dmr);
        System.out.println("Deleted the message with Id : " + msg);
    }
    
//    @RequestMapping("/getIns")
    public synchronized static int AppTierRunningIns() {
    	//just to make the threads a bit slower then each other
	    final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
	    String imageId = "ami-0e355297545de2f82"; 
	    int minInstanceCount = 1; //create 1 instance
	    int maxInstanceCount = 1;
	    DescribeInstancesRequest request = new DescribeInstancesRequest();	
	    RunInstancesRequest rir = new RunInstancesRequest(imageId,
	                minInstanceCount, maxInstanceCount);
	    List<String> valuesT1 = new ArrayList<String>();
	    valuesT1.add("1");
	    System.out.println("List of instances:");
	    Filter filter1 = new Filter("tag:Stack", valuesT1);
	    int num_instances = 0;
	    DescribeInstancesResult res = ec2.describeInstances(request.withFilters(filter1));
	    List<Reservation> reservations = res.getReservations();
//	    System.out.println("Reservations: "+reservations);
	    try {
	    for (Reservation reservation : reservations) {
	    	try {
	    		List<Instance> instances = reservation.getInstances();
	    		for (Instance instance : instances) {
	//    		System.out.println(instance.getInstanceId());
	    		DescribeInstanceStatusRequest describeInstanceRequest = new DescribeInstanceStatusRequest().withInstanceIds(instance.getInstanceId());
		            DescribeInstanceStatusResult describeInstanceResult = ec2.describeInstanceStatus(describeInstanceRequest);
		            List<InstanceStatus> state = describeInstanceResult.getInstanceStatuses();
//		            System.out.println("statussssssssss: "+state.get(0).getInstanceStatus());
		            if( state.get(0).getInstanceState().getCode() == 0 || state.get(0).getInstanceState().getCode() == 16  ) {
//		            	System.out.println(instance.getInstanceId());
		            	num_instances++;
//		            	System.out.println("Instances= "+num_instances);
		            }
		            else {}
	    		}}
	    		catch(IndexOutOfBoundsException exception) {
	//    			System.out.println("here");
	    			continue;}}
	    	}
	    		catch(AmazonEC2Exception exception) {
	    			System.out.println("No instances running");	
	    		}
    		return num_instances;	
    	}
    
       
}
