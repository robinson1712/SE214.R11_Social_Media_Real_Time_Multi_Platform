type Frame = { command: string; headers: Record<string, string>; body: string };
const escapeHeader = (value: string) => value.replace(/\\/g, "\\\\").replace(/\r/g, "\\r").replace(/\n/g, "\\n").replace(/:/g, "\\c");
const unescapeHeader = (value: string) => value.replace(/\\([nrc\\])/g, (_, key: string) => ({ n: "\n", r: "\r", c: ":", "\\": "\\" })[key]!);

export function encodeFrame(command: string, headers: Record<string, string> = {}, body = "") {
  const values = { ...headers, ...(body ? { "content-length": String(new TextEncoder().encode(body).length) } : {}) };
  return `${command}\n${Object.entries(values).map(([key, value]) => `${escapeHeader(key)}:${escapeHeader(value)}`).join("\n")}\n\n${body}\0`;
}
export function parseFrames(input: string): { frames: Frame[]; rest: string } {
  const frames: Frame[] = [];
  let rest = input;
  while (true) {
    rest = rest.replace(/^[\r\n]+/, "");
    const end = rest.indexOf("\0");
    if (end < 0) break;
    const raw = rest.slice(0, end).replace(/\r\n/g, "\n");
    rest = rest.slice(end + 1);
    const split = raw.indexOf("\n\n");
    if (split < 0) throw new Error("STOMP frame không hợp lệ.");
    const [command, ...lines] = raw.slice(0, split).split("\n");
    const headers: Record<string, string> = {};
    for (const line of lines) {
      const colon = line.indexOf(":");
      if (colon >= 0) {
        const key = unescapeHeader(line.slice(0, colon));
        if (!(key in headers)) headers[key] = unescapeHeader(line.slice(colon + 1));
      }
    }
    const body = raw.slice(split + 2);
    if (headers["content-length"] && Number(headers["content-length"]) !== new TextEncoder().encode(body).length) throw new Error("STOMP content-length không hợp lệ.");
    frames.push({ command, headers, body });
  }
  return { frames, rest };
}

type Options = {
  url: () => string;
  destination: string;
  onMessage: (message: unknown) => void;
  onStatus?: (connected: boolean) => void;
  onError?: (message: string) => void;
  reconnectDelay?: number;
};
export class StompConnection {
  private options: Options;
  private socket: WebSocket | null = null;
  private connected = false;
  private stopped = false;
  private buffer = "";
  private pending: Promise<void> | null = null;
  private resolve?: () => void;
  private reject?: (error: Error) => void;
  private retry?: ReturnType<typeof setTimeout>;
  private timeout?: ReturnType<typeof setTimeout>;
  private heartbeat?: ReturnType<typeof setInterval>;
  constructor(options: Options) { this.options = options; }
  isConnected() { return this.connected; }
  connect(): Promise<void> {
    if (this.connected) return Promise.resolve();
    if (this.pending) return this.pending;
    this.stopped = false;
    clearTimeout(this.retry);
    this.pending = new Promise<void>((resolve, reject) => { this.resolve = resolve; this.reject = reject; });
    const pending = this.pending;
    const url = this.options.url();
    let socket: WebSocket;
    try { socket = new WebSocket(url, ["v12.stomp", "v11.stomp", "v10.stomp"]); }
    catch (error) { this.failed(error instanceof Error ? error.message : "Không mở được chat."); return pending; }
    this.socket = socket;
    this.buffer = "";
    this.timeout = setTimeout(() => { if (this.socket === socket && !this.connected) socket.close(); }, 10_000);
    socket.onopen = () => {
      if (this.socket !== socket) return;
      socket.send(encodeFrame("CONNECT", { "accept-version": "1.2", host: new URL(url).hostname, "heart-beat": "10000,10000" }));
    };
    socket.onmessage = event => {
      if (this.socket !== socket || this.stopped) return;
      try {
        if (typeof event.data !== "string") throw new Error("STOMP yêu cầu text frame.");
        this.buffer += event.data;
        if (this.buffer.length > 262_144) throw new Error("STOMP frame vượt giới hạn.");
        const { frames, rest } = parseFrames(this.buffer);
        this.buffer = rest;
        for (const frame of frames) {
          if (frame.command === "CONNECTED") {
            clearTimeout(this.timeout);
            this.connected = true;
            socket.send(encodeFrame("SUBSCRIBE", { id: "user-events", destination: this.options.destination, ack: "auto" }));
            const heartbeat = Number(frame.headers["heart-beat"]?.split(",")[1] || 0);
            if (heartbeat > 0) this.heartbeat = setInterval(() => { if (socket.readyState === WebSocket.OPEN) socket.send("\n"); }, Math.max(10_000, heartbeat));
            this.resolve?.(); this.pending = null; this.resolve = undefined; this.reject = undefined;
            this.options.onStatus?.(true);
          } else if (frame.command === "MESSAGE") {
            this.options.onMessage(JSON.parse(frame.body));
          } else if (frame.command === "ERROR") {
            throw new Error(frame.headers.message || frame.body || "Backend từ chối thao tác realtime.");
          }
        }
      } catch (error) {
        this.options.onError?.(error instanceof Error ? error.message : "Lỗi realtime.");
        socket.close();
      }
    };
    socket.onerror = () => { /* Close carries the single reconnect/error transition. */ };
    socket.onclose = () => { if (this.socket === socket) this.failed("Realtime mất kết nối. Đang thử kết nối lại."); };
    return pending;
  }
  private failed(message: string) {
    clearTimeout(this.timeout); clearInterval(this.heartbeat);
    this.connected = false; this.socket = null;
    this.reject?.(new Error(message));
    this.pending = null; this.resolve = undefined; this.reject = undefined;
    this.options.onStatus?.(false);
    if (!this.stopped) this.retry = setTimeout(() => { void this.connect().catch(() => {}); }, this.options.reconnectDelay ?? 5000);
  }
  send(destination: string, payload: unknown) {
    if (!this.connected || this.socket?.readyState !== WebSocket.OPEN) throw new Error("Chat chưa kết nối. Nội dung của bạn vẫn được giữ lại.");
    const body = JSON.stringify(payload);
    if (new TextEncoder().encode(body).length > 60_000) throw new Error("Tin nhắn vượt giới hạn kích thước.");
    this.socket.send(encodeFrame("SEND", { destination, "content-type": "application/json" }, body));
  }
  disconnect() {
    this.stopped = true;
    clearTimeout(this.retry); clearTimeout(this.timeout); clearInterval(this.heartbeat);
    const socket = this.socket;
    this.socket = null;
    this.connected = false;
    this.reject?.(new Error("Chat đã ngắt kết nối."));
    this.pending = null; this.resolve = undefined; this.reject = undefined;
    if (socket) { socket.onclose = null; socket.close(); }
    this.options.onStatus?.(false);
  }
}
