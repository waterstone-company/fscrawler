package fr.pilato.elasticsearch.crawler.fs.crawler.smb;

import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.util.Collection;
import junit.framework.TestCase;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import org.junit.Ignore;
import org.junit.Test;

public class FileAbstractorSMBTest extends AbstractFSCrawlerTestCase {

    @Test
    @Ignore
    public void testConnectToWindows() throws Exception {
        String path = "test";
        String host = "10.211.55.7";
        String user = "lzwcyd";
        String pass = "123456";
        String serverName = "model";
        FsSettings fsSettings = FsSettings.builder("foo")
                .setServer(
                        Server.builder()
                                .setHostname(host)
                                .setUsername(user)
                                .setPassword(pass)
                                .setServerName(serverName)
                                .build()
                )
                .build();
        FileAbstractorSMB smb = new FileAbstractorSMB(fsSettings);
        smb.open();
        boolean exists = smb.exists(path);
        assertThat(exists, is(true));
        Collection<FileAbstractModel> files = smb.getFiles(path);
        logger.debug("Found {} files", files.size());
        for (FileAbstractModel file : files) {
            logger.debug(" - {}", file);
        }
        smb.close();
    }
}