package org.wso2.policy.migrator;

import org.apache.axis2.client.Options;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.transport.http.HttpTransportProperties;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.impl.llom.factory.OMLinkedListImplFactory;
import org.wso2.carbon.registry.resource.stub.ResourceAdminServiceStub;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.axiom.soap.SOAP11Constants;
import org.wso2.carbon.registry.resource.stub.beans.xsd.CollectionContentBean;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.io.InputStream;


public class commonPolicyMigrator {
    private static String USERNAME = "admin";
    //service_url
    private static String PASSWORD = "admin";
    private static String TOKEN;
    private static String HOST32 = null;
    private static String PORT32 = null;
    private static String HOST42 = null;
    private static String PORT42 = null;

    private static String PATH = null;
    private static String TRUSTSTORE_PATH = null;
    private static String TRUSTSTORE_PASSWORD = null;


    public static void main(String arg[]) {
        readEnvironmentSpecificProperties();
        getCommonPolicyListForFlow();
    }

    private static void readEnvironmentSpecificProperties() {
        // Define the path to the properties file
        String propertiesFilePath = "file.properties";

        // Create a Properties object
        Properties properties = new Properties();

        try (InputStream input = new FileInputStream(propertiesFilePath)) {
            // Load the properties file
            properties.load(input);

            // Retrieve the property values
            TOKEN = properties.getProperty("token");
            HOST32 = properties.getProperty("3_2_host");
            PORT32 = properties.getProperty("3_2_port");
            HOST42 = properties.getProperty("4_2_host");
            PORT42 = properties.getProperty("4_2_port");
            USERNAME = properties.getProperty("3_2_username");
            PASSWORD = properties.getProperty("3_2_password");
            PATH = properties.getProperty("temp_file_path");
            TRUSTSTORE_PATH = properties.getProperty("trustStore_path");
            TRUSTSTORE_PASSWORD = properties.getProperty("trustStore_password");
        } catch (IOException ex) {
            System.out.println("File cannot be found "+ propertiesFilePath);
            ex.printStackTrace();
        }
    }

    public static void getCommonPolicyList(String flow) {
        try {
            // Register a custom protocol to use the provided SSL certificate
            Protocol httpsProtocol = new Protocol("https", (ProtocolSocketFactory) new CustomSSLProtocolSocketFactory(), 9443);
            Protocol.registerProtocol("https", httpsProtocol);
            String SERVICE_URL = "https://" + HOST32 + ":" + PORT32 + "/services/ResourceAdminService.ResourceAdminServiceHttpsSoap11Endpoint";
            // Initialize the service stub
            ResourceAdminServiceStub stub = new ResourceAdminServiceStub(SERVICE_URL);

            // Set up authentication (basic auth)
            ServiceClient client = stub._getServiceClient();
            Options options = client.getOptions();
            HttpTransportProperties.Authenticator auth = new HttpTransportProperties.Authenticator();
            auth.setUsername(USERNAME);
            auth.setPassword(PASSWORD);
            auth.setPreemptiveAuthentication(true);
            options.setProperty(HTTPConstants.AUTHENTICATE, auth);

            // Set additional headers
            options.setProperty(HTTPConstants.CHUNKED, "false"); // Disable chunking
            options.setSoapVersionURI(SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI);
            options.setAction("urn:getCollectionContent");

            // Example: Get collection content
            String collectionPath = "/_system/governance/apimgt/customsequences/" + flow;
            CollectionContentBean collectionContent = stub.getCollectionContent(collectionPath);

            // Process the response
            if (collectionContent != null) {
                //System.out.println("Collection Path: " + collectionContent.getCollectionPath());
                String[] childPaths = collectionContent.getChildPaths();
                if (childPaths != null) {
                    for (String path : childPaths) {
                        System.out.println("Child Path: " + path);
                        String fileName = path.substring(path.lastIndexOf("/") + 1);
                        System.out.println("file name " + fileName);
                        if (!isInSkipList(fileName)) {
                            try {
                                // Create the service client
                                client = new ServiceClient();
                                client.setTargetEPR(new EndpointReference(SERVICE_URL));

                                // Set authentication headers (Basic Auth)
                                //String authHeader = "Basic " + Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes());
                                auth = new HttpTransportProperties.Authenticator();
                                auth.setUsername(USERNAME);
                                auth.setPassword(PASSWORD);
                                auth.setPreemptiveAuthentication(true);
                                options = client.getOptions();
                                options.setProperty(HTTPConstants.AUTHENTICATE, auth);
                                options.setTo(new EndpointReference(SERVICE_URL));
                                options.setAction("urn:getTextContent");

                                // Create the SOAP request payload
                                OMFactory factory = new OMLinkedListImplFactory();
                                OMNamespace ns = factory.createOMNamespace("http://services.resource.registry.carbon.wso2.org", "ns");

                                OMElement getTextContentElement = factory.createOMElement("getTextContent", ns);
                                OMElement resourcePathElement = factory.createOMElement("path", ns);
                                resourcePathElement.setText("/_system/governance/apimgt/customsequences/" + flow + "/" + fileName);  // Change this path as needed
                                getTextContentElement.addChild(resourcePathElement);

                                // Send the request and get the response
                                OMElement response = client.sendReceive(getTextContentElement);
                                // Process the response
                                String fileContent = response.getFirstElement().getText();
                                String policyName = null;

                                try {
                                    // Parse the XML content
                                    DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
                                    fac.setNamespaceAware(true); // Enable namespace awareness
                                    DocumentBuilder builder = fac.newDocumentBuilder();
                                    Document document = builder.parse(new java.io.ByteArrayInputStream(fileContent.getBytes()));

                                    // Locate the <sequence> element by namespace and tag name
                                    Element sequenceElement = (Element) document.getElementsByTagNameNS("http://ws.apache.org/ns/synapse", "sequence").item(0);

                                    // Extract the 'name' attribute
                                    if (sequenceElement != null) {
                                        policyName = sequenceElement.getAttribute("name");
                                        System.out.println("  name =" + policyName);
                                    }
                                } catch (Exception e) {
                                    System.out.println("Error while parsing the file content");
                                    e.printStackTrace();
                                }
                                generatePolicyDefinitionFile(fileContent, policyName);
                                generatePolicySpecificationFile(policyName, flow);
                                uploadCommonPolicy(policyName);


                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } else {
                    System.out.println("No child paths found.");
                }
            } else {
                System.out.println("Collection not found at path: " + collectionPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void getCommonPolicyListForFlow() {
        String[] flows = {"in", "out", "fault"};
        for (String flow : flows) {
            getCommonPolicyList(flow);
        }
    }

    public static boolean isInSkipList(String fileName) {
        String[] sequences = {"json_to_xml_in_message.xml", "json_validator.xml", "preserve_accept_header.xml", "debug_in_flow.xml", "disable_chunking.xml", "log_in_message.xml", "regex_policy.xml", "xml_to_json_in_message.xml", "xml_validator.xml", "debug_json_fault.xml", "json_fault.xml", "apply_accept_header.xml", "debug_out_flow.xml", "disable_chunking.xml", "json_to_xml_out_message.xml", "log_out_message.xml", "xml_to_json_out_message.xml"};
        for (String seqName : sequences) {
            if (seqName.equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    public static void uploadCommonPolicy(String policyName) {
        String token = TOKEN;  // Your Bearer token
        String urlStr = "https://" + HOST42 + ":" + PORT42 + "/api/am/publisher/v4/operation-policies";

        File policySpecFile = new File(PATH + policyName + "_spec.yaml");
        File policyDefinitionFile = new File(PATH + policyName + ".j2");

        try {
            // Load the custom truststore
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream trustStoreStream = new FileInputStream(TRUSTSTORE_PATH)) {
                trustStore.load(trustStoreStream, TRUSTSTORE_PASSWORD.toCharArray());
            }
            // Initialize the TrustManagerFactory with the custom truststore
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            // Create an SSLContext with the custom truststore
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

            // Define boundary
            String boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";

            // Set the default SSL context
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set request method to POST
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);

            // Set headers
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            // Open output stream to send data
            try (OutputStream outputStream = connection.getOutputStream()) {
                // Write policySpecFile
                writeFormFile(outputStream, boundary, "policySpecFile", policySpecFile, "text/yaml");

                // Write policyDefinitionFile
                writeFormFile(outputStream, boundary, "synapsePolicyDefinitionFile", policyDefinitionFile, "application/j2");

                // End the multipart form data
                outputStream.write(("--" + boundary + "--\r\n").getBytes());
            }

            // Send the request and get the response code
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            // Handle the response (optional)
            if (responseCode == HttpURLConnection.HTTP_CREATED) {
                System.out.println("Policy " + policyName + " created successfully.");
            } else {
                System.out.println("Failed to create policy " + policyName);
            }
            Files.delete(policySpecFile.toPath());
            Files.delete(policyDefinitionFile.toPath());

            connection.disconnect();
        } catch (Exception e) {
            System.out.println("Error occurred while uploading the policy"+policyName);
            e.printStackTrace();
        }

    }

    public static void generatePolicyDefinitionFile(String fileContent, String policyName) {
        String extractedFileContent = fileContent.replaceAll("<sequence[^>]*>", "")
                .replaceAll("</sequence>", "");
        System.out.println("File Content: " + extractedFileContent);
        String j2Content = extractedFileContent;
        File defFile = new File(Paths.get(PATH).toFile(), policyName + ".j2");
        // Write the extracted content to the temporary file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(defFile))) {
            writer.write(j2Content);
        } catch (IOException e) {
            System.out.println("Error while creating the policy definition file");
            throw new RuntimeException(e);
        }
    }

    private static void writeFormFile(OutputStream outputStream, String boundary, String fieldName, File file, String contentType) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"").append(fieldName).append("\"; filename=\"").append(file.getName()).append("\"\r\n");
        sb.append("Content-Type: ").append(contentType).append("\r\n\r\n");
        outputStream.write(sb.toString().getBytes());

        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        outputStream.write("\r\n".getBytes());
    }

    public static void generatePolicySpecificationFile(String policyName, String flow) {
        String templatePath = "spec_template.yaml";
        String outputPath = PATH + policyName + "_spec.yaml";
        String applicableFlow = null;
        if (flow.equals("in")) {
            applicableFlow = "request";
        } else if (flow.equals("out")) {
            applicableFlow = "response";
        } else if (flow.equals("fault")) {
            applicableFlow = "fault";
        }
        try {
            String content = new String(Files.readAllBytes(Paths.get(templatePath)));
            content = content.replace("${Name}", policyName)
                    .replace("${version}", "v1")
                    .replace("${DisplayName}", policyName)
                    .replace("${applicableFlow}", applicableFlow);
            Files.write(Paths.get(outputPath), content.getBytes(), StandardOpenOption.CREATE);
        } catch (IOException e) {
            System.out.println("Error while creating policy specification file");
            throw new RuntimeException(e);
        }


    }

    public static void generateToken() {

    }
}
