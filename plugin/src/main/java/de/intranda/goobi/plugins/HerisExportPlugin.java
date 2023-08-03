package de.intranda.goobi.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.goobi.beans.Process;
import org.goobi.beans.Processproperty;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IExportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;

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

        // open metadata file

        Fileformat fileformat = process.readMetadataFile();

        DocStruct logical = fileformat.getDigitalDocument().getLogicalDocStruct();
        List<DocStruct> pages = fileformat.getDigitalDocument().getPhysicalDocStruct().getAllChildren();

        // find the images to export
        Map<Integer, String> selectedImagesNamesOrderMap = new HashMap<>();
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
                int imageOrder = Integer.parseInt(itemParts[1]);
                selectedImagesNamesOrderMap.put(imageOrder, imageName);
            }
        } else {
            // property not set, abort
            return false;
        }
        if (selectedImagesNamesOrderMap.isEmpty()) {
            // no image selected, abort
            return false;
        }
        // get photograph docstructs for those imagess
        for (int i = 0; i < selectedImagesNamesOrderMap.size(); i++) {
            String image = selectedImagesNamesOrderMap.get(i);

            // collect metadata (default do Document docstruct, if metadata is missing)

            for (DocStruct page : pages) {
                // TODO comparison to filename only
                if (page.getImageName().equals(image)) {
                    List<Reference> refs = page.getAllToReferences();
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

}