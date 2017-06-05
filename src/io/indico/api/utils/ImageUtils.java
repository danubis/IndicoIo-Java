package io.indico.api.utils;


import org.imgscalr.Scalr;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;


/**
 * Created by Chris on 6/23/15.
 */
public class ImageUtils {
    public static String encodeImage(BufferedImage image) {
        String imageString = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try {
            ImageIO.write(image, "PNG", bos);
            imageString = Base64.getEncoder()
            		.encodeToString(bos.toByteArray());
            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return imageString;
    }

    @SuppressWarnings("unchecked")
    public static Rectangle getRectangle(Map<String, List<Double>> res) {
        List<Double> topLeft = res.get("top_left_corner");
        List<Double> bottomRight = res.get("bottom_right_corner");

        int top = topLeft.get(1).intValue();
        int left = topLeft.get(0).intValue();
        int bottom = bottomRight.get(1).intValue();
        int right = bottomRight.get(0).intValue();

        return new Rectangle((left + right) / 2, (top + bottom) / 2, right - left, top - bottom);
    }

    public static String convertToImage(Object image, int size, boolean minAxis)
        throws IOException {
        if (image instanceof File) {
            return handleFile((File) image, size, minAxis);
        } else if (image instanceof String) {
            return handleString((String) image, size, minAxis);
        } else if (image instanceof BufferedImage) {
            return handleImage((BufferedImage) image, size, minAxis);
        } else {
            throw new IllegalArgumentException(
                "imageCall method only supports lists of Files and lists of Strings"
            );
        }
    }

    public static List<String> convertToImages(List<?> images, int size, boolean minAxis)
        throws IOException {
        List<String> convertedInput = new ArrayList<>();
        for (Object entry : images) {
            convertedInput.add(convertToImage(entry, size, minAxis));
        }
        return convertedInput;
    }

    final private static Pattern base64_regex = Pattern.compile("^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{4}|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)$");

    public static String handleFile(File filePath, int size, boolean minAxis) throws IOException {
        if (filePath.exists()) {
            // If File
            if (size == -1) { // No Resize
                return ImageUtils.encodeImage(ImageIO.read(filePath));
            }

            return handleImage(ImageIO.read(filePath), size, minAxis);
        } else {
            throw new IllegalArgumentException("File input does not exist " + filePath.getAbsolutePath());
        }
    }

    public static String handleImage(BufferedImage image, int size, boolean minAxis) throws IOException {
        return ImageUtils.encodeImage(resize(image, size, minAxis));
    }

    public static String handleString(String imageString, int size, boolean minAxis) throws IOException {
        BufferedImage image;
        File imageFile = new File(imageString);
        if (imageFile.exists()) {
            return handleFile(imageFile, size, minAxis);
        } else {
            // If URL
            try {
                new URL(imageString);
                return imageString;
            } catch (MalformedURLException malformedURLException) {
                // Check If Base64
                boolean isBase64 = base64_regex.matcher(imageString).matches();
                if (!isBase64) {
                    return imageString;
//                    throw new IllegalArgumentException("Invalid input image. Only file paths, base64 string, and urls are supported");
                }

                image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(imageString)));
                return handleImage(image, size, minAxis);
            }
        }
    }

    public static BufferedImage resize(BufferedImage image, int size, boolean minAxis) {
        double aspect = image.getWidth() / image.getHeight();
        if (aspect >= 10 || aspect <= .1)
            System.out.println("WARNING: We we recommend images with aspect ratio less than 1:10");

        Scalr.Mode method = minAxis ? Scalr.Mode.AUTOMATIC : Scalr.Mode.FIT_EXACT;
        return Scalr.resize(image, method, size);
    }
}
