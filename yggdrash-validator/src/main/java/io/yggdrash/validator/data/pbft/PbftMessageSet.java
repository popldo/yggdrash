package io.yggdrash.validator.data.pbft;

import java.util.List;

public class PbftMessageSet {

    private PbftMessage prePrepare;
    private List<PbftMessage> prepareList;
    private List<PbftMessage> commitList;

    public PbftMessageSet(PbftMessage prePrepare, List<PbftMessage> prepareList, List<PbftMessage> commitList) {
        this.prePrepare = prePrepare;
        this.prepareList = prepareList;
        this.commitList = commitList;
    }

    public PbftMessage getPrePrepare() {
        return prePrepare;
    }

    public void setPrePrepare(PbftMessage prePrepare) {
        this.prePrepare = prePrepare;
    }

    public List<PbftMessage> getPrepareList() {
        return prepareList;
    }

    public void setPrepareList(List<PbftMessage> prepareList) {
        this.prepareList = prepareList;
    }

    public List<PbftMessage> getCommitList() {
        return commitList;
    }

    public void setCommitList(List<PbftMessage> commitList) {
        this.commitList = commitList;
    }
}
