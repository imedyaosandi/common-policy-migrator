package org.wso2.policy.migrator;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.SecureRandom;

public class CustomSSLProtocolSocketFactory implements ProtocolSocketFactory{
    private SSLContext sslContext = null;
    private static final String KEYSTORE_PATH = "/Users/imedya/Desktop/TICKETS/FISGLOBALSUB-1202/wso2am-3.2.0/repository/resources/security/wso2carbon.jks"; // Path to your keystore
    private static final String KEYSTORE_PASSWORD = "wso2carbon";

    private SSLContext createSSLContext() {
        try {
            // Load the keystore containing the SSL certificate
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream keyStoreStream = new FileInputStream(KEYSTORE_PATH)) {
                keyStore.load(keyStoreStream, KEYSTORE_PASSWORD.toCharArray());
            }

            // Create a TrustManager that trusts the certificates in the KeyStore
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            // Initialize SSLContext with the TrustManager
            SSLContext context = SSLContext.getInstance("SSL");
            context.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
            return context;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSLContext", e);
        }
    }

    private SSLContext getSSLContext() {
        if (this.sslContext == null) {
            this.sslContext = createSSLContext();
        }
        return this.sslContext;
    }

    @Override
    public Socket createSocket(String host, int port, java.net.InetAddress clientHost, int clientPort) throws IOException {
        return getSSLContext().getSocketFactory().createSocket(host, port, clientHost, clientPort);
    }

    @Override
    public Socket createSocket(String s, int i, InetAddress inetAddress, int i1, HttpConnectionParams httpConnectionParams) throws IOException, UnknownHostException, ConnectTimeoutException {
        return createSocket(s, i, inetAddress, i1);
    }

//    @Override
//    public Socket createSocket(String host, int port, java.net.InetAddress clientHost, int clientPort, HttpConnectionManagerParams params) throws IOException {
//        return createSocket(host, port, clientHost, clientPort);
//    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return getSSLContext().getSocketFactory().createSocket(host, port);
    }
}

