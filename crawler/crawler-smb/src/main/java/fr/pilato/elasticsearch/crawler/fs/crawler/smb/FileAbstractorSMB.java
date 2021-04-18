package fr.pilato.elasticsearch.crawler.fs.crawler.smb;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msdtyp.SecurityInformation;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.security.bc.BCSecurityProvider;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.Directory;
import com.hierynomus.smbj.share.DiskEntry;
import com.hierynomus.smbj.share.DiskShare;
import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractor;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileAbstractorSMB extends FileAbstractor<DiskEntry> {


    private final Logger logger = LogManager.getLogger(FileAbstractorSMB.class);

    public FileAbstractorSMB(FsSettings fsSettings) {
        super(fsSettings);
    }

    private DiskShare share;

    private SMBClient client;

    @Override
    public FileAbstractModel toFileAbstractModel(String path, DiskEntry file) {

        int permissions = 777;

        //TODO 修正权限 file.getFileInformation().getAccessInformation().getAccessFlags()

        EnumSet<AccessMask> list = EnumWithValue.EnumUtils.toEnumSet(file.getFileInformation().getAccessInformation().getAccessFlags(), AccessMask.class);


        //此处这样取文件/文件夹名的原因为：file.getFileInformation().getNameInformation() 取到的值永远为null
        String fileName = file.getUncPath().substring(file.getUncPath().lastIndexOf("\\") + 1);

        return new FileAbstractModel(
                fileName,
                !file.getFileInformation().getStandardInformation().isDirectory(),
                // We are using here the local TimeZone as a reference. If the remote system is under another TZ, this might cause issues
                LocalDateTime.ofInstant(Instant.ofEpochMilli(file.getFileInformation().getBasicInformation().getLastWriteTime().toEpochMillis()), ZoneId.systemDefault()),
                // We don't have the creation date
                null,
                // We are using here the local TimeZone as a reference. If the remote system is under another TZ, this might cause issues
                LocalDateTime.ofInstant(Instant.ofEpochMilli(file.getFileInformation().getBasicInformation().getCreationTime().toEpochMillis()), ZoneId.systemDefault()),
                FilenameUtils.getExtension(file.getFileInformation().getNameInformation()),
                path,
                path.concat("/").concat(fileName),
                file.getFileInformation().getStandardInformation().getAllocationSize(),
                file.getSecurityInformation(Collections.singleton(SecurityInformation.OWNER_SECURITY_INFORMATION)).getOwnerSid().toString(),
                file.getSecurityInformation(Collections.singleton(SecurityInformation.GROUP_SECURITY_INFORMATION)).getGroupSid().toString(),
                permissions);
    }

    @Override
    public InputStream getInputStream(FileAbstractModel file) throws Exception {
        if (file.isFile()) {
            String fullPath = file.getFullpath();
            fullPath = correctionPath(fullPath);
            return share.openFile(fullPath, EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null).getInputStream();
        } else {
            return new ByteArrayInputStream(file.getName().getBytes());
        }
    }

    @Override
    public Collection<FileAbstractModel> getFiles(String dir) throws Exception {

        //修正路径
        dir = correctionPath(dir);


        logger.debug("Listing smb files from {}", dir);
        List<FileIdBothDirectoryInformation> ls;

        Directory directory = share.openDirectory(dir, EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null);

        ls = directory.list();
        if (ls == null) {
            return null;
        }

        Collection<FileAbstractModel> result = new ArrayList<>(ls.size());
        // Iterate other files
        // We ignore here all files like . and ..
        String finalDir = dir;
        result.addAll(ls.stream().filter(file -> !".".equals(file.getFileName()) &&
                !"..".equals(file.getFileName()))
                .map(file -> toFileAbstractModel(finalDir, share.open(finalDir + "/" + file.getFileName(), EnumSet.of(AccessMask.GENERIC_READ),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null)))
                .collect(Collectors.toList()));

        logger.debug("{} local files found", result.size());
        return result;
    }

    @Override
    public boolean exists(String dir) {
        dir = correctionPath(dir);
        return share.fileExists(dir) || share.folderExists(dir);
    }

    @Override
    public void open() throws Exception {
        share = openSMBConnection(fsSettings.getServer());
    }

    @Override
    public void close() throws Exception {
        share.close();
        client.close();
    }


    private String correctionPath(String dir) {
        if (dir.startsWith("//")) {
            String[] path = dir.split("/");
            dir = dir.substring(3 + path[2].length() + path[3].length());
        }
        return dir;
    }


    private DiskShare openSMBConnection(Server server) throws IOException {
        logger.debug("Opening SMB connection to {}@{}", server.getUsername(), server.getHostname());

        SmbConfig smbConfig = SmbConfig.builder()
                //SMB3.0 use BCSecurityProvider
                .withSecurityProvider(new BCSecurityProvider())
                .withTimeout(12, TimeUnit.SECONDS) // Timeout sets Read, Write, and Transact timeouts (default is 60 seconds)
                .withSoTimeout(18, TimeUnit.SECONDS) // Socket Timeout (default is 0 seconds, blocks forever)
                .build();

        client = new SMBClient(smbConfig);
        AuthenticationContext ac = new AuthenticationContext(server.getUsername(), server.getPassword().toCharArray(), server.getHostname());
        Connection connection = client.connect(server.getHostname());
        Session session = connection.authenticate(ac);
        String url = fsSettings.getFs().getUrl();
        //   //6E64/model      //6E64/model/test
        String serverName = url.split("/")[3];
        return (DiskShare) session.connectShare(serverName);

    }
}
