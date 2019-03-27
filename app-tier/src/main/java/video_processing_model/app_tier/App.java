package video_processing_model.app_tier;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
//import com.example.demo.HelloController;

public class App extends Thread
{
	String requestQueueUrl;
	String bucketName;
	String responseQueueUrl;
	String piClusterUrl = "http://206.207.50.7/getvideo";
//	String processResult = null;
	List<String> process_result = new ArrayList<String>();
	
	public App(String requestQueueUrl, String responseQueueUrl, String bucketName, List<String> processResult) {
		this.requestQueueUrl = requestQueueUrl;
		this.bucketName = bucketName;
		this.responseQueueUrl = responseQueueUrl;
		this.process_result = processResult;
	}
	
	public App(String requestQueueUrl, String responseQueueUrl, String bucketName) {
		this.requestQueueUrl = requestQueueUrl;
		this.bucketName = bucketName;
		this.responseQueueUrl = responseQueueUrl;
	}
	
	public App() {
//		this.requestId = requestId;
	}
	
    public static void main( String[] args ) throws IOException
    {
        System.out.println( "Hello World!" );

        Options options = new Options();

        Option requestQueueUrlOpt = new Option("requestQueueUrl", true, "Request queue URL");
        requestQueueUrlOpt.setRequired(true);
        options.addOption(requestQueueUrlOpt);

        Option responseQueueUrlOpt = new Option("responseQueueUrl", true, "Response queue URL");
        responseQueueUrlOpt.setRequired(true);
        options.addOption(responseQueueUrlOpt);

        Option outputBucketOpt = new Option("outputBucket", true, "Bucket name for storing darknet output");
        outputBucketOpt.setRequired(true);
        options.addOption(outputBucketOpt);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("App", options);

            System.exit(1);
        }
        
        String request_queue_url = cmd.getOptionValue("requestQueueUrl");
        String response_queue_url = cmd.getOptionValue("responseQueueUrl");
        String bucket_name = cmd.getOptionValue("outputBucket");
        App app = new App(request_queue_url, response_queue_url, bucket_name);
        while (true) {
        	
        	app.listenRequest();
        	
		}
    }
    
    String checkCurl() throws IOException {
//    	URL url;
//		url = new URL("https://crunchify.com/");
//        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
//        String res = httpConn.getContent().toString();
    	String strTemp = "";
    	try {
			URL url = new URL("http://169.254.169.254/latest/meta-data/instance-id");
			BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
			strTemp = br.readLine();
			//			while (null != (strTemp = br.readLine())) {
////				System.out.println(strTemp);
//			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
//        System.out.println("ressp= "+res);
    	return strTemp;
    }
    
	public void run()
    {    
		System.out.println("Worker starts");
		
		processRequest();
//		try {
//			Thread.sleep(20000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		process_result.add("Request is processed");
		if(!process_result.isEmpty())
		System.out.println("Worker finishes job, result: "+process_result.get(0));
    }
	
    //keep polling the RequestQueue to get a request and as soon as receive a request process it
    void listenRequest() {
    	String queue_url = requestQueueUrl;
    	AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
    	final ReceiveMessageRequest receiveMessageRequest =
                new ReceiveMessageRequest(queue_url);
    	
    	List<Message> messages = sqs.receiveMessage(receiveMessageRequest)
                .getMessages();
    	String messages_received = "";
    	System.out.println("Listening starts....");
    	int count = 0;
    	while(count < 15 && messages.size() == 0) {
//    	while(messages.size() == 0) {
    		messages = sqs.receiveMessage(receiveMessageRequest)
                    .getMessages();
    		count++;
    		try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	
    	if(messages.size() == 0) {
    		//call the method to kill itself
    		String instanceId = null;
    		try {
				instanceId = checkCurl();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				instanceId = null;
				e.printStackTrace();
			}
    		if(instanceId != null)
    		killInstance(instanceId);
    		
    	}
    	
    	String MessageId = "";
    	String requestId = "";
    	String receipt_handle = "";
    	Message rec_message = null;
    	for(Message message: messages) {
    		MessageId = message.getMessageId();
    		requestId = message.getBody();
    		receipt_handle = message.getReceiptHandle();
    		rec_message = message;
//		messages = sqs.receiveMessage(receiveMessageRequest)
//                .getMessages();
//    	messages_received += message.getBody()+"\t"+message.getMessageId()+"\n";
    		System.out.println("Message Read: "+ message.getBody());
    	}
    	String result = "";
    	App worker = new App(requestQueueUrl, responseQueueUrl, bucketName, process_result);
//    	Thread worker = new Thread();
    	Long startTime = System.nanoTime();
    	worker.start();
    	
        int timeout = 120;
//    	int timeout = 25;
    	
        while(worker.isAlive()) {
    		try {
				Thread.sleep(60000);
//    			Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				break;
			}
    		if(worker.isAlive()) {
//	    		timeout = 120;
	    		sqs.changeMessageVisibility(queue_url, receipt_handle, timeout);
    		}
    	}
    	
    	Long endTime = System.nanoTime();
        Long time = (endTime - startTime)/(long)Math.pow(10.0, 9.0);
        System.out.println("Time elapsed: " + time.toString());
        System.out.println("processResult = "+process_result);
        if(!process_result.isEmpty()) {
//        	processResult = process_result.get(0);
//        	System.out.println("processResult = "+processResult);
	    	storeResultInResponseQueue(requestId, process_result.get(0));
	    	deleteRequest(sqs, queue_url, receipt_handle, MessageId);
	    	process_result.clear();
        }
    }
    
    void killInstance(String instance_id) {
    	
	    final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
	    TerminateInstancesRequest request = new TerminateInstancesRequest().
	            withInstanceIds(instance_id);//terminate instance using the instance id
	    ec2.terminateInstances(request);
	    
    }
    
    void processRequest() {
		String videoPath = getVideo();
		String filename = videoPath.split("/tmp/")[1];
		System.out.println("filename= "+filename);
		
		String outputFile = videoPath.concat("_darknet_output");
		String resultDarknet = runDarknet(videoPath, outputFile);
		String result = "";
		if (resultDarknet != null) {
			result = "{"+filename+","+resultDarknet+"}";
			outputFile = videoPath.concat("_output");
	        try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
				writer.write(result);
				writer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        storeResultInS3(outputFile, filename, resultDarknet);
	        File videoFIle = new File(videoPath);
	        videoFIle.delete();
	        
	        File outFile = new File(outputFile);
	        outFile.delete();
	        process_result.add(result);
		}
		
//    	return result;
    }
    
    String getVideo() {
    	DownloadVideo dv = new DownloadVideo();
    	String fileName = "";
    	try {
			fileName = dv.downloadFile(piClusterUrl, "/tmp/");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	System.out.println("file from server "+fileName);
    	return "/tmp/" + fileName;
    }
    
    void storeResultInS3(String file_path, String fileKeyName, String result) {
    	final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
    	String key_name = Paths.get(file_path).getFileName().toString();
    	
        try {
            s3.putObject(bucketName, fileKeyName, result);
        } catch (AmazonServiceException e) {
            System.err.println(e.getErrorMessage());
        }
    }
    
    void storeResultInResponseQueue(String request, String result) {
    	AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
    	String response_queue_url = responseQueueUrl;
    	System.out.println("resp url "+response_queue_url);
//    	Timestamp timestamp = new Timestamp(System.currentTimeMillis());
//    	String requestId = "request_"+timestamp.getTime();
    	SendMessageRequest send_msg_request = new SendMessageRequest()
    	        .withQueueUrl(response_queue_url)
    	        .withMessageBody(request+" %% "+result)
    	        .withDelaySeconds(1);
    	sqs.sendMessage(send_msg_request).toString();
    	System.out.println("Result stored......");
    }
    
    
    public void deleteRequest(AmazonSQS sqs, String queueUrl, String receipt_handle, String MessageId) {
    	DeleteMessageRequest dmr = new DeleteMessageRequest();
        dmr.withQueueUrl(queueUrl).withReceiptHandle(receipt_handle);
        sqs.deleteMessage(dmr);
        System.out.println("Deleted the message with Id : " + MessageId);
    } 
    
	Process runCommand(String[] cmd, String[] envp, File dir) {
		InputStream stdout = null;
		Runtime rt = Runtime.getRuntime();
		Process pr = null;
		try {
			pr = rt.exec(cmd, envp, dir);
			while(pr.isAlive()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if (pr.exitValue() != 0) {
	            InputStream stderr = pr.getErrorStream();
	            InputStreamReader isr = new InputStreamReader(stderr);
	            BufferedReader br = new BufferedReader(isr);
	            String line = null;
	            while ( (line = br.readLine()) != null)
	                System.out.println(line);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return pr;
	}
	
	String getDisplayID() {
        String display_id = null;
        String[] commands = {"/bin/bash", "-c", "ps -e -f"};
        Process pr = runCommand(commands, null, null);
		InputStream stdout = null;
		if (pr.exitValue() == 0) {
			stdout = pr.getInputStream();
		}
        if(stdout != null) {
            InputStreamReader isr = new InputStreamReader(stdout);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            try {
				while ( (line = br.readLine()) != null) {
				    if (line.contains("Xvfb")) {
				    	String[] lines = line.split("\\s+");
				    	display_id = lines[lines.length-1];
				    	break;
				    }
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return display_id;
	}
	
	String getDisplay() {
		String display_id = getDisplayID();
		if (display_id == null) {
			System.out.println("Creating Display");
	        String[] commands = {"/bin/bash", "-c", "Xvfb :1 &"};
			Process pr = runCommand(commands, null, null);
			InputStream stdout = null;
			if (pr.exitValue() == 0) {
				stdout = pr.getInputStream();
			}
			if(stdout != null) {
				System.out.println("Created Display");
	            InputStreamReader isr = new InputStreamReader(stdout);
	            BufferedReader br = new BufferedReader(isr);
	            String line = null;
	            try {
					while ( (line = br.readLine()) != null)
							System.out.println(line);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
	        display_id = getDisplayID();
		}
        return display_id;
	}
	
	String runDarknet(String videoPath, String outputFile) {
		String display_id = null;
		HashSet<String> objects = new HashSet<String>();
		while(display_id == null)
			display_id = getDisplay();
		String darknetHome = "/home/ubuntu/darknet";
		String configs = darknetHome + "/cfg/coco.data " + darknetHome + "/cfg/yolov3-tiny.cfg";
		String weights = darknetHome + "/yolov3-tiny.weights";
//		String videoPath = "/home/ubuntu/video-pie1-085733.h264";
		String darknetCommand = darknetHome + "/darknet detector demo " + configs + " " + weights + " " + videoPath + " -dont_show > " + outputFile;
		String[] commands = {"/bin/bash", "-c", darknetCommand};
		String[] envp = {"DISPLAY="+display_id};
		File dir = new File(darknetHome);
		System.out.println("Running Object detection program...");
		Process pr = runCommand(commands, envp, dir);
		InputStream stdout = null;
		if (pr.exitValue() == 0) {
			stdout = pr.getInputStream();
		} else {
			System.out.println("Object detection failed for video: " + Paths.get(videoPath).getFileName().toString());
			return null;
		}
		if(stdout != null) {
			File file = new File(outputFile);
			BufferedReader br;
			try {
				br = new BufferedReader(new FileReader(file));
				String line;
				try {
					while ((line = br.readLine()) != null)
						if (line.contains("%") && line.charAt(line.length()-1) == '%') {
							objects.add(line.split(":")[0]);
						}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		StringBuilder result = new StringBuilder();
		for (String str : objects) {
			result.append(str);
			result.append(",");
		}
		return result.length() > 0 ? result.substring(0, result.length()-1): "No item is detected";
	}
}
