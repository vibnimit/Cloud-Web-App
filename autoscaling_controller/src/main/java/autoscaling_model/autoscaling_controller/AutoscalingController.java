package autoscaling_model.autoscaling_controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
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
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;

public class AutoscalingController 
{
	final static int total_instances_allowed = 19;
	final static String requestQueueUrl = "https://sqs.us-west-1.amazonaws.com/411110494130/RequestQueue";
    static Map<String, String> recent_instance_status = new HashMap<String, String>();
	
	public static void main( String[] args )
    {
        System.out.println( "Autoscaling Controller starts" );
        /*final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
        DescribeInstanceStatusRequest describeInstanceRequest = new DescribeInstanceStatusRequest().withInstanceIds("i-001dae1c9d677f004");
        DescribeInstanceStatusResult describeInstanceResults = ec2.describeInstanceStatus(describeInstanceRequest);
        DescribeInstancesRequest descReq = new DescribeInstancesRequest().withInstanceIds("i-001dae1c9d677f004");
        DescribeInstancesResult describeInstanceResult = ec2.describeInstances(descReq);
        List<InstanceStatus> state = describeInstanceResults.getInstanceStatuses();
        System.out.println("Desc: "+describeInstanceResult.getReservations().get(0).getInstances().get(0).getState().getName());
        System.out.println("sate"+ state);
        if(!state.isEmpty())
        System.out.println("Desc: "+state.get(0).getInstanceState());*/
        
        while(true) {
        	System.out.println("Recent Instance Status: "+recent_instance_status);
        	if(!recent_instance_status.isEmpty()) {
	        	while(true) {
		        	for(String id: recent_instance_status.keySet()) {
		        		final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
		        		DescribeInstanceStatusRequest describeInstanceRequest = new DescribeInstanceStatusRequest().withInstanceIds(id);
		                DescribeInstanceStatusResult describeInstanceResult = ec2.describeInstanceStatus(describeInstanceRequest);
		                DescribeInstancesRequest descReq = new DescribeInstancesRequest().withInstanceIds(id);
		                DescribeInstancesResult InstanceResult = ec2.describeInstances(descReq);
//		                System.out.println();
		                List<InstanceStatus> state = describeInstanceResult.getInstanceStatuses();
		                System.out.println("Desc: "+InstanceResult.getReservations().get(0).getInstances().get(0).getState().getName());
		                System.out.println("State of Instances: "+state);
		                if(!state.isEmpty())
		                	recent_instance_status.put(id, state.get(0).getInstanceStatus().getStatus());
		                else {
		                	recent_instance_status.put(id, InstanceResult.getReservations().get(0).getInstances().get(0).getState().getName());
		                }
		                System.out.println("Recent Instance status updated: "+recent_instance_status);
		        	}

	        		int flag = 1;
	        		for(String st: recent_instance_status.values()) {
	        			if(!(st.equals("ok") || st.equals("terminated"))) {
	        				flag = 0;
	        			}
	        		}
	        		if(flag == 1) {
	        			recent_instance_status.clear();
	        			break;
	        		}
	        		
	        		try {
	    				Thread.sleep(5000);
	    			} catch (Exception e) {
	    				// TODO: handle exception
	    			}
	        		if(recent_instance_status.isEmpty()) {
	        			break;
	        		}
	        	}
        	}
        	
        	try {
				Thread.sleep(5000);
			} catch (Exception e) {
				// TODO: handle exception
			}
        	
        	checkAndCreateInstance();
        	
        }
                
    }

	static Boolean isScalingRequired(int num_of_requests) {
		int count = 0;
		while(count < 5) {
			try {
				Thread.sleep(2000);
			} catch (Exception e) {
				// TODO: handle exception
			}
			
			if(getNumberOfMessagesInQueue(requestQueueUrl) < num_of_requests )
				return false;
			
			count++;
		}
		
		return true;
	}
	
	public static void checkAndCreateInstance() {
		System.out.println();
		AutoscalingController obj = new AutoscalingController();
		
		int num_of_messages_in_request_queue = 
				obj.getNumberOfMessagesInQueue(requestQueueUrl);
		System.out.println("Num of Requests "+num_of_messages_in_request_queue);
		int num_of_instances = 0;
		
		if(num_of_messages_in_request_queue > 0) {
			
			num_of_messages_in_request_queue = obj.getNumberOfMessagesInQueue(requestQueueUrl); 
			if( isScalingRequired(num_of_messages_in_request_queue)) {
				System.out.println("Scaling Required");
				num_of_instances = AppTierRunningIns();
				System.out.println("Number of Instances "+num_of_instances);
				if(num_of_instances+num_of_messages_in_request_queue < total_instances_allowed) {
					for(int ins = 0; ins < num_of_messages_in_request_queue; ins++ ) {
						obj.createInstanceWithUserData();
					}
				}
				else {
					for(int ins = 0; ins < total_instances_allowed - num_of_instances; ins++ ) {
						obj.createInstanceWithUserData();
					}
				}
			}
		}
		
/*		if(num_of_instances == 0) {
			
			System.out.println("First App Instance creating...");
			
//			obj.createInstanceWithUserData();
			int num_of_messages_in_request_queue = 
					obj.getNumberOfMessagesInQueue(requestQueueUrl);
			System.out.println("First time num of messages in request queue = "+num_of_messages_in_request_queue);
			if(num_of_messages_in_request_queue > 0) {
				for(int ins = 0; ins < num_of_messages_in_request_queue; ins++ ) {
					obj.createInstanceWithUserData();
				}
			}
			
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
						obj.getNumberOfMessagesInQueue(requestQueueUrl);
				System.out.println("num of messages in request queue= "+num_of_messages_in_request_queue);
				if(num_of_messages_in_request_queue > num_of_instances) {
					int instance_to_create = (num_of_messages_in_request_queue - num_of_instances) < (total_instances_allowed - num_of_instances) ? (num_of_messages_in_request_queue - num_of_instances): (total_instances_allowed - num_of_instances);
					
					for(int ins = 0; ins < instance_to_create; ins++ ) {
						obj.createInstanceWithUserData();
					}
				}
			}
			else {
				System.out.println("Max instance created "+Thread.currentThread().getId());
			}
			System.out.println(Thread.currentThread().getId()+" Exiting the check&createInstance block ");
		}*/
		
		
	}
	
    static int getNumberOfMessagesInQueue(String queue_url) {
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
    
    
    public String createInstanceWithUserData() {
    	String str = "";
    	List<String> sec_grp = new ArrayList<String>();
    	sec_grp.add("sg-0660bd0fc3dfd47dc");
    	final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
    	
		System.out.println("create an Instance");
		String imageId = "ami-0e355297545de2f82";
		int min =1;
		int max =1;
		IamInstanceProfileSpecification iam = new IamInstanceProfileSpecification().withName("ProjectInstanceRole");
		RunInstancesRequest request = new RunInstancesRequest(imageId, min, max);
		request.setInstanceType("t2.micro");
		request.setUserData(getUserDataScript());
		request.setKeyName("cc_trio");
		request.setSecurityGroupIds(sec_grp);
		request.setIamInstanceProfile(iam);
		request.setSubnetId("subnet-aea798f5");
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
//        while(state.isEmpty()) {
//        	if(!state.isEmpty())
        		recent_instance_status.put(resultInstance.getInstanceId(), " ");
//        	describeInstanceResult = ec2.describeInstanceStatus(describeInstanceRequest);
//        	state = describeInstanceResult.getInstanceStatuses();
//        }
//		while(true) {
////			System.out.println("Status: "+resultInstance.getState().getName());
////			count++;
////			try {
//////				Thread.sleep(30000);
////				Thread.sleep(500);
////			} catch (InterruptedException e) {
////				// TODO Auto-generated catch block
////				e.printStackTrace();
////			}
//			
////            System.out.println("state "+state);
//            if(!state.isEmpty()) {
////            	System.out.println("statussssssssss: "+state.get(0).getInstanceStatus().getStatus());
//            	if(state.get(0).getInstanceState().getCode() == 0 || state.get(0).getInstanceState().getCode() == 16 ) {
////            		System.out.println("");
//            		break;
//            	}
//            	System.out.println("s "+state.get(0).getInstanceState().getCode());
//            }
//            describeInstanceResult = ec2.describeInstanceStatus(describeInstanceRequest);
//            state = describeInstanceResult.getInstanceStatuses();
//
//		}
		 
		
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

}
