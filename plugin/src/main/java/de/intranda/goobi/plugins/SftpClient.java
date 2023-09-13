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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

@Log4j2
public class SftpClient {

    private final JSch jsch;
    private final ChannelSftp sftpChannel;
    private final Session jschSession;


    /**
     * Authentication with username and password
     *
     * @return
     */

    public SftpClient(String username, String password, String hostname, int port, String knownHostsFile, Properties additionalConfig) throws IOException {
        jsch = initializeJSch();
        try {
            jsch.setKnownHosts(knownHostsFile);
            jschSession = jsch.getSession(username, hostname);
            jschSession.setPort(port);
            jschSession.setPassword(password);
            setCustomConfig(jschSession,additionalConfig);
            jschSession.connect();
            sftpChannel = (ChannelSftp) jschSession.openChannel("sftp");
            sftpChannel.connect();
        } catch (JSchException e) {
            throw new IOException(e);
        }
    }

    /**
     * 
     * Authentication with key
     * 
     */

    public SftpClient(String username, String key, String password, String hostname, int port, String knownHostsFile, Properties additionalConfig) throws IOException {
        jsch = initializeJSch();
        try {
            jsch.addIdentity(key, password);
            jsch.setKnownHosts(knownHostsFile);
            jschSession = jsch.getSession(username, hostname);
            jschSession.setPort(port);
            setCustomConfig(jschSession,additionalConfig);
            jschSession.connect();
            sftpChannel = (ChannelSftp) jschSession.openChannel("sftp");
            sftpChannel.connect();
        } catch (JSchException e) {
            throw new IOException(e);
        }
    }


    /**
     * @return a new instance of {@link JSch} with logging wired up
     */
    private JSch initializeJSch() {
        JSch instance = new JSch();
        instance.setInstanceLogger(new JSCHLogger(log));
        return instance;
    }

    private void setCustomConfig(Session jschSession, Properties additionalConfig) {
        log.debug("Setting custom config for Session:" + additionalConfig);
        if(additionalConfig != null) {
            jschSession.setConfig(additionalConfig);
        }
    }


    /**
     * Change remote folder
     * 
     * @param folder
     * @throws SftpException
     */

    public void changeRemoteFolder(String folder) throws IOException {
        try {
            log.debug("Change remote folder to "+folder);
            sftpChannel.cd(folder);
        } catch (SftpException e) {
            throw new IOException(e);
        }
    }

    /**
     * get remote folder name
     * 
     * @return
     * @throws SftpException
     */

    public String getRemoteFolder() throws IOException {
        try {
            return sftpChannel.pwd();
        } catch (SftpException e) {
            throw new IOException(e);
        }
    }

    /**
     * get content of remote folder
     * 
     * @return
     * @throws SftpException
     */

    public List<String> listContent() throws IOException {
        log.debug("list content of current working dir");
        List<String> content = new ArrayList<>();
        List<LsEntry> lsList;
        try {
            lsList = sftpChannel.ls(".");
        } catch (SftpException e) {
            throw new IOException(e);
        }
        log.debug("Found "+ lsList.size() + " items in current working dir." );
        for (LsEntry entry : lsList) {
            content.add(entry.getFilename());
        }
        Collections.sort(content);
        log.debug("Files in current working dir (sorted): "+content);
        return content;
    }

    /**
     * Download a remote file into a given folder
     * 
     * @param filename
     * @param downloadFolder
     * @return
     * @throws SftpException
     */

    public Path downloadFile(String filename, Path downloadFolder) throws IOException {
        Path destination = Paths.get(downloadFolder.toString(), filename);
        log.debug("Downloading remote file "+ filename + " to destination "+ destination);
        try {
            sftpChannel.get(filename, destination.toString());
        } catch (SftpException e) {
            log.error("Failed to download file "+ filename + " to "+destination, e);
            throw new IOException(e);
        }
        return destination;
    }

    /**
     * Upload a file into the current remote folder
     * 
     * @param file
     * @throws SftpException
     */

    public void uploadFile(Path file) throws IOException {
        log.debug("Uploading file "+ file + " to remote server.");
        try {
            sftpChannel.put(file.toString(), file.getFileName().toString());
        } catch (SftpException e) {
            log.error("Failed to upload file "+ file + " to remote server.", e);
            throw new IOException(e);
        }
    }

    public void close() {
        if (sftpChannel != null && sftpChannel.isConnected()) {
            sftpChannel.disconnect();
        }
        if (jschSession != null && jschSession.isConnected()) {
            jschSession.disconnect();
        }

    }

    public void createSubFolder(String foldername) throws IOException {
        log.debug("Creating folder "+ foldername);
        try {
            sftpChannel.mkdir(foldername);
        } catch (SftpException e) {
            log.error("Failed to create folder "+foldername, e);
            throw new IOException(e);
        }
    }

    public void deleteFile(String filename) throws IOException {
        log.debug("Deleting file "+ filename);
        try {
            sftpChannel.rm(filename);
        } catch (SftpException e) {
            throw new IOException(e);
        }
    }

    private static class JSCHLogger implements com.jcraft.jsch.Logger {
        private final Map<Integer, Level> levels = new HashMap<>();

        private final Logger LOGGER;

        public JSCHLogger(Logger log) {
            // Mapping between JSch levels and our own levels
            levels.put(DEBUG, Level.DEBUG);
            levels.put(INFO, Level.INFO);
            levels.put(WARN, Level.WARN);
            levels.put(ERROR, Level.ERROR);
            levels.put(FATAL, Level.FATAL);
            LOGGER = log;
        }

        @Override
        public boolean isEnabled(int pLevel) {
            return LOGGER.isDebugEnabled(); //ONLY ENABLE WHEN DEBUG LOGGING IS ACTIVE FOR ACTUAL LOGGER
        }

        @Override
        public void log(int pLevel, String pMessage) {
            Level level = levels.get(pLevel);
            if (level == null) {
                level = Level.INFO;
            }
            LOGGER.log(level, pMessage);
        }
    }
}
