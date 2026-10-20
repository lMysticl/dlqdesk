export interface Info {
  version: string;
  sourceTopic: string;
  targetTopic: string;
  demo: boolean;
  maxSelection: number;
  maxRate: number;
}
export interface Reference {
  partition: number;
  offset: string;
}
export interface Header {
  name: string;
  base64: string | null;
  text: string | null;
}
export interface Source {
  reference: Reference;
  keyBase64: string | null;
  keyText: string | null;
  payloadBase64: string | null;
  payloadText: string | null;
  timestamp: number;
  headers: Header[];
  sourceHash: string;
  validJson: boolean;
  problem: string | null;
}
export interface Message {
  reference: Reference;
  key: string | null;
  timestamp: number;
  sourceHash: string;
  validJson: boolean;
  problem: string | null;
  error: string | null;
}
export interface Page {
  partition: number;
  beginningOffset: string;
  endOffset: string;
  nextOffset: string;
  messages: Message[];
}
export interface Selection extends Reference {
  expectedHash: string;
  editedJson: string | null;
}
export interface Run {
  id: string;
  state:
    "DRAFT" | "RUNNING" | "PAUSE_REQUESTED" | "PAUSED" | "COMPLETED" | "INTERRUPTED" | "FAILED";
  createdAt: string;
  updatedAt: string;
  sourceTopic: string;
  targetTopic: string;
  rate: number;
  total: number;
  sent: number;
  pending: number;
  inFlight: number;
  unknown: number;
  skipped: number;
}
export interface Entry {
  index: number;
  source: Reference;
  key: string | null;
  sourceHash: string;
  payloadHash: string;
  edited: boolean;
  state: "PENDING" | "SENDING" | "SENT" | "UNKNOWN" | "SKIPPED";
  targetPartition: number | null;
  targetOffset: string | null;
  error: string | null;
}
export interface RunView {
  run: Run;
  entries: Entry[];
  audit: { timestamp: string; action: string; detail: string }[];
}
export interface Frozen {
  key: string | null;
  originalPayload: string | null;
  outputPayload: string;
  headers: Header[];
}
export const referenceId = (reference: Reference) => `${reference.partition}:${reference.offset}`;
