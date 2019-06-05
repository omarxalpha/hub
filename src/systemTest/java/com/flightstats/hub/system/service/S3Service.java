package com.flightstats.hub.system.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.util.IOUtils;
import com.flightstats.hub.model.ChannelStorage;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

@Slf4j
public class S3Service {
    private final String testData;
    private final AmazonS3 s3Client;
    private final String bucketName;
    private final String hubBaseUrl;

    @Inject
    public S3Service(AmazonS3 s3Client, @Named("hub.url") String hubBaseUrl, @Named("s3.bucket.name") String bucketName, @Named("test.data") String testData) {
        this.s3Client = s3Client;
        this.hubBaseUrl = hubBaseUrl;
        this.bucketName = bucketName;
        this.testData = testData;
    }

    @SneakyThrows
    private byte[] getS3BatchedItems(String path) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, path);
        S3Object obj = s3Client.getObject(getObjectRequest);
        try (S3ObjectInputStream content = obj.getObjectContent()) {
            return handleZip(content);
        }
    }

    @SneakyThrows
    private byte[] getS3Items(String path) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, path);
        S3Object obj = s3Client.getObject(getObjectRequest);
        ObjectMetadata metadata = s3Client.getObjectMetadata(bucketName, path);
        try (S3ObjectInputStream content = obj.getObjectContent()) {
            if (metadata.getUserMetadata().containsKey("compressed")) {
                return handleZip(content);
            }
            return IOUtils.toByteArray(content);
        }
    }

    @SneakyThrows
    private byte[] handleZip(InputStream read) {
        try (ZipInputStream zipStream = new ZipInputStream(read)) {
            byte[] buffer = new byte[zipStream.available()];
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            while (zipStream.getNextEntry() != null) {
                while (zipStream.read(buffer) > 0) {
                    bos.write(buffer, 0, buffer.length);
                }
            }
            return bos.toByteArray();
        }
    }

    private String stripSecondsAndKey(String path) {
        String[] pathParts = path.split("/");
        String newPath = Arrays.asList(pathParts)
                .subList(0, 5)
                .stream()
                .map(str -> str.concat("/"))
                .collect(Collectors.joining());
        return newPath.substring(0, newPath.length() - 1);
    }

    private String formatS3BatchItemPath(String fullPath, String channelName) {
        String base = fullPath != null ? fullPath
                .replace(hubBaseUrl, "")
                .replace("/channel/", "")
                .replace(bucketName, "")
                .replace(channelName + "/", "") : "";
        return channelName + "Batch/items/" + stripSecondsAndKey(base);
    }

    private String formatS3SingleItemPath(String fullPath) {
        return fullPath != null ? fullPath
                .replace(hubBaseUrl + "/channel/", "") : "";
    }

    public boolean confirmItemsInS3(ChannelStorage storage, String fullPath, String channelName) {
        try {
            String path;
            byte[] result;
            if (storage.equals(ChannelStorage.SINGLE)) {
                path = formatS3SingleItemPath(fullPath);
                result = getS3Items(path);
            } else {
                path = formatS3BatchItemPath(fullPath, channelName);
                result = getS3BatchedItems(path);
            }
            String actual = new String(result, StandardCharsets.UTF_8);
            if (!actual.contains(testData)) {
                log.error("actual {}, testData {}", actual, testData);
                throw new Error("actual does not match expected");
            }
            return true;
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() != 404) {
                log.error("error getting item from s3 {}", e.getMessage());
                throw e;
            }
            return false;
        }
    }
}
