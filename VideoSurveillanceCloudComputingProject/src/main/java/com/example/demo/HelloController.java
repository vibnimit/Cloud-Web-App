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
	

	
	
    public void deleteRequest(String queueUrl, String receipt_handle, String msg) {
    	AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
    	DeleteMessageRequest dmr = new DeleteMessageRequest();
        dmr.withQueueUrl(queueUrl).withReceiptHandle(receipt_handle);
        sqs.deleteMessage(dmr);
        System.out.println("Deleted the message with Id : " + msg);
    }
    
//    @RequestMapping("/getIns")

    
       
}
