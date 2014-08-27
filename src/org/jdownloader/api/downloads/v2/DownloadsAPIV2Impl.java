package org.jdownloader.api.downloads.v2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.swing.Icon;

import jd.controlling.downloadcontroller.DownloadController;
import jd.controlling.downloadcontroller.DownloadSession.STOPMARK;
import jd.controlling.downloadcontroller.DownloadWatchDog;
import jd.controlling.packagecontroller.AbstractNode;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.FilePackageView;
import jd.plugins.PluginProgress;
import jd.plugins.PluginStateCollection;

import org.appwork.remoteapi.exceptions.BadParameterException;
import org.jdownloader.DomainInfo;
import org.jdownloader.api.RemoteAPIController;
import org.jdownloader.extensions.extraction.ExtractionStatus;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.SelectionInfo;
import org.jdownloader.myjdownloader.client.bindings.PriorityStorable;
import org.jdownloader.myjdownloader.client.bindings.interfaces.DownloadsListInterface;
import org.jdownloader.plugins.ConditionalSkipReason;
import org.jdownloader.plugins.FinalLinkState;
import org.jdownloader.plugins.SkipReason;

public class DownloadsAPIV2Impl implements DownloadsAPIV2 {

    public DownloadsAPIV2Impl() {
        RemoteAPIController.validateInterfaces(DownloadsAPIV2.class, DownloadsListInterface.class);

    }

    @Override
    public List<FilePackageAPIStorableV2> queryPackages(PackageQueryStorable queryParams) throws BadParameterException {

        DownloadController dlc = DownloadController.getInstance();
        DownloadWatchDog dwd = DownloadWatchDog.getInstance();

        // filter out packages, if specific packageUUIDs given, else return all packages
        List<FilePackage> packages = null;

        if (queryParams.getPackageUUIDs() != null && queryParams.getPackageUUIDs().length > 0) {

            packages = convertIdsToPackages(queryParams.getPackageUUIDs());

        } else {
            packages = dlc.getPackagesCopy();
        }

        List<FilePackageAPIStorableV2> ret = new ArrayList<FilePackageAPIStorableV2>(packages.size());
        int startWith = queryParams.getStartAt();
        int maxResults = queryParams.getMaxResults();
        if (startWith > dlc.size() - 1) {
            return ret;
        }
        if (startWith < 0) {
            startWith = 0;
        }
        if (maxResults < 0) {
            maxResults = dlc.size();
        }

        for (int i = startWith; i < Math.min(startWith + maxResults, dlc.size()); i++) {
            FilePackage fp = packages.get(i);
            boolean readL = fp.getModifyLock().readLock();
            try {
                FilePackageView fpView = new FilePackageView(fp);
                fpView.setItems(null);
                FilePackageAPIStorableV2 fps = new FilePackageAPIStorableV2(fp);

                if (queryParams.isSaveTo()) {
                    fps.setSaveTo(fp.getView().getDownloadDirectory());

                }
                if (queryParams.isBytesTotal()) {

                    fps.setBytesTotal(fpView.getSize());

                }
                if (queryParams.isChildCount()) {
                    fps.setChildCount(fp.size());
                }
                if (queryParams.isHosts()) {
                    DomainInfo[] di = fpView.getDomainInfos();
                    String[] hosts = new String[di.length];
                    for (int j = 0; j < hosts.length; j++) {
                        hosts[j] = di[j].getTld();
                    }

                    fps.setHosts(hosts);
                }

                if (queryParams.isSpeed()) {
                    fps.setSpeed(dwd.getDownloadSpeedbyFilePackage(fp));
                }
                if (queryParams.isStatus()) {

                    setStatus(fps, fp);
                }
                if (queryParams.isFinished()) {

                    fps.setFinished(fpView.isFinished());
                }
                if (queryParams.isEta()) {
                    fps.setEta(fpView.getETA());
                }
                if (queryParams.isBytesLoaded()) {
                    fps.setBytesLoaded(fpView.getDone());

                }

                if (queryParams.isComment()) {
                    fps.setComment(fp.getComment());

                }
                if (queryParams.isEnabled()) {
                    fps.setEnabled(fpView.isEnabled());
                }
                if (queryParams.isRunning()) {
                    fps.setRunning(dwd.getRunningFilePackages().contains(fp));
                }

                ret.add(fps);
            } finally {
                fp.getModifyLock().readUnlock(readL);
            }
        }
        return ret;
    }

    private void setStatus(FilePackageAPIStorableV2 fps, FilePackage fp) {

        FilePackageView view = fp.getView();

        PluginStateCollection ps = view.getPluginStates();
        if (ps.size() > 0) {

            fps.setStatusIconKey(RemoteAPIController.getInstance().getContentAPI().getIconKey(ps.getMergedIcon()));
            fps.setStatus(ps.isMultiline() ? "" : ps.getText());
            return;
        }
        if (view.isFinished()) {

            fps.setStatusIconKey(IconKey.ICON_TRUE);
            fps.setStatus(_GUI._.TaskColumn_getStringValue_finished_());
            return;
        } else if (view.getETA() != -1) {

            fps.setStatus(_GUI._.TaskColumn_getStringValue_running_());
            return;
        }

    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<DownloadLinkAPIStorableV2> queryLinks(LinkQueryStorable queryParams) {
        List<DownloadLinkAPIStorableV2> result = new ArrayList<DownloadLinkAPIStorableV2>();

        DownloadController dlc = DownloadController.getInstance();

        List<FilePackage> packages = null;

        if (queryParams.getPackageUUIDs() != null && queryParams.getPackageUUIDs().length > 0) {

            packages = convertIdsToPackages(queryParams.getPackageUUIDs());

        } else {
            packages = dlc.getPackagesCopy();
        }

        // collect children of the selected packages and convert to storables for response
        List<DownloadLink> links = new ArrayList<DownloadLink>();
        for (FilePackage pkg : packages) {
            boolean b = pkg.getModifyLock().readLock();
            try {
                links.addAll(pkg.getChildren());
            } finally {
                pkg.getModifyLock().readUnlock(b);
            }
        }

        if (links.isEmpty()) {
            return result;
        }

        int startWith = queryParams.getStartAt();
        int maxResults = queryParams.getMaxResults();

        if (startWith > links.size() - 1) {
            return result;
        }
        if (startWith < 0) {
            startWith = 0;
        }
        if (maxResults < 0) {
            maxResults = links.size();
        }

        for (int i = startWith; i < Math.min(startWith + maxResults, links.size()); i++) {

            DownloadLink dl = links.get(i);
            DownloadLinkAPIStorableV2 dls = new DownloadLinkAPIStorableV2(dl);
            if (queryParams.isPriority()) {
                dls.setPriority(org.jdownloader.myjdownloader.client.bindings.PriorityStorable.valueOf(dl.getPriorityEnum().name()));
            }
            if (queryParams.isHost()) {
                dls.setHost(dl.getHost());
            }
            if (queryParams.isBytesTotal()) {
                dls.setBytesTotal(dl.getView().getBytesTotalEstimated());
            }
            if (queryParams.isStatus()) {
                setStatus(dls, dl);

                // if (value instanceof DownloadLink)

                // } else {
                // FilePackage fp = (FilePackage) value;
                // FilePackageView view = fp.getView();
                //
                // PluginStateCollection ps = view.getPluginStates();
                // if (ps.size() > 0) {
                // icon = ps.getMergedIcon();
                // label = ps.isMultiline() ? "" : ps.getText();
                //
                // tooltip = ps.getText();
                // return;
                // }
                // if (view.isFinished()) {
                // icon = trueIcon;
                // label = finishedText;
                // tooltip = null;
                // return;
                // } else if (view.getETA() != -1) {
                // icon = null;
                // label = runningText;
                // tooltip = null;
                // return;
                // }
                // tooltip = null;
                // icon = null;
                // label = "";
                //
                // }
            }
            if (queryParams.isBytesLoaded()) {
                dls.setBytesLoaded(dl.getView().getBytesLoaded());
            }
            if (queryParams.isSpeed()) {
                dls.setSpeed(dl.getView().getSpeedBps());
            }
            if (queryParams.isEta()) {
                PluginProgress plg = dl.getPluginProgress();
                if (plg != null) {
                    dls.setEta(plg.getETA());
                } else {
                    dls.setEta(-1l);
                }
            }
            if (queryParams.isFinished()) {
                dls.setFinished((FinalLinkState.CheckFinished(dl.getFinalLinkState())));
            }

            if (queryParams.isRunning()) {
                dls.setRunning(dl.getDownloadLinkController() != null);
            }
            if (queryParams.isSkipped()) {
                dls.setSkipped(dl.isSkipped());
            }
            if (queryParams.isUrl()) {
                dls.setUrl(dl.getBrowserUrl());
            }
            if (queryParams.isEnabled()) {
                dls.setEnabled(dl.isEnabled());
            }
            if (queryParams.isExtractionStatus()) {
                if (dl.getExtractionStatus() != null) {
                    dls.setExtractionStatus(dl.getExtractionStatus().toString());
                }
            }

            dls.setPackageUUID(dl.getParentNode().getUniqueID().getID());

            result.add(dls);
        }

        return result;
    }

    private void setStatus(DownloadLinkAPIStorableV2 dls, DownloadLink link) {
        Icon icon = null;
        String label = null;
        PluginProgress prog = link.getPluginProgress();
        if (prog != null) {
            icon = prog.getIcon(this);
            label = prog.getMessage(this);
            if (icon != null) {
                dls.setStatusIconKey(RemoteAPIController.getInstance().getContentAPI().getIconKey(icon));
            }
            dls.setStatus(label);
            return;
        }

        ConditionalSkipReason conditionalSkipReason = link.getConditionalSkipReason();
        if (conditionalSkipReason != null && !conditionalSkipReason.isConditionReached()) {
            icon = conditionalSkipReason.getIcon(this, null);
            label = conditionalSkipReason.getMessage(this, null);
            dls.setStatusIconKey(RemoteAPIController.getInstance().getContentAPI().getIconKey(icon));
            dls.setStatus(label);
            return;
        }
        SkipReason skipReason = link.getSkipReason();
        if (skipReason != null) {

            icon = skipReason.getIcon(this, 18);
            label = skipReason.getExplanation(this);
            dls.setStatusIconKey(RemoteAPIController.getInstance().getContentAPI().getIconKey(icon));
            dls.setStatus(label);
            return;
        }
        final FinalLinkState finalLinkState = link.getFinalLinkState();
        if (finalLinkState != null) {
            if (FinalLinkState.CheckFailed(finalLinkState)) {

                label = finalLinkState.getExplanation(this, link);
                dls.setStatusIconKey(IconKey.ICON_FALSE);
                dls.setStatus(label);
                return;
            }
            ExtractionStatus extractionStatus = link.getExtractionStatus();
            if (extractionStatus != null) {
                switch (extractionStatus) {
                case ERROR:
                case ERROR_PW:
                case ERROR_CRC:
                case ERROR_NOT_ENOUGH_SPACE:
                case ERRROR_FILE_NOT_FOUND:

                    label = extractionStatus.getExplanation();
                    dls.setStatusIconKey(IconKey.ICON_EXTRACTION_TRUE_FAILED);
                    dls.setStatus(label);
                    return;
                case SUCCESSFUL:

                    label = extractionStatus.getExplanation();
                    dls.setStatusIconKey(IconKey.ICON_EXTRACTION_TRUE);

                    dls.setStatus(label);
                    return;
                case RUNNING:

                    label = extractionStatus.getExplanation();
                    dls.setStatusIconKey(IconKey.ICON_COMPRESS);
                    dls.setStatus(label);
                    return;

                }
            }
            if (FinalLinkState.FINISHED_MIRROR.equals(finalLinkState)) {
                dls.setStatusIconKey(IconKey.ICON_TRUE_ORANGE);
            } else {
                dls.setStatusIconKey(IconKey.ICON_TRUE);
            }
            label = finalLinkState.getExplanation(this, link);
            dls.setStatus(label);
            return;
        }
        if (link.getDownloadLinkController() != null) {

            dls.setStatusIconKey(IconKey.ICON_RUN);
            dls.setStatus(_GUI._.TaskColumn_fillColumnHelper_starting());
            return;
        }

    }

    @Override
    public int packageCount() {
        return DownloadController.getInstance().getPackages().size();
    }

    @Override
    public void removeLinks(final long[] linkIds, final long[] packageIds) {

        DownloadController dlc = DownloadController.getInstance();

        dlc.writeLock();
        dlc.removeChildren(getSelectionInfo(linkIds, packageIds).getChildren());
        dlc.writeUnlock();

    }

    @Override
    public void renamePackage(Long packageId, String newName) {
        DownloadController dlc = DownloadController.getInstance();
        try {
            dlc.writeLock();
            for (FilePackage fp : dlc.getPackages()) {
                if (packageId.equals(fp.getUniqueID().getID())) {
                    fp.setName(newName);
                    break;
                }
            }
        } finally {
            dlc.writeUnlock();
        }

    }

    @Override
    public void renameLink(Long linkId, String newName) {
        DownloadController dlc = DownloadController.getInstance();
        try {
            dlc.writeLock();
            DownloadLink link = dlc.getLinkByID(linkId);
            if (link != null) {
                link.setName(newName);
            }
        } finally {
            dlc.writeUnlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void movePackages(long[] packageIds, long afterDestPackageId) {
        List<FilePackage> selectedPackages = convertIdsToPackages(packageIds);
        FilePackage afterDestPackage = afterDestPackageId <= 0 ? null : convertIdsToPackages(afterDestPackageId).get(0);
        DownloadController.getInstance().move(selectedPackages, afterDestPackage);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void moveLinks(long[] linkIds, long afterLinkID, long destPackageID) {
        DownloadController dlc = DownloadController.getInstance();
        List<DownloadLink> selectedLinks = convertIdsToLinks(linkIds);
        DownloadLink afterLink = afterLinkID <= 0 ? null : convertIdsToLinks(afterLinkID).get(0);
        FilePackage destpackage = convertIdsToPackages(destPackageID).get(0);
        dlc.move(selectedLinks, destpackage, afterLink);

    }

    @Override
    public long getStructureChangeCounter(long structureWatermark) {
        DownloadController lc = DownloadController.getInstance();
        if (lc.getChildrenChanges() != structureWatermark) {
            return lc.getChildrenChanges();
        } else {
            return -1l;
        }
    }

    @Override
    public long getStopMark() {
        Object mark = DownloadWatchDog.getInstance().getSession().getStopMark();
        if (mark != STOPMARK.NONE) {
            return ((AbstractNode) mark).getUniqueID().getID();
        }
        return -1l;
    }

    /**
     * the SelectionInfo Class is actually used for the GUI downloadtable. it generates a logic selection out of selected links and
     * packages.
     * 
     * example: if a package is selected, and non if it's links - all its links will be in the selection info<br>
     * example2: if a package is selected AND SOME of it's children. The packge will not be considered as fully selected. only the actual
     * selected links.
     * 
     * @param linkIds
     * @param packageIds
     * @return
     */
    public static SelectionInfo<FilePackage, DownloadLink> getSelectionInfo(long[] linkIds, long[] packageIds) {

        return new SelectionInfo<FilePackage, DownloadLink>(null, convertIdsToObjects(linkIds, packageIds), false);

    }

    public static List<AbstractNode> convertIdsToObjects(long[] linkIds, long[] packageIds) {
        final ArrayList<AbstractNode> ret = new ArrayList<AbstractNode>();

        return convertIdsToObjects(ret, linkIds, packageIds);
    }

    public static List<FilePackage> convertIdsToPackages(long... packageIds) {
        final List<FilePackage> ret = new ArrayList<FilePackage>();

        convertIdsToObjects(ret, null, packageIds);
        return ret;
    }

    public static List<DownloadLink> convertIdsToLinks(long... linkIds) {
        final List<DownloadLink> ret = new ArrayList<DownloadLink>();

        convertIdsToObjects(ret, linkIds, null);
        return ret;
    }

    @SuppressWarnings("unchecked")
    public static <T extends AbstractNode> List<T> convertIdsToObjects(final List<T> ret, long[] linkIds, long[] packageIds) {
        final HashSet<Long> linklookUp = createLookupSet(linkIds);
        final HashSet<Long> packageLookup = createLookupSet(packageIds);

        DownloadController dlc = DownloadController.getInstance();

        if (linklookUp != null || packageLookup != null) {

            boolean readL = dlc.readLock();
            try {
                main: for (FilePackage pkg : dlc.getPackages()) {
                    if (packageLookup != null && packageLookup.remove(pkg.getUniqueID().getID())) {
                        ret.add((T) pkg);
                        if ((packageLookup == null || packageLookup.size() == 0) && (linklookUp == null || linklookUp.size() == 0)) {
                            break main;
                        }

                    }
                    if (linklookUp != null) {
                        boolean readL2 = pkg.getModifyLock().readLock();
                        try {
                            for (DownloadLink child : pkg.getChildren()) {

                                if (linklookUp.remove(child.getUniqueID().getID())) {
                                    ret.add((T) child);
                                    if ((packageLookup == null || packageLookup.size() == 0) && (linklookUp == null || linklookUp.size() == 0)) {
                                        break main;
                                    }
                                }

                            }
                        } finally {
                            pkg.getModifyLock().readUnlock(readL2);
                        }
                    }

                }
            } finally {
                dlc.readUnlock(readL);
            }

        }
        return ret;
    }

    public static HashSet<Long> createLookupSet(long[] linkIds) {
        if (linkIds == null || linkIds.length == 0) {
            return null;
        }
        HashSet<Long> linkLookup = new HashSet<Long>();
        for (long l : linkIds) {
            linkLookup.add(l);
        }
        return linkLookup;
    }

    @Override
    public void setEnabled(boolean enabled, long[] linkIds, long[] packageIds) {

        for (DownloadLink dl : getSelectionInfo(linkIds, packageIds).getChildren()) {
            dl.setEnabled(enabled);
        }

    }

    @Override
    public void resetLinks(long[] linkIds, long[] packageIds) {

        DownloadWatchDog.getInstance().reset(getSelectionInfo(linkIds, packageIds).getChildren());

    }

    @Override
    public void setPriority(PriorityStorable priority, long[] linkIds, long[] packageIds) throws BadParameterException {
        org.jdownloader.controlling.Priority jdPriority = org.jdownloader.controlling.Priority.valueOf(priority.name());
        for (DownloadLink dl : getSelectionInfo(linkIds, packageIds).getChildren()) {
            dl.setPriorityEnum(jdPriority);
        }
    }

    @Override
    public void setStopMark(long linkId, long packageId) {
        for (DownloadLink dl : getSelectionInfo(new long[] { linkId }, new long[] { packageId }).getChildren()) {
            DownloadWatchDog.getInstance().getSession().setStopMark(dl);
        }
    }

    @Override
    public void removeStopMark() {
        DownloadWatchDog.getInstance().getSession().setStopMark(null);
    }

    @Override
    public void resumeLinks(long[] linkIds, long[] packageIds) throws BadParameterException {
        DownloadWatchDog dwd = DownloadWatchDog.getInstance();
        List<DownloadLink> links = getSelectionInfo(linkIds, packageIds).getChildren();
        dwd.resume(links);
    }

    @Override
    public void setDownloadDirectory(String directory, long[] packageIds) {
        DownloadWatchDog dwd = DownloadWatchDog.getInstance();
        List<FilePackage> pkgs = convertIdsToPackages(packageIds);
        for (FilePackage pkg : pkgs) {
            dwd.setDownloadDirectory(pkg, directory);
        }
    }

}
