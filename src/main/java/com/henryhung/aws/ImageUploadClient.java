package com.henryhung.aws;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

public class ImageUploadClient {

    private final String bucketName = "bucket-comp3358-3035782750";
    private final String inboxQueueUrl = "inbox-queue-comp3358-3035782750";
    private final String outboxQueueUrl = "outbox-queue-comp3358-3035782750";

    private final Region region;

    private final S3Client s3;
    private final SqsClient sqs;

    public ImageUploadClient() {
        region = Region.AP_SOUTHEAST_1;

        try {
            s3 = S3Client.builder()
                    .region(region)
                    .credentialsProvider(ProfileCredentialsProvider.create())
                    .build();
            debug("S3 client created");
        } catch (Exception e) {
            debug("Failed to create S3 client");
            throw e;
        }

        try {
            sqs = SqsClient.builder()
                    .region(region)
                    .credentialsProvider(ProfileCredentialsProvider.create())
                    .build();
            debug("SQS client created");
        } catch (Exception e) {
            debug("Failed to create SQS client");
            throw e;
        }
    }

    // Upload image to S3 and send message to SQS inbox queue from local file
    public String uploadImage(String fileName, String directory) {
        InputStream fileContent = fileToInputStream(new File(directory + fileName));
        if (fileContent == null) {
            debug("Failed to get image file.");
            return null;
        }
        return uploadImage(fileName, fileContent);
    }

    // Upload image to S3 and send message to SQS inbox queue via InputStream
    public String uploadImage(String fileName, InputStream fileContent) {

        String key = UUID.randomUUID() + "." + this.getExtension(fileName);

        try {
            // Upload image to S3
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build(),
                    RequestBody.fromInputStream(fileContent, fileContent.available()));
            debug("Uploaded image to S3");
        } catch (Exception e) {
            debug("Failed to upload image to S3");
            return null;
        }

        try {
            // Send message to SQS inbox queue
            sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(inboxQueueUrl)
                    .messageBody(key)
                    .delaySeconds(1)
                    .build());
            debug("Sent message to inbox queue");
        } catch (Exception e) {
            debug("Failed to send message to inbox queue");
            return null;
        }

        return key;
    }

    public String downloadImage(String fileName, String directory, String key) {
        String filePath = directory + fileName;
        File outputFile = new File(filePath);
        if (! createFolders(directory)) {
            debug("Failed to create output folder.");
            return null;
        }
        saveInputStreamToFile(downloadImage(key), outputFile);
        return outputFile.getAbsolutePath();
    }

    public synchronized InputStream downloadImage(String key) {
        int maxRetries = 30;
        int attempt = 1;

        while (attempt <= maxRetries) {
            try {
                List<Message> messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(outboxQueueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(10)
                        .visibilityTimeout(10)
                        .build()).messages();

                if (messages.isEmpty()) {
                    debug("No messages received from outbox queue");
                    return null;
                }

                debug("Received " + messages.size() + " messages from outbox queue");

                for (Message message : messages) {
                    if (message.body().contains(key)) {

                        InputStream inputStream = null;

                        try {
                            inputStream = s3.getObjectAsBytes(GetObjectRequest.builder()
                                    .bucket(bucketName)
                                    .key("processed-" + key)
                                    .build()).asInputStream();
                            debug("Downloaded processed image.");
                        } catch (Exception e) {
                            debug("Failed to download processed image.");
                            break;
                        }

                        try {
                            // Clean up outbox queue
                            sqs.deleteMessage(DeleteMessageRequest.builder()
                                    .queueUrl(outboxQueueUrl)
                                    .receiptHandle(message.receiptHandle())
                                    .build());
                            debug("Deleted message from outbox queue.");
                        } catch (Exception e) {
                            debug("Failed to delete message from outbox queue.");
                        }

                        try {
                            // Clean up S3
                            s3.deleteObject(deleteObjectRequest -> deleteObjectRequest.bucket(bucketName).key("processed-" + key));
                            debug("Deleted processed image from S3.");
                        } catch (Exception e) {
                            debug("Failed to delete processed image from S3.");
                        }

                        return inputStream; // Return the stream to the caller (caller must close it)
                    }
                }
            } catch (Exception e) {
                debug("Failed during download process: " + e.getMessage());
            } finally {
                attempt++;
            }
        }
        debug("Failed to process the image after " + maxRetries + " attempts.");
        return null;
    }

    private boolean createFolders(String directoryName) {
        File directory = new File(directoryName);
        if (!directory.exists()) {
            try {
                directory.mkdirs();
                debug("Created directory: " + directoryName);
            } catch (SecurityException e) {
                debug("Failed to create directory: " + directoryName);
                return false;
            }
        }
        return true;
    }

    private void deleteFile(String fileName) {
        File file = new File(fileName);
        if (file.exists()) {
            file.delete();
        }
    }

    private InputStream fileToInputStream(File file) {
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    private void saveInputStreamToFile(InputStream inputStream, File file) {
        // Save uploaded file to disk
        debug("Saving uploaded file to disk: " + file.getAbsolutePath());
        try {
            Files.copy(inputStream, file.toPath());
            debug("Saved uploaded file to disk: " + file.getAbsolutePath());
        } catch (IOException e) {
        }
    }

    private String getExtension(String fileName) {
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    private static void debug(String message) {
        System.out.println("ImageUploadClient: " + message);
    }

    // for testing purpose only
    public static void main(String[] args) {

        final String inputFolder = "assets/images/raw/";
        final String outputFolder = "assets/images/processed/";

        ImageUploadClient client = new ImageUploadClient();

        // suppose the image already put in the input folder (assets/images/raw/)
        String fileName = "wallhaven-d6mpll.png";

        String key = client.uploadImage(fileName, inputFolder);
        debug("Uploaded image with key: " + key);

        String filePath = client.downloadImage(fileName, outputFolder, key);
        debug("Downloaded processed image to: " + filePath);
    }
}