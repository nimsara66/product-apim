package org.wso2.am.testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class DistributedApimTomlBuilderTest {

    @Test
    public void shouldApplyRuntimeValuesAfterAllOverlays() throws Exception {
        String result = DistributedApimTomlBuilder.build(
                "[server]\nhostname=\"localhost\"\n[database.shared_db]\nurl=\"h2\"\n",
                "[server]\nhostname=\"apim-cp\"\n[database.shared_db]\ntype=\"mysql\"\n",
                "[server]\nhostname=\"overlay-host\"\n",
                Map.of("server.hostname", "apim-cp-final",
                        "database.shared_db.url", "jdbc:mysql://mysql:3306/WSO2AM_SHARED_DB?useSSL=false"));

        Assert.assertTrue(result.contains("apim-cp-final"));
        Assert.assertTrue(result.contains("jdbc:mysql://mysql:3306/WSO2AM_SHARED_DB?useSSL=false"));
        Assert.assertFalse(result.contains("overlay-host"));
    }

    @Test
    public void shouldRejectAmbiguousDistributedOverlayParameters() {
        Assert.assertThrows(IllegalArgumentException.class,
                () -> DistributedApimTomlBuilder.resolveExtraOverlay(
                        Map.of("tomlExtraOverlayPath", "legacy.toml"),
                        DistributedApimTomlBuilder.Component.CP));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> DistributedApimTomlBuilder.resolveServerFiles(
                        Map.of("serverFilesToCopy", "a::b"),
                        DistributedApimTomlBuilder.Component.GATEWAY));
    }

    @Test
    public void shouldResolveOnlyFilesQualifiedForTheSelectedComponent() throws Exception {
        Path source = Files.createTempFile("distributed-apim", ".xml");
        Files.writeString(source, "fixture");
        String value = source + "::repository/conf/example.xml";

        List<DistributedApimTomlBuilder.ServerFile> files =
                DistributedApimTomlBuilder.resolveServerFiles(
                        Map.of("serverFilesToCopy.cp", value),
                        DistributedApimTomlBuilder.Component.CP);

        Assert.assertEquals(files.size(), 1);
        Assert.assertEquals(files.get(0).serverRelativePath(), "repository/conf/example.xml");
        Assert.assertTrue(DistributedApimTomlBuilder.resolveServerFiles(
                Map.of("serverFilesToCopy.cp", value),
                DistributedApimTomlBuilder.Component.TM).isEmpty());
    }

    @Test
    public void distributedBaseOverlaysMustUseNetworkAliases() throws Exception {
        for (String resource : new String[]{"cp-base-overlay.toml", "tm-base-overlay.toml",
                "gateway-base-overlay.toml"}) {
            String content = new String(getClass().getClassLoader()
                    .getResourceAsStream("distributed-apim/" + resource).readAllBytes());
            Assert.assertTrue(content.contains("&amp;allowPublicKeyRetrieval=true&amp;useSSL=false"),
                    resource + " must preserve XML-escaped JDBC separators for master-datasources.xml");
            Assert.assertFalse(content.contains("https://localhost:9443/services/"),
                    resource + " uses localhost for an internal APIM service endpoint");
            Assert.assertTrue(content.contains("jdbc:mysql://mysql:3306/WSO2AM_SHARED_DB?autoReconnect=true"
                            + "&amp;allowPublicKeyRetrieval=true&amp;useSSL=false"),
                    resource + " must configure the shared MySQL datasource");
            if (resource.startsWith("gateway-")) {
                Assert.assertFalse(content.contains("[database.apim_db]"),
                        resource + " must preserve the product default APIM H2 datasource");
            } else {
                Assert.assertTrue(content.contains("jdbc:mysql://mysql:3306/WSO2AM_DB?autoReconnect=true"
                                + "&amp;allowPublicKeyRetrieval=true&amp;useSSL=false"),
                        resource + " must configure the APIM MySQL datasource");
            }
        }

        String cpOverlay = new String(getClass().getClassLoader().getResourceAsStream(
                "distributed-apim/cp-base-overlay.toml").readAllBytes());
        String generated = DistributedApimTomlBuilder.build(
                "[database.apim_db]\nurl=\"h2\"\n",
                cpOverlay, null, Map.of());
        JsonNode urlNode = new TomlMapper().readTree(generated).at("/database/apim_db/url");
        Assert.assertEquals(urlNode.asText(),
                "jdbc:mysql://mysql:3306/WSO2AM_DB?autoReconnect=true&amp;allowPublicKeyRetrieval=true"
                        + "&amp;useSSL=false",
                "Generated TOML must preserve the product-required XML-escaped JDBC URL");
    }
}
