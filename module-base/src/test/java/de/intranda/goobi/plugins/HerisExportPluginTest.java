/**
 * This file is part of the Goobi Application - a Workflow tool for the support of mass digitization.
 * 
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi-workflow
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package de.intranda.goobi.plugins;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.easymock.EasyMock;
import org.goobi.beans.GoobiProperty;
import org.goobi.beans.GoobiProperty.PropertyOwnerType;
import org.goobi.beans.Process;
import org.goobi.beans.Project;
import org.goobi.beans.ProjectFileGroup;
import org.goobi.beans.Ruleset;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.metadaten.MetadatenHelper;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ MetadatenHelper.class, VariableReplacer.class, ConfigurationHelper.class })
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*", "jdk.internal.reflect.*" })
public class HerisExportPluginTest {

    private static final String RULESET_NAME = "ruleset.xml";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private static String resourcesFolder;
    private File processDirectory;
    private File metadataDirectory;

    private Process testProcess = null;

    @BeforeClass
    public static void setUpClass() throws Exception {
        resourcesFolder = "src/test/resources/"; // for junit tests in eclipse

        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }

        String log4jFile = resourcesFolder + "log4j2.xml"; // for junit tests in eclipse

        System.setProperty("log4j.configurationFile", log4jFile);
    }

    @Before
    public void setUp() throws Exception {

        resourcesFolder = "src/test/resources/"; // for junit tests in eclipse

        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }

        metadataDirectory = folder.newFolder("metadata");

        processDirectory = new File(metadataDirectory + File.separator + "1");
        processDirectory.mkdirs();

        // copy meta.xml
        Path metaSource = Paths.get(resourcesFolder + "meta.xml");
        Path metaTarget = Paths.get(processDirectory.getAbsolutePath(), "meta.xml");
        Files.copy(metaSource, metaTarget);
        // copy images to media folder
        Path imagesSource = Paths.get(resourcesFolder, "sample_media");
        Path imagesTarget = Paths.get(processDirectory.getAbsolutePath(), "images", "sample_media");
        Files.createDirectories(imagesTarget);
        Files.copy(Paths.get(imagesSource.toString(), "Sammelmappe1.pdf_Seite_007.tif"),
                Paths.get(imagesTarget.toString(), "Sammelmappe1.pdf_Seite_007.tif"));
        Files.copy(Paths.get(imagesSource.toString(), "Sammelmappe1.pdf_Seite_008.tif"),
                Paths.get(imagesTarget.toString(), "Sammelmappe1.pdf_Seite_008.tif"));
        Files.copy(Paths.get(imagesSource.toString(), "Sammelmappe1.pdf_Seite_009.tif"),
                Paths.get(imagesTarget.toString(), "Sammelmappe1.pdf_Seite_009.tif"));
        Files.copy(Paths.get(imagesSource.toString(), "Sammelmappe1.pdf_Seite_010.tif"),
                Paths.get(imagesTarget.toString(), "Sammelmappe1.pdf_Seite_010.tif"));
        Files.copy(Paths.get(imagesSource.toString(), "Sammelmappe1.pdf_Seite_011.tif"),
                Paths.get(imagesTarget.toString(), "Sammelmappe1.pdf_Seite_011.tif"));

        PowerMock.mockStatic(ConfigurationHelper.class);
        ConfigurationHelper configurationHelper = EasyMock.createMock(ConfigurationHelper.class);
        EasyMock.expect(ConfigurationHelper.getInstance()).andReturn(configurationHelper).anyTimes();
        EasyMock.expect(configurationHelper.getMetsEditorLockingTime()).andReturn(1800000l).anyTimes();
        EasyMock.expect(configurationHelper.isAllowWhitespacesInFolder()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.useS3()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.isUseProxy()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.getGoobiContentServerTimeOut()).andReturn(60000).anyTimes();

        EasyMock.expect(configurationHelper.getConfigurationFolder()).andReturn(resourcesFolder + "config/").anyTimes();

        EasyMock.expect(configurationHelper.getMetadataFolder()).andReturn(metadataDirectory.toString() + "/").anyTimes();
        EasyMock.expect(configurationHelper.getRulesetFolder()).andReturn(resourcesFolder).anyTimes();
        EasyMock.expect(configurationHelper.getProcessImagesMainDirectoryName()).andReturn("sample_media").anyTimes();
        EasyMock.expect(configurationHelper.isUseMasterDirectory()).andReturn(true).anyTimes();
        EasyMock.replay(configurationHelper);
        PowerMock.replay(ConfigurationHelper.class);
        testProcess = createProcess();

    }

    @Test
    public void testConstructor() {
        HerisExportPlugin plugin = new HerisExportPlugin();
        assertNotNull(plugin);
    }

    @Test
    public void testStartExport() throws Exception {
        HerisExportPlugin plugin = new HerisExportPlugin();
        assertNotNull(plugin);
        plugin.setCleanupTempFiles(false);
        assertTrue(plugin.startExport(testProcess));

        // check that json file was created
        Path destination = plugin.getTempDir();
        Path jsonFile = Paths.get(destination.toString(), "21.json");
        assertTrue(Files.exists(jsonFile));

        // check that images are exported
        assertTrue(Files.exists(Paths.get(destination.toString(), "AT-BDA-FA-01-05-GZ-001.jpg")));
        assertTrue(Files.exists(Paths.get(destination.toString(), "AT-BDA-FA-01-05-GZ-002.jpg")));
        assertTrue(Files.exists(Paths.get(destination.toString(), "AT-BDA-FA-01-05-GZ-003.jpg")));
        assertTrue(Files.exists(Paths.get(destination.toString(), "AT-BDA-FA-01-05-GZ-004.jpg")));
        assertTrue(Files.exists(Paths.get(destination.toString(), "AT-BDA-FA-01-05-GZ-005.jpg")));
    }

    public static Process createProcess() throws Exception {

        Process testProcess = new Process();
        testProcess.setTitel("testprocess");
        testProcess.setId(1);

        GoobiProperty prop = new GoobiProperty(PropertyOwnerType.PROCESS);
        prop.setPropertyName("plugin_intranda_step_image_selection");
        prop.setPropertyValue(
                "{\"Sammelmappe1.pdf_Seite_008.tif\":1,\"Sammelmappe1.pdf_Seite_009.tif\":2,\"Sammelmappe1.pdf_Seite_011.tif\":3,\"Sammelmappe1.pdf_Seite_010.tif\":4,\"Sammelmappe1.pdf_Seite_007.tif\":5}");
        List<GoobiProperty> props = new ArrayList<>();
        props.add(prop);
        testProcess.setEigenschaften(props);

        // set temporary ruleset
        setUpRuleset(testProcess);

        setUpProject(testProcess);

        return testProcess;
    }

    private static void setUpProject(Process testProcess) throws IOException {
        Project project = new Project();
        project.setTitel("project");
        project.setId(1);
        testProcess.setProjekt(project);
        project.setFileFormatInternal("Mets");
        project.setFileFormatDmsExport("Mets");

        Path exportFolder = Files.createTempDirectory("hotfolder");
        Files.createDirectories(exportFolder);
        project.setDmsImportImagesPath(exportFolder.toString() + FileSystems.getDefault().getSeparator());
        project.setDmsImportErrorPath(exportFolder.toString() + FileSystems.getDefault().getSeparator());
        project.setDmsImportSuccessPath(exportFolder.toString() + FileSystems.getDefault().getSeparator());
        project.setDmsImportRootPath(exportFolder.toString() + FileSystems.getDefault().getSeparator());
        project.setUseDmsImport(true);
        project.setDmsImportCreateProcessFolder(true);

        ProjectFileGroup presentation = new ProjectFileGroup();
        presentation.setMimetype("image/jp2");
        presentation.setName("PRESENTATION");
        presentation.setPath("/opt/digiverso/viewer/media/1/");
        presentation.setSuffix("jp2");
        presentation.setFolder("");
        presentation.setId(1);
        presentation.setProject(project);

        ProjectFileGroup alto = new ProjectFileGroup();
        alto.setFolder("getOcrAltoDirectory");
        alto.setMimetype("text/xml");
        alto.setName("ALTO");
        alto.setPath("/opt/digiverso/viewer/alto/1/");
        alto.setSuffix("xml");
        alto.setProject(project);

        List<ProjectFileGroup> list = new ArrayList<>();
        list.add(presentation);
        list.add(alto);
        project.setFilegroups(list);

    }

    private static void setUpRuleset(Process testProcess) throws IOException, URISyntaxException {
        Ruleset ruleset = new Ruleset();
        ruleset.setId(11111);
        ruleset.setOrderMetadataByRuleset(true);
        ruleset.setTitel(RULESET_NAME);
        ruleset.setDatei(RULESET_NAME);
        testProcess.setRegelsatz(ruleset);
    }

}
