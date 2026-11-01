export type Session = { accountId: string; email: string; accessToken: string; refreshToken: string };
type SessionStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;
export type RequestOptions = Omit<RequestInit, "body"> & { body?: unknown; anonymous?: boolean; retryAuth?: boolean };
const SESSION_KEY = "vibesocial.session.v1";

export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) { super(message); this.status = status; this.name = "ApiError"; }
}
export class SessionChangedError extends Error {
  constructor() { super("Phiên đăng nhập đã thay đổi. Vui lòng thử lại."); this.name = "SessionChangedError"; }
}

export class ApiClient {
  readonly baseUrl: string;
  private session: Session | null = null;
  private epoch = 0;
  private refreshPromise: Promise<void> | null = null;
  private listeners = new Set<() => void>();
  private storage?: SessionStorage;
  constructor(options: { baseUrl?: string; storage?: SessionStorage } = {}) {
    this.baseUrl = (options.baseUrl ?? "").replace(/\/$/, "");
    this.storage = options.storage;
  }
  getSession() { return this.session; }
  getVersion() { return this.epoch; }
  subscribe(listener: () => void) { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; }
  private browserStorage() {
    if (this.storage) return this.storage;
    try { return typeof window === "undefined" ? undefined : window.sessionStorage; } catch { return undefined; }
  }
  private persist() {
    try {
      if (this.session) this.browserStorage()?.setItem(SESSION_KEY, JSON.stringify(this.session));
      else this.browserStorage()?.removeItem(SESSION_KEY);
    } catch { /* Session still works in memory when browser storage is unavailable. */ }
    this.listeners.forEach(listener => listener());
  }
  setSession(session: Session | null) {
    this.epoch++;
    this.session = session;
    this.refreshPromise = null;
    this.persist();
  }
  restoreSession() {
    try {
      const raw = this.browserStorage()?.getItem(SESSION_KEY);
      const saved: unknown = raw ? JSON.parse(raw) : null;
      if (saved && typeof saved === "object" && ["accountId", "email", "accessToken", "refreshToken"].every(key => typeof (saved as Record<string, unknown>)[key] === "string" && (saved as Record<string, string>)[key])) {
        this.setSession(saved as Session);
      }
    } catch { this.setSession(null); }
  }
  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    if (!path.startsWith("/api/")) throw new ApiError("Đường dẫn API không hợp lệ.", 0);
    const { body, anonymous = false, retryAuth = true, ...init } = options;
    const epoch = this.epoch;
    const accessToken = this.session?.accessToken;
    const headers = new Headers(init.headers);
    headers.set("Accept", "application/json");
    if (!anonymous && this.session) headers.set("Authorization", `Bearer ${this.session.accessToken}`);
    const multipart = typeof FormData !== "undefined" && body instanceof FormData;
    if (body !== undefined && !multipart) headers.set("Content-Type", "application/json");
    let response: Response;
    try {
      response = await fetch(`${this.baseUrl}${path}`, {
        ...init, headers, body: body === undefined ? undefined : multipart ? body as FormData : JSON.stringify(body),
        signal: init.signal ? AbortSignal.any([init.signal, AbortSignal.timeout(20_000)]) : AbortSignal.timeout(20_000),
        cache: "no-store",
      });
    } catch (error) {
      if (epoch !== this.epoch) throw new SessionChangedError();
      if (error instanceof Error && error.name === "AbortError") throw error;
      throw new ApiError("Không kết nối được backend. Kiểm tra kết nối hoặc thử lại.", 0);
    }
    if (epoch !== this.epoch) throw new SessionChangedError();
    if (response.status === 401 && !anonymous && retryAuth && this.session) {
      if (accessToken === this.session.accessToken) await this.refresh();
      if (epoch !== this.epoch) throw new SessionChangedError();
      return this.request<T>(path, { ...options, retryAuth: false });
    }
    if (response.status === 204) return undefined as T;
    let envelope: { success?: boolean; message?: string; data?: T };
    try { envelope = await response.json(); }
    catch { throw new ApiError(`API không trả JSON (HTTP ${response.status}). Kiểm tra gateway/proxy.`, response.status); }
    if (epoch !== this.epoch) throw new SessionChangedError();
    if (!envelope || typeof envelope !== "object" || Array.isArray(envelope)) throw new ApiError("API trả JSON không đúng định dạng.", response.status);
    if (!response.ok || envelope.success !== true) {
      if (response.status === 401 && !anonymous) this.setSession(null);
      throw new ApiError(envelope.message || `Yêu cầu thất bại (HTTP ${response.status}).`, response.status);
    }
    return envelope.data as T;
  }
  private async refresh() {
    if (!this.refreshPromise) {
      const epoch = this.epoch;
      const refreshToken = this.session?.refreshToken;
      this.refreshPromise = (async () => {
        try {
          if (!refreshToken) throw new ApiError("Vui lòng đăng nhập lại.", 401);
          const tokens = await this.request<{ accessToken: string; refreshToken?: string }>("/api/auth/refresh", {
            method: "POST", body: { refreshToken }, anonymous: true,
          });
          if (epoch !== this.epoch || !this.session) throw new SessionChangedError();
          this.session = { ...this.session, ...tokens };
          this.persist();
        } catch (error) {
          if (epoch === this.epoch) this.setSession(null);
          throw error;
        } finally {
          if (epoch === this.epoch) this.refreshPromise = null;
        }
      })();
    }
    return this.refreshPromise;
  }
  async login(email: string, password: string) {
    const session = await this.request<Session>("/api/auth/login", { method: "POST", body: { email, password }, anonymous: true });
    this.setSession(session);
    return session;
  }
  async register(input: { email: string; password: string; fullName: string }) {
    const session = await this.request<Session>("/api/auth/register", { method: "POST", body: input, anonymous: true });
    this.setSession(session);
    return session;
  }
  async logout() {
    const previous = this.session;
    this.setSession(null);
    if (!previous) return;
    try {
      await this.request("/api/auth/logout", {
        method: "POST", anonymous: true,
        headers: { Authorization: `Bearer ${previous.accessToken}` }, body: { refreshToken: previous.refreshToken },
      });
    } catch { /* Local logout must work even when the backend is offline. */ }
  }
}
export const api = new ApiClient({ baseUrl: process.env.NEXT_PUBLIC_API_BASE_URL || "" });
