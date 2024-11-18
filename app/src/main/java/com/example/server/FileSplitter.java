package com.example.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileSplitter {

    /**
     * Splits a text file into equal-sized parts based on the number of subfiles.
     *
     * @param fileName         the name of the input file
     * @param numberOfSubfiles the number of parts to split into
     * @return a list of subfile names
     * @throws IOException if an error occurs during file reading/writing
     */
    public static List<String> splitTextFile(String fileName, int numberOfSubfiles) throws IOException {
        List<String> subfileNames = new ArrayList<>();
        File file = new File(fileName);
        long totalSize = file.length();
        long subfileSize = totalSize / numberOfSubfiles;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            int partNumber = 0;
            long currentSize = 0;

            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
                currentSize += line.getBytes().length;

                if (currentSize >= subfileSize) {
                    String subfileName = fileName + "_part" + partNumber + ".txt";
                    subfileNames.add(subfileName);
                    writeToFile(subfileName, stringBuilder.toString());
                    stringBuilder.setLength(0);
                    currentSize = 0;
                    partNumber++;
                }
            }

            // Add the remaining content as the last subfile
            if (stringBuilder.length() > 0) {
                String subfileName = fileName + "_part" + partNumber + ".txt";
                subfileNames.add(subfileName);
                writeToFile(subfileName, stringBuilder.toString());
            }
        }

        return subfileNames;
    }

    /**
     * Splits a text file proportionally based on the computational capacities of clients.
     *
     * @param fileName   the name of the input file
     * @param capacities a map of client IPs to their computational capacities
     * @throws IOException if an error occurs during file reading/writing
     */
//    public static void splitTextFileBySize(String fileName, Map<String, Double> capacities) throws IOException {
//        File file = new File(fileName);
//        long totalSize = file.length();
//
//        // Calculate total capacity (sum of speeds)
//        double totalCapacity = capacities.values().stream().mapToDouble(Double::doubleValue).sum();
//
//        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
//            String line;
//
//            // Distribute file portions based on capacities
//            for (Map.Entry<String, Double> entry : capacities.entrySet()) {
//                String clientIp = entry.getKey();
//                double speed = entry.getValue();
//
//                long portionSize = (long) ((totalSize * speed) / totalCapacity);
//                File portionFile = new File(fileName + "_" + clientIp + ".part");
//
//                try (PrintWriter writer = new PrintWriter(new FileWriter(portionFile))) {
//                    long writtenBytes = 0;
//
//                    while (writtenBytes < portionSize && (line = reader.readLine()) != null) {
//                        writer.println(line);
//                        writtenBytes += line.getBytes().length + 1; // +1 for newline
//                    }
//                }
//            }
//
//            // Handle leftover content (if any)
//            StringBuilder remainingContent = new StringBuilder();
//            while ((line = reader.readLine()) != null) {
//                remainingContent.append(line).append("\n");
//            }
//            if (remainingContent.length() > 0) {
//                File remainingFile = new File(fileName + "_remaining.part");
//                try (PrintWriter writer = new PrintWriter(new FileWriter(remainingFile))) {
//                    writer.print(remainingContent.toString());
//                }
//            }
//        }
//    }


    public static void splitTextFileBySize(String fileName, Map<String, Double> capacities) throws IOException {
        File file = new File(fileName);
        long totalSize = file.length();

        // Calculate total capacity (sum of speeds)
        double totalCapacity = capacities.values().stream().mapToDouble(Double::doubleValue).sum();

        try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
            // Distribute file portions based on capacities
            for (Map.Entry<String, Double> entry : capacities.entrySet()) {
                String clientIp = entry.getKey();
                double speed = entry.getValue();

                // Calculate the portion size in bytes for this IP
                long portionSize = (long) ((totalSize * speed) / totalCapacity);
                File portionFile = new File(fileName + "_" + clientIp + ".txt");

                try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(portionFile))) {
                    long writtenBytes = 0;
                    byte[] buffer = new byte[8192]; // 8 KB buffer
                    int bytesRead;

                    // Write up to the portion size
                    while (writtenBytes < portionSize
                            && (bytesRead = inputStream.read(buffer, 0, (int) Math.min(buffer.length, portionSize - writtenBytes))) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        writtenBytes += bytesRead;
                    }
                }
            }

            // Handle leftover content (if any)
            File remainingFile = new File(fileName + "_remaining.txt");
            try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(remainingFile))) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        }
    }



    /**
     * Writes content to a file.
     *
     * @param fileName the name of the file to write
     * @param content  the content to write
     * @throws IOException if an error occurs during writing
     */
    private static void writeToFile(String fileName, String content) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(new File(fileName)))) {
            writer.print(content);
        }
    }
}
