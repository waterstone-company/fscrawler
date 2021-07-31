/*
 * Licensed to David Pilato under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package fr.pilato.elasticsearch.crawler.fs.framework;

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.Permissions;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomIntBetween;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.computeRealPathName;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.computeVirtualPathName;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.extractMajorVersion;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.extractMinorVersion;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getFileExtension;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getFileName;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getFilePermissions;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getGroupName;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getOwnerName;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getServerName;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getRelativePath;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.isFileSizeUnderLimit;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.localDateTimeToDate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assume.assumeFalse;

public class FsCrawlerUtilTest extends AbstractFSCrawlerTestCase {

    private static File file;

    @BeforeClass
    public static void createTmpFile() throws IOException {
        Path path = rootTmpDir.resolve("test-group.txt");
        Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwx------");
        FileAttribute<Set<PosixFilePermission>> fileAttributes = PosixFilePermissions.asFileAttribute(permissions);
        Files.createFile(path, fileAttributes);
        file = path.toFile();
    }

    @Test
    public void testOwnerName() {
        String ownerName = getOwnerName(file);
        assertThat(ownerName, not(isEmptyOrNullString()));
    }

    @Test
    public void testGroups() {
        assumeFalse("This test can not run on Windows.", OsValidator.WINDOWS);
        String groupName = getGroupName(file);
        assertThat(groupName, not(isEmptyOrNullString()));
    }

    @Test
    public void testPermissions() {
        assumeFalse("This test can not run on Windows.", OsValidator.WINDOWS);
        int permissions = getFilePermissions(file);
        assertThat(permissions, is(700));
    }

    @Test
    public void testFTPFilePermissions() throws IOException {
        String user = "user";
        String password = "password";
        FakeFtpServer fakeFtpServer = new FakeFtpServer();
        fakeFtpServer.setServerControlPort(5968);
        fakeFtpServer.addUserAccount(new UserAccount(user, password, "/data"));
        FileSystem fileSystem = new UnixFakeFileSystem();
        fileSystem.add(new DirectoryEntry("/data"));
        FileEntry fileAllPermissions = new FileEntry("/data/all.txt", "123");
        fileAllPermissions.setPermissions(Permissions.ALL);
        fileSystem.add(fileAllPermissions);
        FileEntry fileNonePermissions = new FileEntry("/data/none.txt", "456");
        fileNonePermissions.setPermissions(Permissions.NONE);
        fileSystem.add(fileNonePermissions);
        fakeFtpServer.setFileSystem(fileSystem);
        fakeFtpServer.start();

        FTPClient ftp = new FTPClient();
        ftp.connect("localhost", fakeFtpServer.getServerControlPort());
        int reply = ftp.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect();
            throw new IOException("Exception in connecting to FTP Server");
        }
        ftp.login(user, password);

        FTPFile[] files = ftp.listFiles("/data");
        List<String> filenames = Arrays.stream(files).map(FTPFile::getName).collect(Collectors.toList());
        assertThat(filenames.contains("all.txt"), is(true));
        assertThat(filenames.contains("none.txt"), is(true));
        for (FTPFile file : files) {
            if (file.getName().equals("all.txt")) {
                assertThat(getFilePermissions(file), is(777));
            } else if (file.getName().equals("none.txt")) {
                assertThat(getFilePermissions(file), is(0));
            }
        }

        ftp.disconnect();
        fakeFtpServer.stop();
    }

    @Test
    public void testGetFileName(){
        assertThat(getFileName("\\test\\test.txt"),is("test.txt"));
        assertThat(getFileName("\\test.txt"),is("test.txt"));
        assertThat(getFileName("test.txt"),is("test.txt"));
        assertThat(getFileName("\\test\\test\\test.txt"),is("test.txt"));
        assertThat(getFileName("\\test\\test"),is("test"));
        assertThat(getFileName("\\test"),is("test"));
        assertThat(getFileName("test"),is("test"));
    }

    @Test
    public void testIsFileSizeUnderLimit() {
        assertThat(isFileSizeUnderLimit(ByteSizeValue.parseBytesSizeValue("1mb"), 1), is(true));
        assertThat(isFileSizeUnderLimit(ByteSizeValue.parseBytesSizeValue("1mb"), 1048576), is(true));
        assertThat(isFileSizeUnderLimit(ByteSizeValue.parseBytesSizeValue("1mb"),
                new ByteSizeValue(randomIntBetween(2, 100), ByteSizeUnit.MB).getBytes()), is(false));
    }

    @Test
    public void testExtractMajorVersion() {
        assertThat(extractMajorVersion("7.2.0"), is("7"));
        assertThat(extractMajorVersion("10.1.0"), is("10"));
    }

    @Test
    public void testExtractMinorVersion() {
        assertThat(extractMinorVersion("7.2.0"), is("2"));
        assertThat(extractMinorVersion("10.1.0"), is("1"));
    }

    @Test
    public void testGetRealPathNameWindows() {
        testRealPath("/C:", "test-windows.txt", "/C:/test-windows.txt");
        testRealPath("/C:/", "test-windows.txt", "/C:/test-windows.txt");
        testRealPath("/C:/dir", "test-windows.txt", "/C:/dir/test-windows.txt");
        testRealPath("/C:/dir/", "test-windows.txt", "/C:/dir/test-windows.txt");

        testRealPath("C:/", "test-windows.txt", "C:/test-windows.txt");
        testRealPath("C:/dir", "test-windows.txt", "C:/dir/test-windows.txt");
        testRealPath("C:/dir/", "test-windows.txt", "C:/dir/test-windows.txt");

        testRealPath("C:", "test-windows.txt", "C:\\test-windows.txt");
        testRealPath("C:\\", "test-windows.txt", "C:\\test-windows.txt");
        testRealPath("C:\\dir", "test-windows.txt", "C:\\dir\\test-windows.txt");
        testRealPath("C:\\dir\\", "test-windows.txt", "C:\\dir\\test-windows.txt");

        testRealPath("\\\\SOMEONE", "test-smb.txt", "\\\\SOMEONE\\test-smb.txt");
        testRealPath("\\\\SOMEONE\\", "test-smb.txt", "\\\\SOMEONE\\test-smb.txt");
        testRealPath("\\\\SOMEONE\\share", "test-smb.txt", "\\\\SOMEONE\\share\\test-smb.txt");
        testRealPath("\\\\SOMEONE\\share\\", "test-smb.txt", "\\\\SOMEONE\\share\\test-smb.txt");
    }

    @Test
    public void testGetRealPathNameLinux() {
        // Local Linux / FTP
        testRealPath("/", "test-linux.txt", "/test-linux.txt");
        testRealPath("/dir", "test-linux.txt", "/dir/test-linux.txt");
        testRealPath("/dir/", "test-linux.txt", "/dir/test-linux.txt");

        // SMB
        testRealPath("//SOMEONE", "test-smb.txt", "//SOMEONE/test-smb.txt");
        testRealPath("//SOMEONE/", "test-smb.txt", "//SOMEONE/test-smb.txt");
        testRealPath("//SOMEONE/share", "test-smb.txt", "//SOMEONE/share/test-smb.txt");
        testRealPath("//SOMEONE/share/", "test-smb.txt", "//SOMEONE/share/test-smb.txt");
    }

    private void testRealPath(String dirname, String filename, String expectedPath) {
        assertThat(computeRealPathName(dirname, filename), is(expectedPath));
    }

    @Test
    public void testComputePathLinux() {
        // Local Linux / FTP
        testVirtualPath("/", "/", "/");
        testVirtualPath("/", "/dir", "/dir");
        testVirtualPath("/", "/dir/subdir", "/dir/subdir");
        testVirtualPath("/", "/file.txt", "/file.txt");
        testVirtualPath("/", "/dir/file.txt", "/dir/file.txt");
        testVirtualPath("/", "/dir/subdir/file.txt", "/dir/subdir/file.txt");

        testVirtualPath("/tmp", "/tmp", "/");
        testVirtualPath("/tmp", "/tmp/dir", "/dir");
        testVirtualPath("/tmp", "/tmp/dir/subdir", "/dir/subdir");
        testVirtualPath("/tmp", "/tmp/file.txt", "/file.txt");
        testVirtualPath("/tmp", "/tmp/dir/file.txt", "/dir/file.txt");
        testVirtualPath("/tmp", "/tmp/dir/subdir/file.txt", "/dir/subdir/file.txt");

        // SMB
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share", "/");
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share/dir", "/dir");
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share/dir/subdir", "/dir/subdir");
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share/file.txt", "/file.txt");
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share/dir/file.txt", "/dir/file.txt");
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share/dir/subdir/file.txt", "/dir/subdir/file.txt");
    }

    @Test
    public void testComputePathWindows() {
        testVirtualPath("C:", "C:", "\\");
        testVirtualPath("C:", "C:\\dir", "\\dir");
        testVirtualPath("C:", "C:\\dir\\subdir", "\\dir\\subdir");
        testVirtualPath("C:", "C:\\file.txt", "\\file.txt");
        testVirtualPath("C:", "C:\\dir\\file.txt", "\\dir\\file.txt");
        testVirtualPath("C:", "C:\\dir\\subdir\\file.txt", "\\dir\\subdir\\file.txt");

        testVirtualPath("C:\\tmp", "C:\\tmp", "\\");
        testVirtualPath("C:\\tmp", "C:\\tmp\\dir", "\\dir");
        testVirtualPath("C:\\tmp", "C:\\tmp\\dir\\subdir", "\\dir\\subdir");
        testVirtualPath("C:\\tmp", "C:\\tmp\\file.txt", "\\file.txt");
        testVirtualPath("C:\\tmp", "C:\\tmp\\dir\\file.txt", "\\dir\\file.txt");
        testVirtualPath("C:\\tmp", "C:\\tmp\\dir\\subdir\\file.txt", "\\dir\\subdir\\file.txt");

        testVirtualPath("C:/tmp", "C:/tmp", "/");
        testVirtualPath("C:/tmp", "C:/tmp/dir", "/dir");
        testVirtualPath("C:/tmp", "C:/tmp/dir/subdir", "/dir/subdir");
        testVirtualPath("C:/tmp", "C:/tmp/file.txt", "/file.txt");
        testVirtualPath("C:/tmp", "C:/tmp/dir/file.txt", "/dir/file.txt");
        testVirtualPath("C:/tmp", "C:/tmp/dir/subdir/file.txt", "/dir/subdir/file.txt");

        testVirtualPath("/C:/tmp", "/C:/tmp", "/");
        testVirtualPath("/C:/tmp", "/C:/tmp/dir", "/dir");
        testVirtualPath("/C:/tmp", "/C:/tmp/dir/subdir", "/dir/subdir");
        testVirtualPath("/C:/tmp", "/C:/tmp/file.txt", "/file.txt");
        testVirtualPath("/C:/tmp", "/C:/tmp/dir/file.txt", "/dir/file.txt");
        testVirtualPath("/C:/tmp", "/C:/tmp/dir/subdir/file.txt", "/dir/subdir/file.txt");

        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share", "\\");
        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share\\dir", "\\dir");
        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share\\dir\\subdir", "\\dir\\subdir");
        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share\\file.txt", "\\file.txt");
        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share\\dir\\file.txt", "\\dir\\file.txt");
        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share\\dir\\subdir\\file.txt", "\\dir\\subdir\\file.txt");
    }

    private void testVirtualPath(String rootPath, String realPath, String expectedPath) {
        assertThat(computeVirtualPathName(rootPath, realPath), is(expectedPath));
    }

    @Test
    public void testGetFileExtension() {
        assertThat(getFileExtension(new File("foo.bar")), is("bar"));
        assertThat(getFileExtension(new File("foo")), is(""));
        assertThat(getFileExtension(new File("foo.bar.baz")), is("baz"));
    }

    @Test
    public void testLocalDateToDate() {
        LocalDateTime now = LocalDateTime.now();
        logger.info("Current Time [{}] in [{}] is actually [{}]", now, TimeZone.getDefault().getDisplayName(), localDateTimeToDate(now));
    }

    @Test
    public void testGetServerName(){
        assertThat(getServerName("//desttop-123/test"), is("test"));
    }

    @Test
    public void testGetRelativePath(){
        assertThat(getRelativePath("//desktopName/shareName"), is(""));
        assertThat(getRelativePath("//desktopName/shareName/test"), is("/test"));
        assertThat(getRelativePath("//desktopName/shareName/test.txt"), is("/test.txt"));
        assertThat(getRelativePath("//desktopName/shareName/folder/test.txt"), is("/folder/test.txt"));
        assertThat(getRelativePath("/shareName"), is(""));
        assertThat(getRelativePath("/shareName/test"), is("/test"));
    }

}
