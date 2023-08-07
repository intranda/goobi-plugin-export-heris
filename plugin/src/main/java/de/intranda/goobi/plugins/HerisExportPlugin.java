package de.intranda.goobi.plugins;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.beans.Processproperty;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IExportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.json.JSONObject;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.ExportFileException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.helper.exceptions.UghHelperException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Reference;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;

@PluginImplementation
@Log4j2
@Getter
@Setter
public class HerisExportPlugin implements IExportPlugin, IPlugin {

    private static final long serialVersionUID = 8034087147444531960L;

    private String title = "intranda_export_heris";

    private PluginType type = PluginType.Export;

    private Step step;

    private List<String> problems;

    private boolean exportFulltext;
    private boolean exportImages;

    // name of the Processproperty that holds information of all selected images
    private String propertyName;

    private transient List<JsonField> jsonFields;
    private String jsonRootElementName;

    // set this to false in order to keep temp files (for junit tests)
    private boolean cleanupTempFiles = true;

    private Path tempDir;

    @Override
    public boolean startExport(Process process) throws IOException, InterruptedException, DocStructHasNoTypeException, PreferencesException,
            WriteException, MetadataTypeNotAllowedException, ExportFileException, UghHelperException, ReadException, SwapException, DAOException,
            TypeNotAllowedForParentException {

        return startExport(process, null);
    }

    @Override
    public boolean startExport(Process process, String destination) throws IOException, InterruptedException, DocStructHasNoTypeException,
            PreferencesException, WriteException, MetadataTypeNotAllowedException, ExportFileException, UghHelperException, ReadException,
            SwapException, DAOException, TypeNotAllowedForParentException {
        problems = new ArrayList<>();

        // read configuration file

        initializeFields(process);
        // open metadata file
        Fileformat fileformat = process.readMetadataFile();
        DocStruct logical = fileformat.getDigitalDocument().getLogicalDocStruct();
        List<DocStruct> pages = fileformat.getDigitalDocument().getPhysicalDocStruct().getAllChildren();

        // get heris id
        String herisId = null;
        for (Metadata md : logical.getAllMetadata()) {
            if ("HerisID".equals(md.getType().getName())) {
                herisId = md.getValue();
                break;
            }
        }
        if (StringUtils.isBlank(herisId)) {
            // TODO error message
            return false;
        }

        // TODO open sftp connection, check for previous exports

        // if previous export found, backup files, download older json file
        // filename + id list

        // find the images to export
        List<String> selectedImagesList = new ArrayList<>();
        Processproperty property = null;
        for (Processproperty p : process.getEigenschaften()) {
            if (propertyName.equals(p.getTitel())) {
                property = p;
                break;
            }
        }
        if (property != null) {
            String propertyValue = property.getWert();
            log.debug("propertyValue = " + propertyValue);
            // remove { and } from both ends
            String reducedValue = propertyValue.substring(1, propertyValue.length() - 1);
            String[] items = reducedValue.split(",");
            for (String item : items) {
                String[] itemParts = item.split(":");
                String imageName = itemParts[0];
                // remove " from both ends
                String reducedImageName = imageName.substring(1, imageName.length() - 1);
                selectedImagesList.add(reducedImageName);
            }
        } else {
            // property not set, abort
            return false;
        }
        if (selectedImagesList.isEmpty()) {
            // no image selected, abort
            return false;
        }

        //  first one is always the representative
        boolean isFistImage = true;
        // get photograph docstructs for those imagess
        List<Map<String, String>> metadataList = new ArrayList<>();
        for (String image : selectedImagesList) {
            Map<String, String> metadata = new HashMap<>();
            for (DocStruct page : pages) {
                String pageFileName = Paths.get(page.getImageName()).getFileName().toString();
                // comparison with filenames only
                if (pageFileName.equals(image)) {
                    DocStruct photograph = null;
                    List<Reference> refs = page.getAllFromReferences();
                    for (Reference ref : refs) {
                        if ("Photograph".equals(ref.getSource().getType().getName())) {
                            photograph = ref.getSource();
                        }
                    }

                    // collect metadata (default do Document docstruct, if metadata is missing in photograph)
                    for (JsonField jsonField : jsonFields) {
                        String fieldValue = getJsonFieldValue(jsonField, logical, photograph, isFistImage, image);
                        metadata.put(jsonField.getName(), fieldValue);
                        // TODO check if filename was used in previous export. If this is the case, re-use identifier.
                    }
                    metadataList.add(metadata);
                    isFistImage = false;
                }

            }
        }

        tempDir = Files.createTempDirectory(herisId);

        // export images to tmp folder
        exportSelectedImagesToTempFolder(process, selectedImagesList);
        // create json file in tmp folder
        writeJsonFile(metadataList, herisId);
        // upload data via sftp
        uploadData();

        // finally delete tmp folder
        if (cleanupTempFiles) {
            StorageProvider.getInstance().deleteDir(tempDir);
        }

        return true;
    }

    private void uploadData() {
        // TODO Auto-generated method stub

    }

    private void writeJsonFile(List<Map<String, String>> metadataList, String herisId) {
        JSONObject jsonObject = new JSONObject();

        List<JSONObject> list = new ArrayList<>();
        for (Map<String, String> map : metadataList) {
            JSONObject jo = new JSONObject(map);
            list.add(jo);
        }
        jsonObject.put("HERIS-ID", "herisId");
        jsonObject.put(jsonRootElementName, list);

        Path jsonFilePath = Paths.get(tempDir.toString(), herisId + ".json");
        try (OutputStream out = StorageProvider.getInstance().newOutputStream(jsonFilePath)) {
            out.write(jsonObject.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            log.error(e);
        }
    }

    private void exportSelectedImagesToTempFolder(Process process, List<String> imagesList) {
        try {
            String imageFolder = process.getImagesTifDirectory(false);
            for (String imagename : imagesList) {
                Path source = Paths.get(imageFolder, imagename);
                Path destination = Paths.get(tempDir.toString(), imagename);
                StorageProvider.getInstance().copyFile(source, destination);
            }
        } catch (IOException | SwapException e) {
            log.error(e);
        }
    }

    private String getJsonFieldValue(JsonField jsonField, DocStruct logical, DocStruct photograph, boolean representative, String filename) {

        switch (jsonField.getType()) {
            case "static":
                return jsonField.getValue();
            case "metadata":
                Metadata md = null;
                // first get metadat from photograph element
                if (photograph != null && photograph.getAllMetadata() != null) {
                    for (Metadata metadata : photograph.getAllMetadata()) {
                        if (metadata.getType().getName().equals(jsonField.getValue())) {
                            md = metadata;
                            break;
                        }
                    }
                }
                // if this didn't work, get it from main docstruct
                if (md == null) {
                    for (Metadata metadata : logical.getAllMetadata()) {
                        if (metadata.getType().getName().equals(jsonField.getValue())) {
                            md = metadata;
                            break;
                        }
                    }
                }
                return md == null ? "" : md.getValue();
            case "filename":
                return filename;
            case "representative":
                return String.valueOf(representative);
            case "identifier":
            default:
                return "";
        }
    }

    /**
     * initialize private fields
     * 
     * @param process Goobi process
     */
    private void initializeFields(Process process) {
        SubnodeConfiguration config = getConfig(process);

        propertyName = config.getString("./propertyName", "");

        jsonRootElementName = config.getString("/jsonRootElement");

        jsonFields = new ArrayList<>();

        List<HierarchicalConfiguration> fields = config.configurationsAt("/json_format/field");

        for (HierarchicalConfiguration field : fields) {

            String jsonName = field.getString("/@name");
            String jsonValue = field.getString(".");
            String jsonType = field.getString("/@type");

            JsonField jf = new JsonField();
            jf.setName(jsonName);
            jf.setType(jsonType);
            jf.setValue(jsonValue);
            jsonFields.add(jf);
        }

    }

    /**
     * get the SubnodeConfiguration of the current process
     * 
     * @param process Goobi process
     * @return SubnodeConfiguration object according to the project's name
     */
    private SubnodeConfiguration getConfig(Process process) {
        String projectName = process.getProjekt().getTitel();
        log.debug("projectName = " + projectName);
        XMLConfiguration xmlConfig = getXMLConfig();
        SubnodeConfiguration conf = null;

        // order of configuration is:
        // 1.) project name matches
        // 2.) project is *
        try {
            conf = xmlConfig.configurationAt("//config[./project = '" + projectName + "']");
        } catch (IllegalArgumentException e) {
            conf = xmlConfig.configurationAt("//config[./project = '*']");
        }

        return conf;
    }

    /**
     * get the XML configuration of this plugin
     * 
     * @return the XMLConfiguration of this plugin
     */
    private XMLConfiguration getXMLConfig() {
        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(title);
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());

        return xmlConfig;
    }
}