package infrastructure;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeKeyPairsResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.KeyPairInfo;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TagSpecification;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.AddRoleToInstanceProfileRequest;
import com.amazonaws.services.identitymanagement.model.AttachRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.CreateInstanceProfileRequest;
import com.amazonaws.services.identitymanagement.model.CreateInstanceProfileResult;
import com.amazonaws.services.identitymanagement.model.CreateRoleRequest;
import com.amazonaws.services.identitymanagement.model.CreateRoleResult;
import com.amazonaws.services.identitymanagement.model.DeleteInstanceProfileRequest;
import com.amazonaws.services.identitymanagement.model.DeleteRoleRequest;
import com.amazonaws.services.identitymanagement.model.DetachRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.ListPoliciesResult;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.amazonaws.services.identitymanagement.model.Policy;
import com.amazonaws.services.identitymanagement.model.RemoveRoleFromInstanceProfileRequest;
import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.BucketTaggingConfiguration;
import com.amazonaws.services.s3.model.TagSet;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.DeleteQueueRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;

public class Bootstrap {
	String webAMIID;
	String webInstanceType;
	String webSubnetID;
	String webSecurityGroups;
	String appAMIID;
	String appInstanceType;
	String appSubnetID;
	String appSecurityGroups;
	String keyName;
	String requestQueueName;
	String responseQueueName;
	String requestQueueUrl;
	String responseQueueUrl;
	String outputBucketName;
	String binarySourceBucket;
	String clusterID;
	String iamInstanceRole;
	boolean rolePresent = false;
	boolean outputBucketPresent = false;
	public Bootstrap() {
		
	}
	
	private boolean isNotNullOrEmpty(String str){
	    return (str != null && !str.isEmpty());
	}
	
	private String getDefaultVPC() {
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
	
	private String getDefaultSubnet() {
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
	
	private boolean isBucketPresent(String bucketName) {
		final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
		return s3.doesBucketExistV2(bucketName);
	}
	
	private Bucket getBucket(String bucketName) {
		final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
        List<Bucket> buckets = s3.listBuckets();
        Bucket named_bucket = null;
		for (Bucket b : buckets) {
            if (b.getName().equals(bucketName)) {
                named_bucket = b;
            }
        }
        return named_bucket;
	}
	
	private Bucket createBucket(String bucketName) {
		final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
        Bucket b = null;
		if (isBucketPresent(bucketName)) {
			outputBucketPresent = true;
            System.out.format("Bucket %s already exists.\n", bucketName);
            b = getBucket(bucketName);
        } else {
            try {
                b = s3.createBucket(bucketName);
                BucketTaggingConfiguration btc = new BucketTaggingConfiguration();
                Map <String, String> tagMap = new HashMap<String, String>();
                tagMap.put("Cluster", clusterID);
                List<TagSet> tagSets = Arrays.asList(
                		new TagSet(tagMap));
                btc.setTagSets(tagSets);
                s3.setBucketTaggingConfiguration(bucketName, btc);
            } catch (AmazonS3Exception e) {
                System.err.println(e.getErrorMessage());
            }
        }
        return b;
    }
	
	private boolean isQueuePresent(String queueName) {
		final AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
		ListQueuesResult result = sqs.listQueues(queueName);
		for(String q : result.getQueueUrls()) {
			String[] name = q.split("/");
			if(name[name.length - 1].equals(queueName)) {
				return true;
			}
		}
		return false;
	}
	
	private String createQueue(String queueName, int visibilityTimeout) {
		final AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
        CreateQueueRequest cqr = new CreateQueueRequest();
        cqr.setQueueName(queueName);
        cqr.addAttributesEntry("VisibilityTimeout", String.valueOf(visibilityTimeout));
        CreateQueueResult result = sqs.createQueue(cqr);
        return result.getQueueUrl();
	}
	
	private boolean isKeyPresent(String keyName) {
		final AmazonEC2 ec2Client = AmazonEC2ClientBuilder.defaultClient();
		DescribeKeyPairsResult result = ec2Client.describeKeyPairs();
		List<KeyPairInfo> keyPairs = result.getKeyPairs();
		for(KeyPairInfo k : keyPairs) {
			if(k.getKeyName().equals(keyName))
				return true;
		}
		return false;
	}
	
	private Instance createWebInstance() {
		Instance webInstance = null;
		final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
		RunInstancesRequest rir = new RunInstancesRequest(webAMIID, 1, 1);
		rir.setInstanceType(webInstanceType);
		rir.setSubnetId(webSubnetID);
		rir.setKeyName(keyName);
		List<String> securityGroups = new ArrayList<String>();
		String[] sgs = webSecurityGroups.split(",");
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
		rir.setSecurityGroupIds(securityGroups);
		IamInstanceProfileSpecification iamspec = new IamInstanceProfileSpecification().withName(iamInstanceRole);
		rir.setIamInstanceProfile(iamspec);
		StringWriter userData = new StringWriter();
		PrintWriter writer = new PrintWriter(userData, true);
		writer.println("#!/bin/bash");
		writer.println("add-apt-repository -y ppa:webupd8team/java");
		writer.println("echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections");
		writer.println("apt update && apt install -y oracle-java8-installer awscli");

		writer.println("pushd /opt");
		writer.println("wget https://archive.apache.org/dist/tomcat/tomcat-9/v9.0.17/bin/apache-tomcat-9.0.17.tar.gz");
		writer.println("tar -zxf apache-tomcat-9.0.17.tar.gz");
		writer.println("ln -s /opt/apache-tomcat-9.0.17 /opt/tomcat");
		writer.println("popd");

		writer.println("groupadd tomcat");
		writer.println("useradd -s /bin/bash -g tomcat -d /opt/tomcat tomcat");
		writer.println("chown -R tomcat.tomcat /opt/apache-tomcat-9.0.17");
		writer.println("chmod 775 /opt/apache-tomcat-9.0.17/webapps");

		writer.println("cat <<EOF > /etc/init.d/tomcat");
		writer.println("#!/bin/sh");

		writer.println("start() {");
		writer.println("   echo running: /opt/tomcat/bin/startup.sh");
		writer.println("   su - tomcat -c  \"/opt/tomcat/bin/startup.sh\"");
		writer.println("}");

		writer.println("stop() {");
		writer.println("    echo running: /opt/tomcat/bin/shutdown.sh");
		writer.println("    su - tomcat -c  \"/opt/tomcat/bin/shutdown.sh\"");
		writer.println("}");

		writer.println("case \"\\$1\" in");
		writer.println("  start)");
		writer.println("        start");
		writer.println("        ;;");
		writer.println("  stop)");
		writer.println("        stop");
		writer.println("        ;;");
		writer.println("  restart)");
		writer.println("        stop");
		writer.println("        sleep 3");
		writer.println("        start");
		writer.println("        ;;");
		writer.println("  *)");
		writer.println("  echo \"Usage: \\$0 (start|stop|restart)\""); 
		writer.println("  exit 1");
		writer.println("esac");
		writer.println("EOF");

		writer.println("chmod +x /etc/init.d/tomcat");
		writer.println("/bin/systemctl daemon-reload");

		writer.println("aws s3 cp s3://" + binarySourceBucket + "/webTier.war /opt/tomcat/webapps/webTier.war");
		writer.println("service tomcat start");
		
		writer.println("sleep 5");
		writer.println("cat <<EOF > /opt/tomcat/webapps/webTier/WEB-INF/classes/application.properties");
		writer.println("request.queue.url=" + requestQueueUrl);
		writer.println("response.queue.url=" + responseQueueUrl);
		writer.println("EOF");
		writer.println("service tomcat restart");

		writer.println("aws s3 cp s3://" + binarySourceBucket + "/autoscaling.jar /home/ubuntu/autoscaling.jar");
		writer.println("sudo apt-get install -y supervisor");
		writer.println("cat <<EOF > /etc/supervisor/conf.d/autoscaler.conf");
		writer.println("[supervisord]");
		writer.println("nodaemon=true");
		writer.println("[program:autoscaler]");
		writer.println("command=java -jar /home/ubuntu/autoscaling.jar -requestQueueUrl " +
		requestQueueUrl + " -keyName " + keyName + " -iamInstanceRole " + iamInstanceRole +
		" -clusterIdentifier " + clusterID + " -responseQueueUrl " + responseQueueUrl +
		" -outputBucket " + outputBucketName + " -appAMIID " + appAMIID +
		" -appInstanceType " + appInstanceType + " -appSubnetID " + appSubnetID +
		" -appSecurityGroups " + appSecurityGroups+ " -binarySourceBucket " + binarySourceBucket);
		
		writer.println("directory=/home/ubuntu");
		writer.println("autostart=true");
		writer.println("autorestart=true");
		writer.println("EOF");
		writer.println("service supervisor restart");

		rir.setUserData(new String(Base64.encodeBase64(userData.toString().getBytes())));
		
		webInstance = ec2.runInstances(rir).getReservation().getInstances().get(0);
        List<Tag> tags = Arrays.asList(
        		new Tag("Name", clusterID + "-web-server"),
        		new Tag("Cluster", clusterID),
        		new Tag("Role", "Web Server"));
        ec2.createTags(
        		new CreateTagsRequest().withResources(
        				webInstance.getInstanceId()).withTags(tags));
		
		return webInstance;
	}
	
	private void attachPolicyToRole(String roleName, String policyArn) {
		final AmazonIdentityManagement iam = AmazonIdentityManagementClientBuilder.defaultClient();
		AttachRolePolicyRequest arpr = new AttachRolePolicyRequest();
		arpr.setRoleName(roleName);
		arpr.setPolicyArn(policyArn);
		iam.attachRolePolicy(arpr);
	}
	
	private String createIamInstanceRole(String roleName) {
		String iamInstanceRole = null;
		final String policyDocument = 
			    "{" +
			    "  \"Version\": \"2012-10-17\"," +
			    "  \"Statement\": [" +
			    "    {" +
			    "        \"Effect\": \"Allow\"," +
			    "        \"Principal\": {" +
			    "            \"Service\": [" +
			    "                \"ec2.amazonaws.com\"" +
			    "            ]" +
			    "         },"+
			    "        \"Action\": \"sts:AssumeRole\"" +
			    "    }" +
			    "   ]" +
			    "}";
		if(roleName == null)
			roleName = clusterID + "InstanceRole";
		
		if(!isRolePresent(roleName)) {
			System.out.println("Creating role");
			final AmazonIdentityManagement iam = AmazonIdentityManagementClientBuilder.defaultClient();
			CreateRoleRequest crr = new CreateRoleRequest();
			crr.setRoleName(roleName);
			crr.setAssumeRolePolicyDocument(policyDocument);
			CreateRoleResult result = iam.createRole(crr);
			
			ListPoliciesResult lpr = iam.listPolicies();
			List<Policy> policies = lpr.getPolicies();
			for(Policy p: policies) {
				String policyName = p.getPolicyName();
				if(policyName.equals("AmazonEC2FullAccess") || policyName.equals("AmazonSQSFullAccess") || policyName.equals("AmazonS3FullAccess") || policyName.equals("IAMFullAccess")) {
					attachPolicyToRole(roleName, p.getArn());
				}
			}
			iamInstanceRole = result.getRole().getRoleName();
			CreateInstanceProfileRequest cipr = new CreateInstanceProfileRequest().withInstanceProfileName(roleName);
			CreateInstanceProfileResult cipresult = iam.createInstanceProfile(cipr);
			iam.addRoleToInstanceProfile(
					new AddRoleToInstanceProfileRequest().withInstanceProfileName(
							cipresult.getInstanceProfile().getInstanceProfileName()).withRoleName(
									roleName));
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			rolePresent = true;
			iamInstanceRole = roleName;
		}
		return iamInstanceRole;
	}
	
	private boolean isRolePresent(String roleName) {
		final AmazonIdentityManagement iam = AmazonIdentityManagementClientBuilder.defaultClient();
		ListRolesResult result = iam.listRoles();
		List<Role> roles = result.getRoles();
		for(Role r: roles) {
			if(r.getRoleName().equals(roleName)) {
				return true;
			}
		}
		return false;
	}
	
	private void detachPolicyFromRole(String roleName, String policyArn) {
		final AmazonIdentityManagement iam = AmazonIdentityManagementClientBuilder.defaultClient();
		iam.detachRolePolicy(new DetachRolePolicyRequest().withPolicyArn(policyArn).withRoleName(roleName));
	}
	
	private void deleteIamInstanceRole(String roleName) {
		final AmazonIdentityManagement iam = AmazonIdentityManagementClientBuilder.defaultClient();
		ListPoliciesResult lpr = iam.listPolicies();
		List<Policy> policies = lpr.getPolicies();
		for(Policy p: policies) {
			String policyName = p.getPolicyName();
			if(policyName.equals("AmazonEC2FullAccess") || policyName.equals("AmazonSQSFullAccess") || policyName.equals("AmazonS3FullAccess") || policyName.equals("IAMFullAccess")) {
				detachPolicyFromRole(roleName, p.getArn());
			}
		}
		iam.removeRoleFromInstanceProfile(
				new RemoveRoleFromInstanceProfileRequest().withInstanceProfileName(
						roleName).withRoleName(roleName));
		iam.deleteRole(new DeleteRoleRequest().withRoleName(roleName));
		iam.deleteInstanceProfile(new DeleteInstanceProfileRequest().withInstanceProfileName(roleName));
	}
	
	private void deleteQueue(String queueUrl) {
		final AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
		sqs.deleteQueue(new DeleteQueueRequest().withQueueUrl(queueUrl));
	}
	
	private void deleteBucket(String bucketName) {
		final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
		s3.deleteBucket(bucketName);
	}
	
	private void rollBack(String stage) {
		System.out.println("Starting rollback...");
		if(!rolePresent) {
			deleteIamInstanceRole(iamInstanceRole);
			System.out.println("Deleted IAM role " + iamInstanceRole);
		}
		if(stage != null) {
			if(stage.equals("bucket") && !outputBucketPresent) {
				deleteBucket(outputBucketName);
				System.out.println("Deleted bucket " + outputBucketName);
			} else if (stage.equals("requestQueue")) {
				deleteBucket(outputBucketName);
				System.out.println("Deleted bucket " + outputBucketName);
				deleteQueue(requestQueueUrl);
				System.out.println("Deleted Queue " + requestQueueName);
			} else if (stage.equals("responseQueue")) {
				deleteBucket(outputBucketName);
				System.out.println("Deleted bucket " + outputBucketName);
				deleteQueue(requestQueueUrl);			
				System.out.println("Deleted Queue " + requestQueueName);
				deleteQueue(responseQueueName);			
				System.out.println("Deleted Queue " + responseQueueName);
			}
		}
	}

	boolean checkStatus(Instance webInstance) {
		final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
		DescribeInstanceStatusResult result = ec2.describeInstanceStatus(
				new DescribeInstanceStatusRequest().withInstanceIds(webInstance.getInstanceId()));
		if(result.getInstanceStatuses().size() != 0 && 
				result.getInstanceStatuses().get(0).getInstanceState().getCode()==16) {
			return true;
		}
		return false;
	}
	
	public static void main(String[] args) {
        Options options = new Options();

        Option webAMIIDOpt = new Option("webAMIID", true, "AMI ID for webserver instance (Default: ami-0ad16744583f21877)");
        webAMIIDOpt.setRequired(false);
        options.addOption(webAMIIDOpt);

        Option webInstanceTypeOpt = new Option("webInstanceType", true, "Instance type for webserver instance (Default: t2.micro)");
        webInstanceTypeOpt.setRequired(false);
        options.addOption(webInstanceTypeOpt);

        Option webSubnetIDOpt = new Option("webSubnetID", true, "Subnet ID for webserver instance (Default: one of the default subnet in region)");
        webSubnetIDOpt.setRequired(false);
        options.addOption(webSubnetIDOpt);

        Option webSecurityGroupsOpt = new Option("webSecurityGroups", true, "Comma seperated list of security groups for webserver instance (Default: default)");
        webSecurityGroupsOpt.setRequired(false);
        options.addOption(webSecurityGroupsOpt);

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

        Option requestQueueNameOpt = new Option("requestQueueName", true, "Request queue name");
        requestQueueNameOpt.setRequired(true);
        options.addOption(requestQueueNameOpt);

        Option responseQueueNameOpt = new Option("responseQueueName", true, "Response queue name");
        responseQueueNameOpt.setRequired(true);
        options.addOption(responseQueueNameOpt);

        Option outputBucketOpt = new Option("outputBucket", true, "Bucket name for storing darknet output");
        outputBucketOpt.setRequired(true);
        options.addOption(outputBucketOpt);

        Option sourceBucketOpt = new Option("binarySourceBucket", true, "Bucket name in which application binary are stored");
        sourceBucketOpt.setRequired(true);
        options.addOption(sourceBucketOpt);

        Option clusterIdentifier = new Option("clusterIdentifier", true, "Unique cluster identifier used to tag all the resources created under this process");
        clusterIdentifier.setRequired(true);
        options.addOption(clusterIdentifier);

        Option iamInstanceRole = new Option("iamInstanceRole", true, "Unique cluster identifier used to tag all the resources created under this process");
        iamInstanceRole.setRequired(false);
        options.addOption(iamInstanceRole);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("Bootstrap", options);

            System.exit(1);
        }
        
        Bootstrap b = new Bootstrap();
        b.webAMIID = b.isNotNullOrEmpty(cmd.getOptionValue("webAMIID")) ? cmd.getOptionValue("webAMIID") : "ami-0ad16744583f21877";
        b.webInstanceType = b.isNotNullOrEmpty(cmd.getOptionValue("webInstanceType")) ? cmd.getOptionValue("webInstanceType") : "t2.micro";
        b.webSubnetID = b.isNotNullOrEmpty(cmd.getOptionValue("webSubnetID")) ? cmd.getOptionValue("webSubnetID") : b.getDefaultSubnet();
        b.webSecurityGroups = b.isNotNullOrEmpty(cmd.getOptionValue("webSecurityGroups")) ? cmd.getOptionValue("webSecurityGroups") : "default";
        b.appAMIID = b.isNotNullOrEmpty(cmd.getOptionValue("appAMIID")) ? cmd.getOptionValue("appAMIID") : "ami-0e355297545de2f82";
        b.appInstanceType = b.isNotNullOrEmpty(cmd.getOptionValue("appInstanceType")) ? cmd.getOptionValue("appInstanceType") : "t2.micro";
        b.appSubnetID = b.isNotNullOrEmpty(cmd.getOptionValue("appSubnetID")) ? cmd.getOptionValue("appSubnetID") : b.getDefaultSubnet();
        b.appSecurityGroups = b.isNotNullOrEmpty(cmd.getOptionValue("appSecurityGroups")) ? cmd.getOptionValue("appSecurityGroups") : "default";
        b.keyName = cmd.getOptionValue("keyName");
        b.responseQueueName = cmd.getOptionValue("responseQueueName");
        b.requestQueueName = cmd.getOptionValue("requestQueueName");
        b.outputBucketName = cmd.getOptionValue("outputBucket");
        b.binarySourceBucket = cmd.getOptionValue("binarySourceBucket");
        b.clusterID = cmd.getOptionValue("clusterIdentifier");
        b.iamInstanceRole = b.isNotNullOrEmpty(cmd.getOptionValue("iamInstanceRole")) && b.isRolePresent(cmd.getOptionValue("iamInstanceRole")) ? cmd.getOptionValue("iamInstanceRole") : b.createIamInstanceRole(cmd.getOptionValue("iamInstanceRole"));
        
        if(!b.isKeyPresent(b.keyName)) {
        	System.err.println("Error: Specified key doesn't exist. Exiting...");
        	b.rollBack(null);
        	System.exit(1);
        }
        Bucket sourceBucket = null;
        if(!b.isBucketPresent(b.binarySourceBucket)) {
        	System.err.println("Error: Binary source bucket doesn't exist. Exiting...");
        	b.rollBack(null);
        	System.exit(1);
        } else {
        	sourceBucket = b.getBucket(b.binarySourceBucket);
        }
        
        Bucket outBucket = b.createBucket(b.outputBucketName);
        if(outBucket == null) {
        	System.err.println("Error: Failed to create output bucket. Exiting...");
        	b.rollBack(null);
        	System.exit(1);
        }
        
        System.out.println("Creating Queues...");        
        String requestQueueUrl = null;
        if(!b.isQueuePresent(b.requestQueueName)) {
        	b.requestQueueUrl = b.createQueue(b.requestQueueName, 120);
        } else {
        	System.err.println("Error: Queue \"" + b.requestQueueName + "\" already present. Exiting...");
        	b.rollBack("bucket");
        	System.exit(1);
        }
        
        String responseQueueUrl = null;
        if(!b.isQueuePresent(b.responseQueueName)) {
        	b.responseQueueUrl = b.createQueue(b.responseQueueName, 0);
        } else {
        	System.err.println("Error: Queue \"" + b.responseQueueName + "\" already present. Exiting...");
        	b.rollBack("requestQueue");
        	System.exit(1);
        }
        
        System.out.println("Creating Instance...");
        Instance webInstance = b.createWebInstance();
        
        while(!b.checkStatus(webInstance)) {
        	try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        
        final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
        webInstance = ec2.describeInstances(new DescribeInstancesRequest().withInstanceIds(webInstance.getInstanceId())).getReservations().get(0).getInstances().get(0); 
        System.out.println(webInstance);
        System.out.println("Cluster is up. Webserver Instance ID: " + webInstance.getPublicIpAddress());
	}
}
