// SAMPLE display fixtures only. No inference, cryptographic proof, or blockchain execution.
import { ApiError } from "./api";
const hash = (value) => "0x" + value.repeat(64);
export const sampleId = hash("a");
export const sampleMeta = {
  chainId: "31337",
  registryAddress: "0x" + "1".repeat(40),
  transactionMode: "SAMPLE",
  proofMode: "MOCK_PROOF",
};
export const sampleRecord = {
  ...sampleMeta,
  threatId: sampleId,
  voiceprintHash: hash("b"),
  riskScore: 9000,
  registeredAt: "1789257600",
  zkProof: "0x53414d504c45",
};
export const sampleEvents = [
  {
    chainId: "31337",
    blockNumber: "5",
    blockHash: hash("c"),
    txHash: hash("d"),
    logIndex: "0",
    event: "ThreatRegistered",
    args: {
      threatId: sampleId,
      voiceprintHash: hash("b"),
      riskScore: 9000,
      registeredAt: "1789257600",
    },
  },
];
export const mockApi = {
  async events(cursor) {
    return {
      ...sampleMeta,
      events: cursor ? [] : structuredClone(sampleEvents),
      nextCursor: "sample-page-1",
      throughBlock: "5",
    };
  },
  async threat(id) {
    if (id.toLowerCase() !== sampleId)
      throw new ApiError("THREAT_NOT_FOUND", 404);
    return structuredClone(sampleRecord);
  },
  async submission(id) {
    const status = new Map([
      ["sample-registered", "REGISTERED"],
      ["sample-pending", "SUBMITTED"],
      ["sample-failed", "FAILED"],
    ]).get(id);
    if (!status) throw new ApiError("SUBMISSION_NOT_FOUND", 404);
    return {
      ...sampleMeta,
      submissionId: id,
      status,
      threatId: sampleId,
      txHash: status === "SUBMITTED" ? null : hash("d"),
      errorCode: status === "FAILED" ? "TRANSACTION_REVERTED" : null,
    };
  },
};
