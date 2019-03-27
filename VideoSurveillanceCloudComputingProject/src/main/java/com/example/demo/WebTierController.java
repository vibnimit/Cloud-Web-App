package com.example.demo;

import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;


@RestController
public class WebTierController extends Thread{
	
	@Value("${request.queue.url}")
	private String requestQueueUrl;

	@Value("${response.queue.url}")
	private String responseQueueUrl;
	
	HashMap<Long, String> requestResult = new HashMap<Long, String>();
	HashMap<String, String> responsesReceived = new HashMap<String, String>();
	
	public WebTierController(HashMap<Long, String> requestResult, HashMap<String, String> responsesReceived, String reqQueue, String respQueue) {
		this.requestResult = requestResult;
		this.responsesReceived = responsesReceived;
		this.requestQueueUrl = reqQueue;
		this.responseQueueUrl = respQueue;
	}
		
    public WebTierController() {
	}


	@RequestMapping("/vibhu")
    public String index() {
        return "Greetings from Spring Boot! You have successfully hit the EC2 instance";
    }
    
    @RequestMapping("/recognizeObject")
    public String handleRequests() {
    	String result = "";
    	WebTierController worker = new WebTierController(requestResult, responsesReceived, requestQueueUrl, responseQueueUrl);
    	worker.start();
    	
    	try {
    		
			worker.join();
			long wid = worker.getId();
			result = requestResult.get(wid);
		} catch (InterruptedException e) {
			e.printStackTrace();
			result = "Error Occured in thread";
		}
   	   	
    	return result;
    }
    
    public String registerUserRequest(String message) {
    	AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
    	String queue_url = requestQueueUrl;
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
    	String queue_url = responseQueueUrl;
    	GetQueueAttributesRequest sqsRequest = new GetQueueAttributesRequest(queue_url);

    	sqsRequest.setAttributeNames(Arrays.asList("ApproximateNumberOfMessages"));;
		GetQueueAttributesResult result = sqs.getQueueAttributes(sqsRequest);
		String qCount = result.getAttributes().get("ApproximateNumberOfMessages");

		int q_Count = Integer.parseInt(qCount);

    	return qCount;	
    }
	
    @RequestMapping("/readMessage")
    public String readMessageQ() {
    	String queue_url = responseQueueUrl;
    	AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
    	final ReceiveMessageRequest receiveMessageRequest =
                new ReceiveMessageRequest(queue_url);    	
    	List<Message> messages = sqs.receiveMessage(receiveMessageRequest)
                .getMessages();
    	List<String> response = new ArrayList<String>();
    	
    	String receipt_handle = "";
    	for(Message message: messages) {
    		response.add(message.getBody());
    		response.add(message.getReceiptHandle());
    		receipt_handle = message.getReceiptHandle();
    	}
    	
    	int timeout = 30;
    	sqs.changeMessageVisibility(queue_url, receipt_handle, timeout);
    	
    	return response.get(0);
    }
    
    public List<String> readMessage(String queue_url) {
    	AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
    	final ReceiveMessageRequest receiveMessageRequest =
                new ReceiveMessageRequest(queue_url);    	
    	List<Message> messages = sqs.receiveMessage(receiveMessageRequest)
                .getMessages();
    	String messages_received = "";
    	List<String> response = new ArrayList<String>();
	    	for(Message message: messages) {
	    		messages_received += message.getBody()+"\n";
	    		response.add(message.getBody());
	    		response.add(message.getReceiptHandle());
	    	}

    	return response;
    	
    	
    }
    
    int getNumberOfMessagesInQueue(String queue_url) {
    	AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
    	GetQueueAttributesRequest sqsRequest = new GetQueueAttributesRequest(queue_url);
    	sqsRequest.setAttributeNames(Arrays.asList("ApproximateNumberOfMessages"));;
		GetQueueAttributesResult result = sqs.getQueueAttributes(sqsRequest);
		String qCount = result.getAttributes().get("ApproximateNumberOfMessages");
		int q_Count = Integer.parseInt(qCount);
    	return q_Count;
    }
        
	@Override
	public void run() {
		UUID uuid = UUID.randomUUID();
		Long threadId = Thread.currentThread().getId();
		System.out.println("thread: "+threadId);
        String randomUUIDString = uuid.toString();
		registerUserRequest(threadId+"_"+randomUUIDString);
		String my_id = threadId+"_"+randomUUIDString;

			
		try {
			Thread.sleep(20000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		int numOfResponsesGenerated = getNumberOfMessagesInQueue(responseQueueUrl);
		List<String> msg = new ArrayList<String>();
		while(true) {
			System.out.println(" response found= "+numOfResponsesGenerated);
			int flag = 0;
			if(numOfResponsesGenerated == 0) {
				if(responsesReceived.containsKey(my_id)) {
					System.out.println("Response found in hashmap: "+my_id+" "+responsesReceived.get(my_id));
					requestResult.put(threadId, responsesReceived.get(my_id));
					
					responsesReceived.remove(my_id);
					System.out.println("Deleted: "+my_id+" "+responsesReceived.get(my_id));
					return;
				}
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			else {
				msg = readMessage(responseQueueUrl);
				for(int i = 0; i< numOfResponsesGenerated; i++) {
					if(!msg.isEmpty()) { 
						if(isMyResponse(my_id, msg.get(0))) {
							flag = 1;
							break;
						}
						else {
							System.out.println("Putting in hashmap....");
							String[] decodedResponse = msg.get(0).split(" %% ");
							responsesReceived.put(decodedResponse[0],decodedResponse[1]);
							deleteRequest(responseQueueUrl, msg.get(1), msg.get(0));
							System.out.println("hashmap: "+responsesReceived);
						}
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
				e.printStackTrace();
			}
			numOfResponsesGenerated = getNumberOfMessagesInQueue(responseQueueUrl);
		}

			String[] decrypt_response = msg.get(0).split(" %% ");

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
        System.out.println("Result of delete: "+sqs.deleteMessage(dmr));
        System.out.println("Deleted the message with Id : " + msg);
    }
       
       
}
