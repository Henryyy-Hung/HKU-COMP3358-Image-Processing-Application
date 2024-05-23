package com.henryhung.utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.File;
import java.io.IOException;

public class ImageUtils {

    public static void resizeImage(File inputFile, File outputFile, int width, int height) throws IOException {
        String fileName = inputFile.getName();
        String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1);

        // Read the original image
        BufferedImage originalImage = ImageIO.read(inputFile);
        if (originalImage == null) {
            throw new IOException("The file " + inputFile + " could not be opened, it is not an image or the format is not supported.");
        }

        // Create a new buffered image with desired size
        BufferedImage resizedImage = new BufferedImage(width, height, originalImage.getType());
        Graphics2D g2d = resizedImage.createGraphics();

        // Apply quality rendering settings
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw the resized image
        g2d.drawImage(originalImage, 0, 0, width, height, null);
        g2d.dispose();

        // Write the resized image to the output file
        if (!ImageIO.write(resizedImage, fileExtension, outputFile)) {
            throw new IOException("No appropriate writer found for " + fileExtension);
        }
        System.out.println("Resized image saved to " + outputFile.getAbsolutePath());
    }
}