package org.wso2.policy.migrator;


import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;


import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;


import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.classic.methods.HttpPost;


import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;


import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.*;
import java.nio.file.Files;
import java.security.KeyStore;


public class PolicyUploader {
    private final String token;
    private final String trustStorePath;
    private final String trustStorePassword;
    private final String uploadUrl;
    private final String path;

    public PolicyUploader(String token, String host, String port, String trustStorePath, String trustStorePassword,String path) {
        this.token = token;
        this.trustStorePath = trustStorePath;
        this.trustStorePassword = trustStorePassword;
        this.uploadUrl = "https://" + host + ":" + port + "/api/am/publisher/v4/operation-policies";
        this.path = path;
    }

    public void uploadCommonPolicyHttpClient(String policyName) {
        //String url = "https://" + HOST42 + ":" + PORT42 + "/api/am/publisher/v4/operation-policies";

        File policySpecFile = new File(path + policyName + "_spec.yaml");
        File policyDefinitionFile = new File(path + policyName + ".j2");

        try {
            SSLContext sslContext = getSSLContext(trustStorePath, trustStorePassword);
            try (CloseableHttpClient httpClient = createHttpClient(sslContext)) {
                HttpPost httpPost = createHttpPost(uploadUrl, token, policySpecFile, policyDefinitionFile);
                executeRequest(httpClient, httpPost);
                Files.delete(policySpecFile.toPath());
                Files.delete(policyDefinitionFile.toPath());
            }
        } catch (Exception e) {
            System.err.println("Error during policy upload: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private SSLContext getSSLContext(String trustStorePath, String trustStorePassword) throws Exception {
        try (FileInputStream truststoreStream = new FileInputStream(trustStorePath)) {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(truststoreStream, trustStorePassword.toCharArray());

            return SSLContextBuilder.create()
                    .loadTrustMaterial(trustStore, (TrustStrategy) (chain, authType) -> true)
                    .build();
        }
    }

    private static CloseableHttpClient createHttpClient(SSLContext sslContext) {
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext);
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", sslSocketFactory)
                .build();

        BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager(socketFactoryRegistry);

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();
    }

    private static HttpPost createHttpPost(String url, String token, File policySpecFile, File policyDefinitionFile) {
        HttpPost httpPost = new HttpPost(url);

        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
        entityBuilder.addBinaryBody("policySpecFile", policySpecFile, ContentType.parse("text/yaml"), policySpecFile.getName());
        entityBuilder.addBinaryBody("synapsePolicyDefinitionFile", policyDefinitionFile, ContentType.parse("application/j2"), policyDefinitionFile.getName());

        httpPost.setEntity(entityBuilder.build());
        httpPost.addHeader("Authorization", "Bearer " + token);

        return httpPost;
    }

    private static void executeRequest(CloseableHttpClient httpClient, HttpPost httpPost) {
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int responseCode = response.getCode();
            System.out.println("Response Code: " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_CREATED) {
                System.out.println("Files uploaded successfully.");
            } else {
                System.err.println("Failed to upload files. Response code: " + responseCode);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error during request execution", e);
        }
    }
}
