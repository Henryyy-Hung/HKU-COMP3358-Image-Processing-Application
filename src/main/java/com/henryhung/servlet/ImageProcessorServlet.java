package com.henryhung.servlet;

import com.henryhung.aws.ImageUploadClient;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@WebServlet("/processImage")
@MultipartConfig(fileSizeThreshold=1024*1024*5, maxFileSize=1024*1024*10, maxRequestSize=1024*1024*20) // 100MB
public class ImageProcessorServlet extends HttpServlet {

    private ImageUploadClient uploadClient = new ImageUploadClient();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        debug("received a GET request");
        setAccessControlHeaders(resp);
        resp.getWriter().write("The /processImage endpoint is running.");
        if (uploadClient == null) {
            resp.getWriter().write("The uploadClient is absent.");
        } else {
            resp.getWriter().write("The uploadClient is functional.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        debug("Received a POST request");
        // allow cross-origin requests
        setAccessControlHeaders(resp);
        Part filePart;
        try {
            filePart = req.getPart("image");
        } catch (Exception e) {
            resp.getWriter().write("Failed to get image part.");
            return;
        }
        if (filePart == null) {
            resp.getWriter().write("Image part is missing.");
            return;
        }

        String fileName = filePart.getSubmittedFileName();
        debug("Received image file: " + fileName);

        // Upload the image using ImageUploadClient
        String key;
        try (InputStream fileContent = filePart.getInputStream()) {
            key = uploadClient.uploadImage(fileName, fileContent);
            if (key == null) {
                resp.getWriter().write("Failed to upload image to S3.");
                debug("Failed to upload image to S3.");
                return;
            }
        }

        // Download the processed image as InputStream
        try (InputStream processedImageStream = uploadClient.downloadImage(key)) {
            if (processedImageStream == null) {
                resp.getWriter().write("Failed to download processed image from S3.");
                debug("Failed to download processed image from S3.");
                return;
            }

            // Set the response content type and headers
            resp.setContentType(getServletContext().getMimeType(fileName));
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + key + "\"");

            // Write the processed image content to the response output stream
            try (OutputStream out = resp.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = processedImageStream.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }
        }
    }

    private void debug(String message) {
        System.out.println("ImageProcessorServlet: " + message);
    }

    private void setAccessControlHeaders(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE, HEAD");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }
}