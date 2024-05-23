package com.henryhung.aws;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.henryhung.utils.ImageUtils;

public class ImageProcessingServer {

    private final String bucketName = "bucket-comp3358-3035782750";
    private final String inboxQueueUrl = "inbox-queue-comp3358-3035782750";
    private final String outboxQueueUrl = "outbox-queue-comp3358-3035782750";

    private final Region region;

    private final S3Client s3;
    private final SqsClient sqs;

    private final String inputFolder = "assets/images/tmp/raw/";
    private final String outputFolder = "assets/images/tmp/processed/";

    public ImageProcessingServer() {
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

        try {
            createFolders(inputFolder);
            createFolders(outputFolder);
            debug("Created input and output folders");
        } catch (Exception e) {
            debug("Failed to create input and output folders");
            throw e;
        }

        try{
            sqs.purgeQueue(purgeQueueRequest -> purgeQueueRequest.queueUrl(inboxQueueUrl));
            sqs.purgeQueue(purgeQueueRequest -> purgeQueueRequest.queueUrl(outboxQueueUrl));
            debug("Cleared inbox and outbox queues");
        } catch (Exception e) {
            debug("Failed to clear inbox and outbox queues");
            e.printStackTrace();
        }
    }

    private void processImages() {
        while (true) {
            List<Message> messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(inboxQueueUrl)
                    .maxNumberOfMessages(1)
                    .waitTimeSeconds(10)
                    .build()).messages();

            if (messages.isEmpty()) {
                debug("No messages received from inbox queue");
                continue;
            }

            debug("Received " + messages.size() + " messages from inbox queue");

            for (Message message : messages) {
                String key = message.body();
                String outputKey = "processed-" + key;
                debug("Received Image: " + key);

                File inputFile = new File(inputFolder + key);
                File outputFile = new File(outputFolder + key);
                deleteFile(inputFile.getAbsolutePath());
                deleteFile(outputFile.getAbsolutePath());

                debug("Downloading image from S3");
                try {
                    // Download the image from S3
                    s3.getObject(GetObjectRequest.builder()
                                    .bucket(bucketName)
                                    .key(key)
                                    .build(),
                            Paths.get(inputFile.getAbsolutePath()));
                    debug("Downloaded image from S3");
                } catch (Exception e) {
                    e.printStackTrace();
                    debug("Failed to download image from S3");
                    continue;
                }

                // For development only
//                try{
//                    ImageUtils.resizeImage(inputFile, outputFile, 512, 512);
//                    debug("Processed image");
//                } catch (Exception e) {
//                    debug("Failed to process image");
//                    e.printStackTrace();
//                    continue;
//                }

                debug("Processing image");
                try {
                    ProcessBuilder builder = new ProcessBuilder(
                            "convert",
                            inputFile.getAbsolutePath(),
                            "-resize", "512x512",
                            "-background", "white",
                            "-gravity", "center",
                            "-extent", "512x512",
                            outputFile.getAbsolutePath()
                    );
                    Process process = builder.start();

                    // Wait for the process to complete with a timeout
                    if (!process.waitFor(60, TimeUnit.SECONDS)) {
                        // Timeout elapsed before termination, kill the process
                        process.destroyForcibly();
                        debug("Failed to process image: timeout");
                        continue;
                    }

                    // Check if the process terminated unsuccessfully
                    if (process.exitValue() != 0) {
                        debug("Failed to process image: " + process.exitValue());
                        continue;
                    }

                    debug("Processed image");
                } catch (Exception e) {
                    e.printStackTrace();
                    debug("Failed to process image");
                    continue;
                }

                debug("Uploading processed image to S3");
                try {
                    // Upload the processed image back to S3
                    s3.putObject(PutObjectRequest.builder()
                                    .bucket(bucketName)
                                    .key(outputKey)
                                    .build(),
                            RequestBody.fromFile(Paths.get(outputFile.getAbsolutePath())));
                    debug("Uploaded processed image to S3");
                } catch (Exception e) {
                    e.printStackTrace();
                    debug("Failed to upload processed image to S3");
                    continue;
                }

                debug("Sending message to outbox queue");
                try {
                    // Send a message to the outbox queue
                    sqs.sendMessage(SendMessageRequest.builder()
                            .queueUrl(outboxQueueUrl)
                            .messageBody(outputKey)
                            .delaySeconds(1)
                            .build());
                    debug("Sent message to outbox queue");
                } catch (Exception e) {
                    e.printStackTrace();
                    debug("Failed to send message to outbox queue");
                    continue;
                }

                debug("Deleting message from inbox queue");
                try {
                    // Delete the message from the inbox queue
                    sqs.deleteMessage(DeleteMessageRequest.builder()
                            .queueUrl(inboxQueueUrl)
                            .receiptHandle(message.receiptHandle())
                            .build());
                    debug("Deleted message from inbox queue");
                } catch (Exception e) {
                    e.printStackTrace();
                    debug("Failed to delete message from inbox queue");
                    continue;
                }

                debug("Deleting image from S3 and local files");
                try {
                    // Delete the image from the S3 bucket
                    s3.deleteObject(deleteObjectRequest -> deleteObjectRequest.bucket(bucketName).key(key));
                    debug("Deleted image from S3");
                } catch (Exception e) {
                    e.printStackTrace();
                    debug("Failed to delete image from S3");
                    continue;
                }

                debug("Deleting local files");
                try {
                    // Delete the local files
                    deleteFile(inputFile.getAbsolutePath());
                    deleteFile(outputFile.getAbsolutePath());
                } catch (Exception e) {
                    e.printStackTrace();
                    debug("Failed to delete local files");
                }
            }
        }
    }

    private void createFolders(String directoryName) {
        File directory = new File(directoryName);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    private void deleteFile(String fileName) {
        File file = new File(fileName);
        if (file.exists()) {
            file.delete();
        }
    }

    private static void debug(String message) {
        System.out.println("ImageProcessingServer: " + message);
    }

    public static void main(String[] args) {
        ImageProcessingServer server = new ImageProcessingServer();
        server.processImages();
    }
}