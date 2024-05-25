# Image Processing Application with AWS

## Project Description

This project is an image processing application leveraging AWS services, including S3, SQS, and EC2. It consists of a client application for image uploading, an EC2 worker application for image processing, and a web interface for user interactions. The web interface is powered by a Java servlet that handles HTTP requests and integrates seamlessly with AWS services.

## Features

- **Image Uploading**: Users can upload images through a web interface.
- **Image Processing**: Images are processed on an AWS EC2 instance using ImageMagick.
- **Result Retrieval**: Users can view and download the processed images.

## Architecture Overview

1. **Client Application**: A Java-based uploader that sends images to an AWS S3 bucket and posts messages to an SQS queue.
2. **EC2 Worker Application**: Listens for messages in the SQS queue, processes the images using ImageMagick, and uploads the processed images back to S3.
3. **Web Server**: Deployed on Apache Tomcat, it encapsulates the client application and uses Java Servlets to handle HTTP requests and serve the web application efficiently.
4. **Web Frontend**: A simple HTML interface hosted on Apache, allowing users to upload images for processing.

## Technologies Used

- AWS S3
- AWS SQS
- AWS EC2
- Java
- ImageMagick
- Apache Tomcat
- Apache HTTP Server
- Java Servlets

## Setup and Deployment

- Please refer to the comprehensive guide provided in the project's documentation for details on setting up and deploying the application components on AWS.
- Click [here](doc/setup.md) to access the setup guide.