package com.paddi.utils;

import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * @Project: minio-demo
 * @Author: Paddi-Yan
 * @CreatedTime: 2022年11月30日 15:48:50
 */
@Slf4j
@Component
public class MinioUtil {

    @Autowired
    private MinioClient client;

    @SneakyThrows
    private void createBucket(String bucketName) {
        BucketExistsArgs bucketExistsArgs = BucketExistsArgs.builder().bucket(bucketName).build();
        if(!client.bucketExists(bucketExistsArgs)) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    public Map<String, String> upload(MultipartFile file, String bucketName) throws Exception {
        if(file == null || file.getSize() == 0) {
            throw new Exception("文件为空或者损坏,上传失败");
        }
        createBucket(bucketName);
        String originalFilename = file.getOriginalFilename();
        Map<String, String> result = new HashMap<>(2);
        String newFileName = bucketName + "-" + System.currentTimeMillis() + "-" + originalFilename;
        InputStream in = null;
        try {
            in = file.getInputStream();
            client.putObject(PutObjectArgs.builder().bucket(bucketName)
                                                    .contentType(file.getContentType())
                                                    .object(newFileName)
                                                    .stream(in, in.available(), -1)
                                                    .build());
            MinioUtil.log.info("文件上传成功:{}", newFileName);
            if(in != null) {
                in.close();
            }
            result.put("fileName", newFileName);
            String size = FileSizeUnitUtil.readableFileSize(file.getSize());
            result.put("size", size);
            return result;
        } catch(IOException | ServerException | InsufficientDataException | ErrorResponseException |
                NoSuchAlgorithmException | InvalidKeyException | InvalidResponseException | XmlParserException |
                InternalException e) {
            e.printStackTrace();
            log.warn("文件上传失败:{}", newFileName);
        } finally {
            if(in != null) {
                try {
                    in.close();
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }


    public Boolean download(HttpServletResponse response, String fileName, String bucketName) {
        StatObjectResponse stat = null;
        GetObjectResponse in = null;
        try{
            stat = client.statObject(StatObjectArgs.builder()
                                                   .bucket(bucketName)
                                                   .object(fileName)
                                                   .build());
            in = client.getObject(GetObjectArgs.builder()
                                               .bucket(bucketName)
                                               .object(fileName).build());
            String size = FileSizeUnitUtil.readableFileSize(stat.size());
            log.info("下载文件:{}, 文件大小:{}",fileName, size);
            if(in == null) {
                return false;
            }
            response.setContentType(stat.contentType());
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
            IOUtils.copy(in, response.getOutputStream());
            return true;
        } catch(ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException |
                XmlParserException e) {
            e.printStackTrace();
            MinioUtil.log.warn("文件下载失败");
            return false;
        } finally {
            if(in != null) {
                try {
                    in.close();
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public String getPreviewPath(String fileName, String bucketName) {
        try {
            createBucket(bucketName);
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                                                                              .bucket(bucketName)
                                                                              .object(fileName).method(Method.GET).build());
            return url;
        } catch(ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                InvalidResponseException | IOException | NoSuchAlgorithmException | XmlParserException |
                ServerException e) {
            throw new RuntimeException(e);
        }
    }
}
