package fr.pilato.elasticsearch.crawler.fs;

import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractor;
import fr.pilato.elasticsearch.crawler.fs.crawler.smb.FileAbstractorSMB;
import fr.pilato.elasticsearch.crawler.fs.crawler.ssh.FileAbstractorSSH;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import java.nio.file.Path;

public class FsParserSmb extends FsParserAbstract{

    public FsParserSmb(FsSettings fsSettings, Path config, FsCrawlerManagementService managementService,
                       FsCrawlerDocumentService documentService, Integer loop){
        super(fsSettings, config, managementService, documentService, loop);
    }

    @Override
    protected FileAbstractor<?> buildFileAbstractor() {
        return new FileAbstractorSMB(fsSettings);
    }
}
