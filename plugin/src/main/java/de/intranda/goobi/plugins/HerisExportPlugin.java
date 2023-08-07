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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.beans.Processproperty;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IExportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.ExportFileException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.helper.exceptions.UghHelperException;
import io.goobi.workflow.api.connection.SftpUtils;
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

    private transient Path tempDir;

    // sftp connection
    boolean useSftp = false;
    private String username;
    private String password;
    private String keyfile;
    private String hostname;
    private String knownHosts;
    private String ftpFolder;
    private int port = 22;
    private transient SftpUtils utils = null;

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
        readConfiguration(process);

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
            Helper.setFehlerMeldung("The record doesn't contain a HERIS ID, abort");
            return false;
        }

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
            Helper.setFehlerMeldung("The record has no images selected, abort");
            // property not set, abort
            return false;
        }
        if (selectedImagesList.isEmpty()) {
            // no image selected, abort
            Helper.setFehlerMeldung("The record has no images selected, abort");
            return false;
        }

        tempDir = Files.createTempDirectory(herisId);

        //  open sftp connection,
        connect();
        // search for previous entry
        Path previousData = getExistingJsonFile(herisId);

        Map<String, String> imageIdentifierMap = new HashMap<>();
        if (previousData != null) {
            // parse file, generate filename + identifier list
            InputStream is = StorageProvider.getInstance().newInputStream(previousData);
            String jsonTxt = IOUtils.toString(is, Charset.defaultCharset());

            JSONObject json = new JSONObject(jsonTxt);

            JSONArray images = json.getJSONArray(jsonRootElementName);

            for (Object imageObject : images) {
                JSONObject image = (JSONObject) imageObject;
                String filename = image.getString("Dateiinformation");
                String identifier = image.getString("Id");
                imageIdentifierMap.put(filename, identifier);
            }

            // rename file
            Path backupFile =
                    Paths.get(previousData.getParent().toString(), previousData.getFileName().toString() + "-" + System.currentTimeMillis());
            Files.move(previousData, backupFile);
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

                        // if filename was used in previous export, re-use identifier. Otherwise identifier field is blank
                        if ("identifier".equals(jsonField.getType())) {
                            metadata.put(jsonField.getName(), imageIdentifierMap.get(image));
                        }

                    }
                    metadataList.add(metadata);
                    isFistImage = false;
                }

            }
        }

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
        disconnect();
        return true;
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
    private void readConfiguration(Process process) {
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

        useSftp = config.getBoolean("/sftp/@use", false);
        username = config.getString("/sftp/username");
        password = config.getString("/sftp/password");
        hostname = config.getString("/sftp/hostname");
        port = config.getInt("/sftp/port", 22);
        keyfile = config.getString("/sftp/keyfile");
        knownHosts = config.getString("/sftp/knownHosts", System.getProperty("user.home").concat("/.ssh/known_hosts"));
        ftpFolder = config.getString("/sftp/sftpFolder");

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

    private void connect() {
        if (useSftp) {
            try {
                // first option, use passphrase protected keyfile
                if (StringUtils.isNotBlank(keyfile) && StringUtils.isNotBlank(password)) {
                    utils = new SftpUtils(username, keyfile, password, hostname, port, knownHosts);
                }
                // second option: use keyfile without passphrase
                else if (StringUtils.isNotBlank(keyfile)) {
                    utils = new SftpUtils(username, keyfile, null, hostname, port, knownHosts);
                }
                // third option, username + password
                else {
                    utils = new SftpUtils(username, password, hostname, port, knownHosts);
                }
            } catch (IOException e) {
                log.error(e);
            }
        }
    }

    private Path getExistingJsonFile(String herisId) {
        Path jsonFile = null;
        if (useSftp) {
            try {
                // open configured folder
                utils.changeRemoteFolder(ftpFolder);

                // check for existing data in previous exports
                utils.changeRemoteFolder(herisId);
                List<String> dataInFolder = utils.listContent();

                // download existing data
                if (!dataInFolder.isEmpty()) {
                    for (String filename : dataInFolder) {
                        if ((herisId + ".json").equals(filename)) {
                            jsonFile = utils.downloadFile(filename, tempDir);
                        }
                    }
                }
            } catch (IOException e) {
                // exception is thrown if the given file does not exist
            }
        }
        return jsonFile;
    }

    private void uploadData() {

        // TODO
        // create new remote folder, if missing
        // list all files in remote folder
        // compare filenames with new files
        // if remote file is not present in local folder, delete it
        // upload new images + json + backup file

    }

    private void disconnect() {
        if (utils != null) {
            utils.close();
        }
    }

}