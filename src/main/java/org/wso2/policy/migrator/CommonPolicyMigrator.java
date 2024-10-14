package org.wso2.policy.migrator;

import org.apache.axis2.client.Options;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.transport.http.HttpTransportProperties;
import org.apache.axis2.client.ServiceClient;
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
import java.io.ByteArrayInputStream;
import java.util.Arrays;


public class CommonPolicyMigrator {
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

    private static String SKIP_LIST;
    //private static String SOAP_ACTION;
    //PolicyFilesGenerator filesGenerator=new PolicyFilesGenerator(PATH);

    public static void main(String arg[]) {
        loadProperties();
        //readEnvironmentSpecificProperties();
        getCommonPolicyListForFlow();
    }

    private static void loadProperties() {
        PropertyReader reader = new PropertyReader("file.properties");
        TOKEN = reader.getProperty("token");
        HOST32 = reader.getProperty("3_2_host");
        PORT32 = reader.getProperty("3_2_port");
        HOST42 = reader.getProperty("4_2_host");
        PORT42 = reader.getProperty("4_2_port");
        USERNAME = reader.getProperty("3_2_username");
        PASSWORD = reader.getProperty("3_2_password");
        PATH = reader.getProperty("temp_file_path");
        TRUSTSTORE_PATH = reader.getProperty("trustStore_path");
        TRUSTSTORE_PASSWORD = reader.getProperty("trustStore_password");
        SKIP_LIST = reader.getProperty("skip_list");
    }
    public static void getCommonPolicyList(String flow) {
        try {
            //SOAP_ACTION="urn:getCollectionContent";
            ResourceAdminServiceStub stub = initializeServiceStub("urn:getCollectionContent");

            // Example: Get collection content
            String collectionPath = "/_system/governance/apimgt/customsequences/" + flow;
            CollectionContentBean collectionContent = stub.getCollectionContent(collectionPath);
            if (collectionContent != null && collectionContent.getChildPaths() != null) {
                processCollectionContent(stub, flow, collectionContent.getChildPaths());
            } else {
                System.out.println("No child paths found at path: " + collectionPath);
            }
        } catch (Exception e) {
            System.err.println("Error retrieving policy list: " + e.getMessage());
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
        //String[] sequences = {"json_to_xml_in_message.xml", "json_validator.xml", "preserve_accept_header.xml", "debug_in_flow.xml", "disable_chunking.xml", "log_in_message.xml", "regex_policy.xml", "xml_to_json_in_message.xml", "xml_validator.xml", "debug_json_fault.xml", "json_fault.xml", "apply_accept_header.xml", "debug_out_flow.xml", "disable_chunking.xml", "json_to_xml_out_message.xml", "log_out_message.xml", "xml_to_json_out_message.xml"};
        String[] sequences = SKIP_LIST.split(",");
        if (Arrays.asList(sequences).contains(fileName)){
            System.out.println("Sequence "+ fileName + " is in skip list. Not adding to 4.2.0");
            return true;
        }
        return false;
    }

    private static ResourceAdminServiceStub initializeServiceStub(String action) throws Exception {
        // Register the custom protocol for SSL
        Protocol httpsProtocol = new Protocol("https", (ProtocolSocketFactory) new CustomSSLProtocolSocketFactory(), 9443);
        Protocol.registerProtocol("https", httpsProtocol);

        String serviceUrl = "https://" + HOST32 + ":" + PORT32 + "/services/ResourceAdminService.ResourceAdminServiceHttpsSoap11Endpoint";
        ResourceAdminServiceStub stub = new ResourceAdminServiceStub(serviceUrl);

        // Set authentication and request options
        ServiceClient client = stub._getServiceClient();
        Options options = client.getOptions();
        HttpTransportProperties.Authenticator auth = new HttpTransportProperties.Authenticator();
        auth.setUsername(USERNAME);
        auth.setPassword(PASSWORD);
        auth.setPreemptiveAuthentication(true);
        options.setProperty(HTTPConstants.AUTHENTICATE, auth);
        options.setProperty(HTTPConstants.CHUNKED, "false"); // Disable chunking
        options.setSoapVersionURI(SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI);
        options.setAction(action);

        return stub;
    }

    private static void processCollectionContent(ResourceAdminServiceStub stub, String flow, String[] childPaths) throws Exception {
        for (String path : childPaths) {
            String fileName = extractFileName(path);

            if (!isInSkipList(fileName)) {
                String fileContent = retrieveFileContent(stub, flow, fileName);
                if (fileContent != null) {
                    String policyName = extractPolicyNameFromContent(fileContent);

                    if (policyName != null) {
                        generateAndUploadPolicyFiles(flow, fileContent, policyName);
                    }
                }
            }
        }
    }

    private static String extractFileName(String filePath) {
        return filePath.substring(filePath.lastIndexOf("/") + 1);
    }

    private static String retrieveFileContent(ResourceAdminServiceStub stub, String flow, String fileName) throws Exception {
        stub=initializeServiceStub("urn:getTextContent");
        ServiceClient client = stub._getServiceClient();

        OMFactory factory = new OMLinkedListImplFactory();
        OMNamespace ns = factory.createOMNamespace("http://services.resource.registry.carbon.wso2.org", "ns");

        OMElement getTextContentElement = factory.createOMElement("getTextContent", ns);
        OMElement resourcePathElement = factory.createOMElement("path", ns);
        resourcePathElement.setText("/_system/governance/apimgt/customsequences/" + flow + "/" + fileName);
        getTextContentElement.addChild(resourcePathElement);

        OMElement response = client.sendReceive(getTextContentElement);
        return response != null ? response.getFirstElement().getText() : null;
    }

    private static String extractPolicyNameFromContent(String fileContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true); // Enable namespace awareness
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(fileContent.getBytes()));

            Element sequenceElement = (Element) document.getElementsByTagNameNS("http://ws.apache.org/ns/synapse", "sequence").item(0);
            return sequenceElement != null ? sequenceElement.getAttribute("name") : null;
        } catch (Exception e) {
            System.err.println("Error parsing file content: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static void generateAndUploadPolicyFiles(String flow, String fileContent, String policyName) {
        try {
            PolicyFilesGenerator filesGenerator = new PolicyFilesGenerator(PATH);
            filesGenerator.generatePolicyDefinitionFile(fileContent, policyName);
            filesGenerator.generatePolicySpecificationFile(policyName, flow);

            PolicyUploader uploader = new PolicyUploader(TOKEN, HOST42, PORT42, TRUSTSTORE_PATH, TRUSTSTORE_PASSWORD, PATH);
            uploader.uploadCommonPolicyHttpClient(policyName);
        } catch (Exception e) {
            System.err.println("Error generating or uploading policy files: " + e.getMessage());
            e.printStackTrace();
        }
    }


}
