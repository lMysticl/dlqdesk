import { Brand } from "./Brand";
import { useState } from "react";
import type { FormEvent } from "react";
import { ArrowRight, KeyRound, ShieldCheck } from "lucide-react";

export function Login({
  initialToken,
  onConnect,
}: {
  initialToken: string;
  onConnect: (token: string) => Promise<void>;
}) {
  const [token, setToken] = useState(initialToken);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  async function submit(event: FormEvent) {
    event.preventDefault();
    setPending(true);
    setError("");
    try {
      await onConnect(token);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Could not connect");
    } finally {
      setPending(false);
    }
  }
  return (
    <div className={"login-page dlq-theme"}>
      <div className="login-art">
        <div className="brand">
          <Brand />
        </div>
        <div>
          <p className="eyebrow">OPERATIONS, IN FOCUS</p>
          <h1>
            Failed delivery.
            <br />
            Deliberate recovery.
          </h1>
          <p className="login-description">
            Inspect Kafka dead letters, preview what will change, and replay with a durable delivery
            ledger.
          </p>
          <div className="login-diagram" aria-hidden="true">
            <span /> <i /> <span /> <i /> <span className="diagram-accent" />
          </div>
        </div>
        <span className="login-footnote">Open source · Self-hosted · Java + React</span>
      </div>
      <div className="login-form-area">
        <form className="login-form" onSubmit={(event) => void submit(event)}>
          <div className="login-key">
            <KeyRound size={24} />
          </div>
          <p className="eyebrow">YOUR WORKSPACE</p>
          <h2>Connect to DLQDesk</h2>
          <p className="muted">Use the API token configured by the application owner.</p>
          <label htmlFor="api-token">API token</label>
          <input
            id="api-token"
            type="password"
            autoComplete="off"
            value={token}
            onChange={(event) => setToken(event.target.value)}
            placeholder="Paste your token"
            required
            minLength={32}
            disabled={pending}
          />
          {error && (
            <p className="error-banner" role="alert">
              {error}
            </p>
          )}
          <button className="button primary wide" disabled={pending || token.length < 32}>
            {pending ? "Connecting…" : "Connect workspace"}
            <ArrowRight size={16} />
          </button>
          <button
            className="text-button demo-token"
            type="button"
            disabled={pending}
            onClick={() => setToken("local-demo-token-change-this-before-deployment")}
          >
            Use the bundled local demo token
          </button>
          <p className="security-note">
            <ShieldCheck size={16} />
            Your token stays in this browser tab. It is sent only to this application's API.
          </p>
        </form>
      </div>
    </div>
  );
}
