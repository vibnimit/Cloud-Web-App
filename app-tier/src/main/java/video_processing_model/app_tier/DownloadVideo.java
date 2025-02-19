package video_processing_model.app_tier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
 
public class DownloadVideo {
    private static final int BUFFER_SIZE = 4096;
    public static String getVideo(String fileURL, String saveDir)
            throws IOException {
    	String fileName = "";
        URL url = new URL(fileURL);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        int responseCode = httpConn.getResponseCode();
 
        // always check HTTP response code first
        if (responseCode == HttpURLConnection.HTTP_OK) {
            
            String disposition = httpConn.getHeaderField("Content-Disposition");
 
            if (disposition != null) {
                // extracts file name from header field
            	fileName = disposition.split("filename=")[1];
            } else {
                // extracts file name from URL
                fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1,
                        fileURL.length());
            }
 
            // opens input stream from the HTTP connection
            InputStream inputStream = httpConn.getInputStream();
            String saveFilePath = saveDir + File.separator + fileName;
             
            // opens an output stream to save into file
            FileOutputStream outputStream = new FileOutputStream(saveFilePath);
 
            int bytesRead = -1;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
 
            outputStream.close();
            inputStream.close();
 
            System.out.println("video downloaded");
        } else {
            System.out.println("Error occured in downloading video" + responseCode);
        }
        httpConn.disconnect();
        return fileName;
    }
}