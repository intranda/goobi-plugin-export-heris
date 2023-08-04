package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.goobi.beans.Process;
import org.goobi.beans.Processproperty;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IExportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.ExportFileException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.helper.exceptions.UghHelperException;
import de.sub.goobi.persistence.managers.PropertyManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
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

        // find the images to export
        List<String> selectedImagesList = new ArrayList<>();
        Processproperty property = getProcessproperty(process);
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
        // get photograph docstructs for those imagess
        for (String image : selectedImagesList) {
            // TODO first one is always the representative ?

            for (DocStruct page : pages) {
                String pageFileName = Paths.get(page.getImageName()).getFileName().toString();
                DocStruct photograph = null;
                // comparison with filenames only
                if (pageFileName.equals(image)) {
                    List<Reference> refs = page.getAllFromReferences();
                    for (Reference ref : refs) {
                        if ("Photograph".equals(ref.getSource().getType().getName())) {
                            photograph = ref.getSource();
                        }
                    }
                }

                // collect metadata (default do Document docstruct, if metadata is missing in photograph)
                if (photograph != null) {

                }

            }

        }
        // create json file

        // open sftp connection, check for previous exports

        // if previous export found, backup files

        // check if the same images are used -> re-use identifier

        // export images + json file

        return false;
    }

    /**
     * initialize private fields
     * 
     * @param process Goobi process
     */
    private void initializeFields(Process process) {
        SubnodeConfiguration config = getConfig(process);

        propertyName = config.getString("./propertyName", "");

        String jsonRootElementName = config.getString("/jsonRootElement");
        String herisId = config.getString("/herisId");

        List<HierarchicalConfiguration> fields = config.configurationsAt("/json_format/field");

        for (HierarchicalConfiguration field : fields) {

            String jsonName = field.getString("/@name");
            String value = field.getString(".");
            String type = field.getString("/@type");
            System.out.println("######");
            System.out.println(jsonName);
            System.out.println(value);
            System.out.println(type);

        }

    }

    /**
     * get the proper Processproperty object holding information of all selected images
     * 
     * @param process Goobi process
     * @return the Processproperty object holding information of all selected images
     */
    private Processproperty getProcessproperty(Process process) {
        List<Processproperty> props = PropertyManager.getProcessPropertiesForProcess(process.getId());
        for (Processproperty p : props) {
            if (propertyName.equals(p.getTitel())) {
                return p;
            }
        }
        // nothing found, report it
        String message = "Can not find a proper process property. Please recheck your configuration.";
        log.info(message);
        return null;
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