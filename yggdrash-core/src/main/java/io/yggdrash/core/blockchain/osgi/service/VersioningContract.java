/*
 * Copyright 2019 Akashic Foundation
 * Licensed under the Apache License, Version 2.0 (the “License”);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an “AS IS” BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package io.yggdrash.core.blockchain.osgi.service;

import com.google.gson.JsonObject;
import com.sun.jdi.event.EventSet;
import io.yggdrash.common.contract.ContractVersion;
import io.yggdrash.common.store.BranchStateStore;
import io.yggdrash.common.utils.JsonUtil;
import io.yggdrash.contract.core.ContractEvent;
import io.yggdrash.contract.core.ContractEventSet;
import io.yggdrash.contract.core.ExecuteStatus;
import io.yggdrash.contract.core.Receipt;
import io.yggdrash.contract.core.annotation.ContractBranchStateStore;
import io.yggdrash.contract.core.annotation.ContractEndBlock;
import io.yggdrash.contract.core.annotation.ContractQuery;
import io.yggdrash.contract.core.annotation.ContractStateStore;
import io.yggdrash.contract.core.annotation.ContractReceipt;
import io.yggdrash.contract.core.annotation.Genesis;
import io.yggdrash.contract.core.annotation.InvokeTransaction;
import io.yggdrash.contract.core.channel.ContractEventType;
import io.yggdrash.contract.core.store.ReadWriterStore;
import io.yggdrash.core.blockchain.osgi.ContractConstants;
import io.yggdrash.core.blockchain.osgi.ContractStatus;
import io.yggdrash.core.blockchain.osgi.Downloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;


public class VersioningContract {

    private static final Logger log = LoggerFactory.getLogger(VersioningContract.class);
    private static final String SUFFIX_TEMP_CONTRACT = "/update-temp-contracts";
    private static final String SUFFIX_CONTRACT = "/.yggdrash/contract";

    private static final String LOG_PREFIX = "[Versioning Contract]";

    @ContractStateStore
    ReadWriterStore<String, JsonObject> state;

    @ContractBranchStateStore
    BranchStateStore branchStore;

    @ContractReceipt
    Receipt receipt;

    @ContractQuery
    public ContractProposal proposalStatus(JsonObject params) {
        return getProposal(params.get("txId").getAsString());
    }

    @Genesis
    @InvokeTransaction
    public Receipt init(JsonObject params) {
        log.info("Init VersioningContract");
        return receipt;
    }

    @InvokeTransaction
    public Receipt propose(JsonObject params) {
        // Verify that the issuer is a validator
        if (!isValidator()) {
            setFalseTxReceipt("Validator verification failed");
            return receipt;
        }

        // Create a contract proposal with parameters.
        String txId = receipt.getTxId();
        String proposer = receipt.getIssuer();
        long blockHeight = receipt.getBlockHeight();
        String proposalVersion = params.get("proposalVersion").getAsString();
        String sourceUrl = params.get("sourceUrl").getAsString();
        String buildVersion = params.get("buildVersion").getAsString();
        Set<String> validatorSet = new HashSet<>(branchStore.getValidators().getValidatorMap().keySet());

        // blockHeight => targetBlockHeight
        ContractProposal proposal = new ContractProposal(
                txId, proposer, proposalVersion, sourceUrl, buildVersion, blockHeight, validatorSet);

        // The proposer automatically votes to agree
        proposal.vote(proposer, true);

        JsonObject proposalObj = JsonUtil.parseJsonObject(proposal);
        // Store the proposal in stateStore
        state.put(txId, JsonUtil.parseJsonObject(proposal));
        state.put(String.valueOf(proposal.getTargetBlockHeight()), JsonUtil.parseJsonObject(proposal));

        setSuccessTxReceipt("Contract proposal has been issued");
        log.info("Contract Proposal : txId = {}, proposal = {}", txId, proposalObj);

        return receipt;
    }

    @InvokeTransaction
    public Receipt vote(JsonObject params) {
        // Verify that the issuer is a validator
        if (!isValidator()) {
            setFalseTxReceipt("Validator verification failed");
            return receipt;
        }

        String txId = params.get("txId").getAsString();
        ContractProposal proposal = getProposal(txId);
        // Verify the proposal exists
        if (proposal == null) {
            setFalseTxReceipt("Contract proposal not found");
            return receipt;
        }

        long curBlockHeight = receipt.getBlockHeight();
        // Verify the proposal is expired
        if (proposal.isExpired(curBlockHeight)) {
            setFalseTxReceipt("Contract proposal has already expired");
            proposal.setVotingStatus(VotingProgress.VotingStatus.EXPIRED);
            return receipt;
        }

        String issuer = receipt.getIssuer();
        // Verify if the validator has already voted
        if (proposal.hasAlreadyVoted(issuer)) {
            setFalseTxReceipt("Validator has already voted");
            return receipt;
        }

        boolean agree = params.get("agree").getAsBoolean();
        proposal.vote(issuer, agree);

        state.put(txId, JsonUtil.parseJsonObject(proposal));
        state.put(String.valueOf(proposal.getTargetBlockHeight()), JsonUtil.parseJsonObject(proposal));

        setSuccessTxReceipt("Update proposal voting is in progress");

        return receipt;
    }

    @ContractEndBlock
    public Receipt endBlock() {
        // Check if an event of the current block height exists in the StateStore.
        String currentBlockHeight = String.valueOf(receipt.getBlockHeight());
        if (state.contains(currentBlockHeight)) {
            /**
             * todo : add event set.
             * 2. the vote ends with agree or disagree then add load bundle(download, install, start, inject) event
             * 3. add receipt log!
             */

            JsonObject proposalJson = state.get(currentBlockHeight);
            ContractProposal proposal = JsonUtil.generateJsonToClass(proposalJson.toString(), ContractProposal.class);
            VotingProgress.VotingStatus votingStatus = proposal.getVotingProgress().getVotingStatus();

            ContractEventSet eventSet = new ContractEventSet();

            switch (votingStatus) {
                case VOTEABLE:
                    log.debug("the vote not end. It will be expired. tx id : {}", proposal.getTxId());
                    proposal.setVotingStatus(VotingProgress.VotingStatus.EXPIRED);
                    state.put(proposal.getTxId(), JsonUtil.parseJsonObject(proposal));
                    setSuccessTxReceipt(
                            String.format("%s Expire proposal tx id : %s", LOG_PREFIX, proposal.getTxId()));
                    break;
                case AGREE:
                    log.debug("The vote ends with agreed. It will be updated.");
                    ContractEvent agreeEvent = new ContractEvent(ContractEventType.AGREE, proposal, ContractConstants.VERSIONING_CONTRACT.toString());
                    eventSet.addEvents(agreeEvent);

                    proposal.setVotingStatus(VotingProgress.VotingStatus.APPLYING);

                    state.put(proposal.getTxId(), JsonUtil.parseJsonObject(proposal));
                    state.put(String.valueOf(proposal.getApplyBlockHeight()), JsonUtil.parseJsonObject(proposal));

                    setSuccessTxReceipt(
                            String.format("%s The vote ends up with agreed. tx id : %s", LOG_PREFIX, proposal.getTxId())
                    );
                    break;
                case DISAGREE:
                    log.debug("The vote end with disagreed.");
                    setSuccessTxReceipt(
                            String.format("%s The vote ends up with disagreed. tx id : %s", LOG_PREFIX, proposal.getTxId())
                    );
                    break;
                case APPLYING:
                    log.debug("The proposal is applying.");
                    ContractEvent applyEvent = new ContractEvent(ContractEventType.APPLY, proposal, ContractConstants.VERSIONING_CONTRACT.toString());
                    eventSet.addEvents(applyEvent);
                    setSuccessTxReceipt(
                            String.format("%s The proposal is applying. tx id : %s", LOG_PREFIX, proposal.getTxId())
                    );
                    break;
                case EXPIRED:
                default:
                    log.warn("The vote is in unknown status.");
                    break;
            }

            receipt.setEvent(eventSet);
        }
        return receipt;
    }

    private void moveTmpContract(String contractVersion) throws IOException {
        Path tmp = Paths.get(String.format("%s/%s", tmpContractPath(), contractVersion + ".jar"));
        Path origin = Paths.get(contractPath());
        Files.move(tmp, origin.resolve(tmp.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    }

    private File downloadContractFile(String contractVersion) {
        return Downloader.downloadContract(tmpContractPath(), ContractVersion.of(contractVersion));
    }

    private String tmpContractPath() {
        return String.format("%s%s", contractPath(), SUFFIX_TEMP_CONTRACT);
    }

    private String contractPath() {
        Path path = Paths.get(System.getProperty("user.home"));
        return String.format("%s%s", path, SUFFIX_CONTRACT);
    }

    private ContractProposal getProposal(String txId) {
        JsonObject proposalObj = state.get(txId);
        return proposalObj != null
                ? JsonUtil.generateJsonToClass(proposalObj.toString(), ContractProposal.class) : null;
    }

    private boolean isValidator() {
        return branchStore.getValidators() != null && branchStore.getValidators().getValidatorMap().keySet().stream()
                .anyMatch(key -> !key.isEmpty() && key.equals(receipt.getIssuer()));
    }

    private void setFalseTxReceipt(String msg) {
        this.receipt.setStatus(ExecuteStatus.FALSE);
        this.receipt.addLog(msg);
    }

    private void setSuccessTxReceipt(String msg) {
        this.receipt.setStatus(ExecuteStatus.SUCCESS);
        this.receipt.addLog(msg);
    }

}
