package de.intranda.goobi.plugins;

import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

public class SftpClientTest {

    String username = "username";

    String passphrase = "passphrase";

    String password = "password";

    String hostname = "127.0.0.1";

    int port = 22;

    String knownHostFiles = "~/.known_hosts";

    String keyFile = "~/.heris_key";

    @Test
    public void testKeyWithEmptyConfig() throws IOException {
        new SftpClient(username,keyFile, passphrase, hostname, port, knownHostFiles, new Properties());
    }

    @Test
    public void testKeyWithNullConfig() throws IOException {
        new SftpClient(username,keyFile, passphrase, hostname, port, knownHostFiles, null);
    }

    @Test
    public void testKeyWithProperties() throws IOException {
        Properties config = new Properties();
        config.put("PubkeyAcceptedAlgorithms", "ssh-rsa");
        new SftpClient(username,keyFile, passphrase, hostname, port, knownHostFiles, config);
    }

    @Test
    public void testUsernameWithEmptyConfig() throws IOException {
        new SftpClient(username, password, hostname, port, knownHostFiles, new Properties());
    }

    @Test
    public void testUsernameWithNullConfig() throws IOException {
        new SftpClient(username, password, hostname, port, knownHostFiles, null);
    }

    @Test
    public void testUsernameWithConfig() throws IOException {
        Properties config = new Properties();
        config.put("PubkeyAcceptedAlgorithms", "ssh-rsa");
        new SftpClient(username, password, hostname, port, knownHostFiles, config);
    }

}