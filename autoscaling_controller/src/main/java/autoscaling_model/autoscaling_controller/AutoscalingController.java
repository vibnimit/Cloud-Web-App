package autoscaling_model.autoscaling_controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.binary.Base64;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;

public class AutoscalingController 
{
	static int total_instances_allowed;
	static String requestQueueUrl;
	static String responseQueueUrl;
	static String outputBucketName;
    static Map<String, String> recent_instance_status = new HashMap<String, String>();
	static String appAMIID;
	static String appInstanceType;
	static String appSubnetID;
	static String appSecurityGroups;
	static String keyName;
	static String iamInstanceRole;
	static String clusterID;
	static String binarySourceBucket;

	static boolean isNotNullOrEmpty(String str){
	    return (str != null && !str.isEmpty());
	}

	static private String getDefaultVPC() {
		String vpcID = null;
		final AmazonEC2 ec2Client = AmazonEC2ClientBuilder.defaultClient();
		DescribeVpcsRequest dvr = new DescribeVpcsRequest();
		List <Filter> vpcFilters = Arrays.asList(
				new Filter("isDefault", Arrays.asList("true")));
		dvr.setFilters(vpcFilters);
		DescribeVpcsResult vpcResult = ec2Client.describeVpcs(dvr);
		vpcID = vpcResult.getVpcs().get(0).getVpcId();
		return vpcID;
	}
	
	static private String getDefaultSubnet() {
		String subnetId = null;
		final AmazonEC2 ec2Client = AmazonEC2ClientBuilder.defaultClient();
		String vpcID = getDefaultVPC();
		DescribeSubnetsRequest dsr = new DescribeSubnetsRequest();
		List<Filter> subnetFilters = Arrays.asList(
				new Filter("vpc-id", Arrays.asList(vpcID)));
		dsr.setFilters(subnetFilters);
		DescribeSubnetsResult result = ec2Client.describeSubnets(dsr);
		List <Subnet> subnets = result.getSubnets();
		for(Subnet s : subnets) {
			String[] subs = s.getAvailabilityZone().split("-");
			if(subs[subs.length - 1].contains("a")) {
				subnetId = s.getSubnetId();
			}
		}
		return subnetId;
	}
	
	public static void main( String[] args )
    {
        Options options = new Options();

        Option webAMIIDOpt = new Option("allowedInstances", true, "Number of allowed app instances (Default: 19)");
        webAMIIDOpt.setRequired(false);
        options.addOption(webAMIIDOpt);

        Option requestQueueUrlOpt = new Option("requestQueueUrl", true, "Request queue URL");
        requestQueueUrlOpt.setRequired(true);
        options.addOption(requestQueueUrlOpt);

        Option responseQueueUrlOpt = new Option("responseQueueUrl", true, "Response queue URL");
        responseQueueUrlOpt.setRequired(true);
        options.addOption(responseQueueUrlOpt);

        Option outputBucketOpt = new Option("outputBucket", true, "Bucket name for storing darknet output");
        outputBucketOpt.setRequired(true);
        options.addOption(outputBucketOpt);

        Option sourceBucketOpt = new Option("binarySourceBucket", true, "Bucket name in which application binary are stored");
        sourceBucketOpt.setRequired(true);
        options.addOption(sourceBucketOpt);

        Option appAMIIDOpt = new Option("appAMIID", true, "AMI ID for appserver instance (Default: ami-0e355297545de2f82)");
        appAMIIDOpt.setRequired(false);
        options.addOption(appAMIIDOpt);

        Option appInstanceTypeOpt = new Option("appInstanceType", true, "Instance type for appserver instance (Default: t2.micro)");
        appInstanceTypeOpt.setRequired(false);
        options.addOption(appInstanceTypeOpt);

        Option appSubnetIDOpt = new Option("appSubnetID", true, "Subnet ID for appserver instance (Default: one of the default subnet in region)");
        appSubnetIDOpt.setRequired(false);
        options.addOption(appSubnetIDOpt);

        Option appSecurityGroupsOpt = new Option("appSecurityGroups", true, "Comma seperated list of security groups for appserver instance (Default: default)");
        appSecurityGroupsOpt.setRequired(false);
        options.addOption(appSecurityGroupsOpt);
        
        Option keyNameOpt = new Option("keyName", true, "Key name for server instance");
        keyNameOpt.setRequired(true);
        options.addOption(keyNameOpt);

        Option iamInstanceRoleOpt = new Option("iamInstanceRole", true, "Unique cluster identifier used to tag all the resources created under this process");
        iamInstanceRoleOpt.setRequired(true);
        options.addOption(iamInstanceRoleOpt);

        Option clusterIdentifier = new Option("clusterIdentifier", true, "Unique cluster identifier used to tag all the resources created under this process");
        clusterIdentifier.setRequired(true);
        options.addOption(clusterIdentifier);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("autoscalingController", options);

            System.exit(1);
        }
        
        total_instances_allowed = isNotNullOrEmpty(cmd.getOptionValue("allowedInstances")) ? Integer.parseInt(cmd.getOptionValue("allowedInstances")) : 19;
        requestQueueUrl = cmd.getOptionValue("requestQueueUrl");
        appAMIID = isNotNullOrEmpty(cmd.getOptionValue("appAMIID")) ? cmd.getOptionValue("appAMIID") : "ami-0e355297545de2f82";
        appInstanceType = isNotNullOrEmpty(cmd.getOptionValue("appInstanceType")) ? cmd.getOptionValue("appInstanceType") : "t2.micro";
        appSubnetID = isNotNullOrEmpty(cmd.getOptionValue("appSubnetID")) ? cmd.getOptionValue("appSubnetID") : getDefaultSubnet();
        appSecurityGroups = isNotNullOrEmpty(cmd.getOptionValue("appSecurityGroups")) ? cmd.getOptionValue("appSecurityGroups") : "default";
        keyName = cmd.getOptionValue("keyName");
        iamInstanceRole = cmd.getOptionValue("iamInstanceRole");
        clusterID = cmd.getOptionValue("clusterIdentifier");
        outputBucketName = cmd.getOptionValue("outputBucket");
        responseQueueUrl = cmd.getOptionValue("responseQueueUrl");
        binarySourceBucket = cmd.getOptionValue("binarySourceBucket");
        
        System.out.println( "Autoscaling Controller starts" );
        
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
			}
			
			if(getNumberOfMessagesInQueue(requestQueueUrl) < num_of_requests )
				return false;
			
			count++;
		}
		
		return true;
	}
	
	public static void checkAndCreateInstance() {

		AutoscalingController obj = new AutoscalingController();
		
		int num_of_messages_in_request_queue = 
				obj.getNumberOfMessagesInQueue(requestQueueUrl);
		int num_of_instances = 0;
		
		if(num_of_messages_in_request_queue > 0) {
			
			num_of_messages_in_request_queue = obj.getNumberOfMessagesInQueue(requestQueueUrl); 
			if( isScalingRequired(num_of_messages_in_request_queue)) {
				num_of_instances = AppTierRunningIns();
				
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

		
		
	}
	
    static int getNumberOfMessagesInQueue(String queue_url) {

    	AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
    	GetQueueAttributesRequest sqsRequest = new GetQueueAttributesRequest(queue_url);


    	sqsRequest.setAttributeNames(Arrays.asList("ApproximateNumberOfMessages"));;
		GetQueueAttributesResult result = sqs.getQueueAttributes(sqsRequest);
		String qCount = result.getAttributes().get("ApproximateNumberOfMessages");

		int q_Count = Integer.parseInt(qCount);


    	return q_Count;
    }
	
    public synchronized static int AppTierRunningIns() {
    	//just to make the threads a bit slower then each other
	    final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
	    String imageId = appAMIID; 
	    int minInstanceCount = 1; //create 1 instance
	    int maxInstanceCount = 1;
	    DescribeInstancesRequest request = new DescribeInstancesRequest();	
	    RunInstancesRequest rir = new RunInstancesRequest(imageId,
	                minInstanceCount, maxInstanceCount);
	    List<String> valuesT1 = new ArrayList<String>();
	    valuesT1.add(clusterID);
	    List<String> valuesT2 = new ArrayList<String>();
	    valuesT2.add("App Server");
	    System.out.println("List of instances:");
	    Filter filter1 = new Filter("tag:Cluster", valuesT1);
	    Filter filter2 = new Filter("tag:Role", valuesT2);
	    int num_instances = 0;
	    DescribeInstancesResult res = ec2.describeInstances(request.withFilters(filter1, filter2));
	    List<Reservation> reservations = res.getReservations();
	    try {
	    for (Reservation reservation : reservations) {
	    	try {
	    		List<Instance> instances = reservation.getInstances();
	    		for (Instance instance : instances) {
	
	    		DescribeInstanceStatusRequest describeInstanceRequest = new DescribeInstanceStatusRequest().withInstanceIds(instance.getInstanceId());
		            DescribeInstanceStatusResult describeInstanceResult = ec2.describeInstanceStatus(describeInstanceRequest);
		            List<InstanceStatus> state = describeInstanceResult.getInstanceStatuses();
		            if( state.get(0).getInstanceState().getCode() == 0 || state.get(0).getInstanceState().getCode() == 16  ) {
		            	num_instances++;
		            }
		            else {}
	    		}}
	    		catch(IndexOutOfBoundsException exception) {
	
	    			continue;}}
	    	}
	    		catch(AmazonEC2Exception exception) {
	    			System.out.println("No instances running");	
	    		}
    		return num_instances;	
    	}
    
    
    public String createInstanceWithUserData() {
    	String str = "";
    	final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
    	
		List<String> securityGroups = new ArrayList<String>();
		String[] sgs = appSecurityGroups.split(",");
		DescribeSecurityGroupsRequest dsr = new DescribeSecurityGroupsRequest();
		for(int i=0; i<sgs.length;i++) {
			List<Filter> filters = Arrays.asList(
					new Filter("group-name",Arrays.asList(sgs[i])));
			dsr.setFilters(filters);
			DescribeSecurityGroupsResult result = ec2.describeSecurityGroups(dsr);
			if(result.getSecurityGroups().size() == 0) {
				System.err.println("Security group \"" + sgs[i] + "\" not found. Skipping it...");
				continue;
			}
			SecurityGroup s = result.getSecurityGroups().get(0);
			securityGroups.add(s.getGroupId());
		}
		if(securityGroups.size() == 0) {
			List<Filter> filters = Arrays.asList(
					new Filter("group-name",Arrays.asList("default")),
					new Filter("vpc-id",Arrays.asList(getDefaultVPC())));
			dsr.setFilters(filters);
			DescribeSecurityGroupsResult result = ec2.describeSecurityGroups(dsr);
			securityGroups.add(result.getSecurityGroups().get(0).getGroupName());
		}
    	
		System.out.println("create an Instance");
		String imageId = appAMIID;
		int min =1;
		int max =1;
		IamInstanceProfileSpecification iam = new IamInstanceProfileSpecification().withName("ProjectInstanceRole");
		RunInstancesRequest request = new RunInstancesRequest(imageId, min, max);
		request.setInstanceType(appInstanceType);
		request.setUserData(getUserDataScript());
		request.setKeyName(keyName);
		request.setSecurityGroupIds(securityGroups);
		request.setIamInstanceProfile(iam);
		request.setSubnetId(appSubnetID);



        
		RunInstancesResult result = ec2.runInstances(request);
        Instance resultInstance = result.getReservation().getInstances().get(0);
		System.out.println("New Instance has been created "+resultInstance.getInstanceId());
		
		str = resultInstance.getInstanceId();
		CreateTagsRequest createTagsRequest = new CreateTagsRequest()
                .withResources(resultInstance.getInstanceId())
                .withTags(new Tag("Cluster", clusterID),
                		new Tag("Role", "App Server"),
                		new Tag("Name", clusterID + "-app-server"));
        ec2.createTags(createTagsRequest);
		
		resultInstance = result.getReservation().getInstances().get(0);
		DescribeInstanceStatusRequest describeInstanceRequest = new DescribeInstanceStatusRequest().withInstanceIds(resultInstance.getInstanceId());
        DescribeInstanceStatusResult describeInstanceResult = ec2.describeInstanceStatus(describeInstanceRequest);
        
        List<InstanceStatus> state = describeInstanceResult.getInstanceStatuses();

        recent_instance_status.put(resultInstance.getInstanceId(), " ");

		
    	return str;
    }
    
    private static String getUserDataScript(){
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("#! /bin/bash");

        lines.add("add-apt-repository -y ppa:webupd8team/java");
        lines.add("echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections");
        lines.add("apt update && apt install -y oracle-java8-installer");
        lines.add("apt-get install -y awscli xvfb");
        lines.add("aws s3 cp s3://" + binarySourceBucket+ "/app.jar /home/ubuntu/app.jar");
        lines.add("curl https://pjreddie.com/media/files/yolov3-tiny.weights -o /home/ubuntu/darknet/yolov3-tiny.weights");
        lines.add("java -jar /home/ubuntu/app.jar -outputBucket " + outputBucketName + " -requestQueueUrl " + requestQueueUrl + " -responseQueueUrl " + responseQueueUrl);
        

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
