import type { ReactNode } from "react";
import { LogOut } from "lucide-react";
import { Brand } from "./Brand";
export function Shell({
  demo,
  active,
  onNavigate,
  onDisconnect,
  children,
}: {
  demo: boolean;
  active: string;
  onNavigate: (section: string) => void;
  onDisconnect: () => void;
  children: ReactNode;
}) {
  return (
    <div className="application dlq-theme">
      <a className="skip-link" href="#workspace">
        Skip to workspace
      </a>
      <header className="masthead">
        <a className="brand" href="./index.html" aria-label="DLQDesk home">
          <Brand />
        </a>
        <span className="edition">/ record workshop</span>
        <nav className="primary-nav" aria-label="Workspace navigation">
          <button
            className={active === "inbox" ? "nav-item active" : "nav-item"}
            onClick={() => onNavigate("inbox")}
            aria-current={active === "inbox" ? "page" : undefined}
          >
            Dead-letter inbox
          </button>
          <button
            className={active === "replays" ? "nav-item active" : "nav-item"}
            onClick={() => onNavigate("replays")}
            aria-current={active === "replays" ? "page" : undefined}
          >
            Replay plans
          </button>
          <a
            className="nav-item"
            href="https://github.com/lMysticl/dlqdesk/blob/main/docs/usage.md"
            target="_blank"
            rel="noreferrer"
          >
            Field guide ↗
          </a>
        </nav>
        <div className="connection-label">
          <span className="connection-dot" />
          {demo ? "LOCAL DEMO" : "CONNECTED"}
        </div>
        <button
          className="icon-button"
          title="Disconnect"
          aria-label="Disconnect"
          onClick={onDisconnect}
        >
          <LogOut size={16} />
        </button>
      </header>
      <main id="workspace" tabIndex={-1}>
        {children}
      </main>
      <footer className="studio-footer">
        <span>FROM DEAD LETTERS TO LIVE AGAIN.</span>
        <a href="https://github.com/lMysticl/dlqdesk" target="_blank" rel="noreferrer">
          OPEN SOURCE / v0.3.0 ↗
        </a>
      </footer>
    </div>
  );
}
