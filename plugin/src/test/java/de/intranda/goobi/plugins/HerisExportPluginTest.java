package de.intranda.goobi.plugins;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
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
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.metadaten.MetadatenHelper;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigPlugins.class, ConfigurationHelper.class, MetadatenHelper.class, })
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*", "jdk.internal.reflect.*" })
public class HerisExportPluginTest {

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

        //        PowerMock.mockStatic(ConfigurationHelper.class);
        //        ConfigurationHelper configurationHelper = EasyMock.createMock(ConfigurationHelper.class);
        //        EasyMock.expect(ConfigurationHelper.getInstance()).andReturn(configurationHelper).anyTimes();
        //        EasyMock.expect(configurationHelper.getMetsEditorLockingTime()).andReturn(1800000l).anyTimes();
        //        EasyMock.expect(configurationHelper.isAllowWhitespacesInFolder()).andReturn(false).anyTimes();
        //        EasyMock.expect(configurationHelper.useS3()).andReturn(false).anyTimes();
        //        EasyMock.expect(configurationHelper.isUseProxy()).andReturn(false).anyTimes();
        //        EasyMock.expect(configurationHelper.getGoobiContentServerTimeOut()).andReturn(60000).anyTimes();
        //        EasyMock.expect(configurationHelper.getMetadataFolder()).andReturn(metadataDirectoryName).anyTimes();
        //        EasyMock.expect(configurationHelper.getRulesetFolder()).andReturn(resourcesFolder).anyTimes();
        //        EasyMock.expect(configurationHelper.getProcessImagesMainDirectoryName()).andReturn("00469418X_media").anyTimes();
        //        EasyMock.expect(configurationHelper.isUseMasterDirectory()).andReturn(true).anyTimes();
        //
        //        EasyMock.expect(configurationHelper.getNumberOfMetaBackups()).andReturn(0).anyTimes();
        //        EasyMock.replay(configurationHelper);
        //
        //        MetadatenSperrung locking = new MetadatenSperrung();
        //        locking.setLocked(1, "1");
        //
        //        testProcess = createProcess();
        //
        //        PowerMock.mockStatic(MetadatenHelper.class);
        //        EasyMock.expect(MetadatenHelper.getMetaFileType(EasyMock.anyString())).andReturn("metsmods").anyTimes();
        //        EasyMock.expect(MetadatenHelper.getFileformatByName(EasyMock.anyString(), EasyMock.anyObject(Ruleset.class)))
        //        .andReturn(new MetsMods(testProcess.getRegelsatz().getPreferences()))
        //        .anyTimes();
        //        EasyMock.expect(MetadatenHelper.getExportFileformatByName(EasyMock.anyString(), EasyMock.anyObject(Ruleset.class)))
        //        .andReturn(new MetsModsImportExport(testProcess.getRegelsatz().getPreferences()))
        //        .anyTimes();
        //
        //        PowerMock.mockStatic(Helper.class);
        //
        //        EasyMock.expect(Helper.getCurrentUser()).andReturn(null).anyTimes();
        //        EasyMock.expect(Helper.getTranslation(EasyMock.anyString(), EasyMock.anyString(), EasyMock.anyString())).andReturn("").anyTimes();
        //        Helper.setFehlerMeldung(EasyMock.anyString());
        //        Helper.setMeldung(EasyMock.anyString(), EasyMock.anyString(), EasyMock.anyString());
        //        PowerMock.replayAll();
    }

    @Test
    public void testConstructor() {
        HerisExportPlugin plugin = new HerisExportPlugin();
        assertNotNull(plugin);
    }

    private XMLConfiguration getConfig() {
        String file = "plugin_intranda_export_heris.xml";
        XMLConfiguration config = new XMLConfiguration();
        config.setDelimiterParsingDisabled(true);
        try {
            config.load(resourcesFolder + file);
        } catch (ConfigurationException e) {
        }
        config.setReloadingStrategy(new FileChangedReloadingStrategy());
        return config;
    }

    private static final String RULESET_NAME = "ruleset.xml";

    public static Process createProcess() throws Exception {

        Process testProcess = new Process();
        testProcess.setTitel("testprocess");
        testProcess.setId(1);

        // set temporary ruleset
        setUpRuleset(testProcess);

        setUpProject(testProcess);

        setUpConfig();

        return testProcess;
    }

    private static void setUpConfig() {
        ConfigurationHelper.getInstance().setParameter("DIRECTORY_SUFFIX", "media");
        ConfigurationHelper.getInstance().setParameter("DIRECTORY_PREFIX", "master");
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
