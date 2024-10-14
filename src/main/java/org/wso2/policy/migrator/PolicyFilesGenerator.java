package org.wso2.policy.migrator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class PolicyFilesGenerator {
    private static String path = null;

    public PolicyFilesGenerator(String path) {
        this.path = path;
    }

    public void generatePolicyDefinitionFile(String fileContent, String policyName) {
        String extractedFileContent = fileContent.replaceAll("<sequence[^>]*>", "")
                .replaceAll("</sequence>", "");
        System.out.println("File Content: " + extractedFileContent);
        String j2Content = extractedFileContent;
        File defFile = new File(Paths.get(path).toFile(), policyName + ".j2");
        // Write the extracted content to the temporary file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(defFile))) {
            writer.write(j2Content);
        } catch (IOException e) {
            System.out.println("Error while creating the policy definition file");
            throw new RuntimeException(e);
        }
    }

    public void generatePolicySpecificationFile(String policyName, String flow) {
        String templatePath = "spec_template.yaml";
        String outputPath = path + policyName + "_spec.yaml";
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
                    .replace("${DisplayName}", policyName+"_common_policy")
                    .replace("${applicableFlow}", applicableFlow);
            Files.write(Paths.get(outputPath), content.getBytes(), StandardOpenOption.CREATE);
        } catch (IOException e) {
            System.out.println("Error while creating policy specification file");
            throw new RuntimeException(e);
        }


    }
}
