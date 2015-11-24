package org.jdownloader.gui.views.downloads.properties;

import java.util.List;

import jd.controlling.packagecontroller.AbstractNode;
import jd.controlling.packagecontroller.AbstractPackageNode;

import org.jdownloader.controlling.Priority;
import org.jdownloader.extensions.extraction.Archive;
import org.jdownloader.extensions.extraction.BooleanStatus;

public abstract class AbstractNodeProperties {
    abstract protected List<Archive> loadArchives();

    abstract protected boolean hasLoadedArchives();

    abstract protected String loadComment();

    abstract protected String loadDownloadFrom();

    abstract protected String loadDownloadPassword();

    abstract protected String loadFilename();

    abstract protected String loadMD5();

    abstract protected String loadPackageName();

    abstract protected Priority loadPriority();

    abstract protected String loadSaveTo();

    abstract protected String loadSha1();

    abstract protected void saveMd5(String cs);

    abstract protected void savePackageName(String text);

    abstract protected void savePriority(Priority priop);

    abstract protected void saveSaveTo(String path);

    abstract protected void saveSha1(String cs);

    abstract protected void saveArchivePasswords(List<String> hashSet);

    abstract protected void saveAutoExtract(BooleanStatus selectedItem);

    abstract protected void saveComment(String text);

    abstract protected void saveDownloadPassword(String text);

    abstract protected void saveFilename(String text);

    abstract protected boolean samePackage(AbstractPackageNode pkg);

    abstract protected boolean isDifferent(AbstractNode node);

}
