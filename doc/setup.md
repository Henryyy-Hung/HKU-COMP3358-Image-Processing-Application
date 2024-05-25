# COMP3358 Assignment 4: Image Processing Application

## 1. Overview

### 1.1 AWS Application Overview

1. **Client Application (Uploader):**

    - A Java program that uploads an image to the S3 bucket, sends a message to the "inbox" queue with the S3 key of the uploaded image, and polls the "outbox" queue for a response. Once the processed image key is received, it downloads the image from S3.

2. **EC2 Worker Application:**

    - A Java program that runs on the EC2 instance. This program should continuously listen for new messages in the "inbox" queue, download the raw image from S3, process the images using ImageMagick, upload the processed images back to S3, and send a message with the new image key to the "outbox" queue.

3. **Web Server**

    - Encapsulate the client application, deploy in Tomcat to handle HTTP requests from users.

4. **Web Frontend**

    - A simple website where users can upload images to be processed, deployed on Apache.

### 1.2 Resources Involved

**AWS S3 Bucket**, **AWS SQS Queues**, **AWS EC2 Instances**

## 2. Integration with AWS SDK

### Step 1: Update build.gradle.kts

First, let’s update your `build.gradle.kts` file to include additional dependencies for AWS SDK components such as Amazon SQS and EC2 (if needed for any direct EC2 interactions). Here’s how you can modify your file:

```kotlin
plugins {
    id("java")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("software.amazon.awssdk:s3:2.17.89")
    implementation("software.amazon.awssdk:sqs:2.17.89")
    implementation("software.amazon.awssdk:ec2:2.17.89")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
```

### Step 2: Configure AWS Credentials

Before writing the application, ensure your AWS credentials are properly configured. You can set up your credentials using the AWS CLI or by creating a `credentials` file at `~/.aws/credentials` (Linux & macOS) or `C:\Users\USERNAME\.aws\credentials` (Windows) with the following content:

```
[default]
aws_access_key_id = YOUR_ACCESS_KEY
aws_secret_access_key = YOUR_SECRET_KEY
```

## 3. Set up Necessary Resources on EC2 Instance

### 3.1 Install ImageMagick (for image processing)

```bash
sudo yum install ImageMagick -y
```

### 3.2 Install Apache

Install and initialize Apache server.

```bash
sudo yum install httpd -y  # For Amazon Linux 2
sudo systemctl start httpd
sudo systemctl enable httpd
```

Check status and log of Apache server.

```bash
sudo systemctl status httpd
```

### 3.2 Install Tomcat

Install and initialize Tomcat.

```bash
sudo yum install tomcat9 -y  # For Amazon Linux 2
sudo systemctl start tomcat9
sudo systemctl enable tomcat9
```

Check status and log of Tomcat.

```bash
sudo systemctl status tomcat9 # Check the status of Tomcat
```

## 4. Enable Public Access to Your EC2 Instance

Ensure that the security group associated with your EC2 instance allows inbound traffic on port 8080. Here's how to check and modify the security group:

1. **Go to the AWS Management Console** and navigate to the EC2 dashboard.
2. **Select Instances** and then click on the instance you're using.
3. **Look for the Security Groups** associated with the instance in the description tab.
4. **Click on the Security Group ID** to go to its settings.
5. **Edit Inbound Rules**:
    - Ensure there's a rule allowing TCP traffic on port 8080.
    - The source can be set to `0.0.0.0/0` for public access.

## 5. Build Jar

### 5.1 Configure Your Project for Building a Fat JAR

Add the Shadow Plugin to `build.gradle.kts` (if using Gradle Kotlin DSL). This plugin helps in creating a fat JAR by bundling all dependencies into a single JAR file. Below is the settins:

```kotlin
plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("application")
}

// Define the main class for the application
application {
    mainClass.set("com.yourpackage.MainClass")
}

// Configure the Shadow JAR settings
tasks.shadowJar {
    archiveBaseName.set("YourAppName")
    archiveVersion.set("0.1")
    archiveClassifier.set("")
}
```

Replace `"com.yourpackage.MainClass"` with the fully qualified name of your main class in the project.

### 5.2 Build the Fat JAR

1. **Open the Gradle Tool Window in IntelliJ:**

- You can find it on the right side of the IDE or via `View > Tool Windows > Gradle`.

2. **Run the ShadowJar Task:**

- In the Gradle tool window, navigate to `Tasks > shadow > shadowJar`.
- Double-click `shadowJar` to run this task. This will generate a fat JAR in the `build/libs` directory of your project.

### 5.3 Locate the JAR

- Navigate to the `build/libs` directory in your project folder. You should see a file named `YourAppName-0.1.jar` or similar, depending on how you configured the task.

### 5.4 Run the JAR

- Once the JAR is built, you can test it by running:

  ```bash
  java -jar path/to/your/YourAppName-0.1.jar
  ```

- This should execute your application if everything is set up correctly.

## 6. Configure AWS Credentials on EC2

1. **Check if AWS Credentials File Exists**

- On your EC2 instance, the AWS credentials file should typically be located at `~/.aws/credentials`. You can check if the file exists and has content by running:
  ```bash
  cat ~/.aws/credentials
  ```
- The output should look something like this:
  ```
  [default]
  aws_access_key_id = YOUR_ACCESS_KEY
  aws_secret_access_key = YOUR_SECRET_KEY
  ```

2. **Create or Update the AWS Credentials File**

- If the file doesn’t exist or is incorrect, you’ll need to create or update it. You can use a text editor like `nano` or `vim`:
  ```bash
  mkdir -p ~/.aws  # Ensure the .aws directory exists
  nano ~/.aws/credentials
  ```
- Then, add or update the file to include your AWS credentials:
  ```
  [default]
  aws_access_key_id = YOUR_ACCESS_KEY
  aws_secret_access_key = YOUR_SECRET_KEY
  ```

3. **Allow the Tomcat Web App To Access the AWS Credentials File**

- provide a copy of .aws credentials on to tomecat9

  ```bash
  sudo cp -r ~/.aws /usr/share/tomcat9/
  ```

## 6. Run Image Processor on EC2 with Worker

Use `systemd` to create a service. This allows the system to manage your Java application as a service, which can start automatically on system boots, restart on failure, and more.

Here’s a basic example of a `systemd` service file:

1. **Create a service file:**

   ```bash
   sudo nano /etc/systemd/system/imageprocessor.service
   ```

2. **Add the following configuration:**

   Suppose your `.jar` file placed on `/home/ec2-user/ImageProcessingServer-0.1.jar`.

   ```ini
   [Unit]
   Description=Image Processing Server
   After=network.target

   [Service]
   User=ec2-user
   WorkingDirectory=/home/ec2-user
   ExecStart=/usr/bin/java -jar /home/ec2-user/ImageProcessingServer-0.1.jar
   SuccessExitStatus=143
   TimeoutStopSec=10
   Restart=on-failure
   RestartSec=5

   [Install]
   WantedBy=multi-user.target
   ```

3. **Enable and start the service:**

   ```bash
   sudo systemctl enable imageprocessor.service
   sudo systemctl start imageprocessor.service
   ```

4. **Check the status of the service:**
   ```bash
   sudo systemctl status imageprocessor.service
   sudo journalctl -u imageprocessor.service
   ```

## 7. Deploy the Web Application to Tomcat

- You should develop a servlet web application that can be deployed on Tomcat. The application should be able to handle HTTP requests, process images, and interact with AWS services.
- Then, pack the application into a WAR file and upload it to EC2.
- Suppose you have put your war at ~ directory.

    ```bash
    sudo mv ~/MyWebApp-1.0.0.war /var/lib/tomcat9/webapps/
    ```
    
    ```bash
    sudo systemctl restart tomcat9
    ```

## 8. Deploy Frontend on Apache and Tomcat

- Add Index.html to Apache server

    ```bash
    sudo vi /var/www/html/index.html
    ```

- Add Index.html to Tomcat server root

    ```bash
    cd /var/lib/tomcat9/webapps/
    mkdir ROOT
    sudo vi /var/lib/tomcat9/webapps/ROOT/index.html
    ```

## 9. Ending Notes

- 都看到这了，说一句谢谢学长不过分吧，我亲爱的好学弟/学妹。`(๑*◡*๑)`
- 这门课可真是有够难的！