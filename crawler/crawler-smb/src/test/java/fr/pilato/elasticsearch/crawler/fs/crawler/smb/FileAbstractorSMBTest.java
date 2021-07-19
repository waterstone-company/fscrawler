package fr.pilato.elasticsearch.crawler.fs.crawler.smb;

import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
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
        String[] paths = {"","folder","文件夹","folder/文件夹","文件夹/folder"};
        String host = "192.168.31.45";
        String user = "lzwcyd";
        String pass = "123456";
        String url = "//Desktop/win10_share_test";
        FsSettings fsSettings = FsSettings.builder("foo")
                .setServer(
                        Server.builder()
                                .setHostname(host)
                                .setUsername(user)
                                .setPassword(pass)
                                .build()
                ).setFs(Fs.builder()
                        .setUrl(url)
                        .build())
                .build();
        FileAbstractorSMB smb = new FileAbstractorSMB(fsSettings);
        smb.open();
        for (String path : paths) {
            boolean exists = smb.exists(path);
            assertThat(exists, is(true));
            Collection<FileAbstractModel> files = smb.getFiles(path);
            logger.debug("Found {} files", files.size());
            for (FileAbstractModel file : files) {
                logger.debug(" - {}", file);
            }
        }
        smb.close();
    }
}