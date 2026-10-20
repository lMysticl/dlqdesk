import type { ReactNode } from "react";
import { BookOpen, ListTree, LogOut, Radio, Workflow } from "lucide-react";

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
    <div className={"application dlq-theme"}>
      <aside className="sidebar">
        <a className="brand" href={"./index.html"}>
          <span className="brand-mark">D</span>
          <span>DLQDesk</span>
        </a>
        <div className="sidebar-label">WORKSPACE</div>
        <nav aria-label="Workspace navigation">
          <button
            className={active === "inbox" ? "nav-item active" : "nav-item"}
            onClick={() => onNavigate("inbox")}
            aria-current={active === "inbox" ? "page" : undefined}
          >
            <ListTree size={18} />
            Dead-letter inbox
          </button>
          <button
            className={active === "replays" ? "nav-item active" : "nav-item"}
            onClick={() => onNavigate("replays")}
            aria-current={active === "replays" ? "page" : undefined}
          >
            <Workflow size={18} />
            Replay plans
          </button>
          <a
            className="nav-item"
            href={"https://github.com/lMysticl/dlqdesk/blob/main/docs/usage.md"}
            target="_blank"
            rel="noreferrer"
          >
            <BookOpen size={18} />
            Documentation
          </a>
        </nav>
        <div className="sidebar-footer">
          <span className="version-tag">v0.1.0</span>
          <p>A deliberate path to recovery.</p>
          <a href={"https://github.com/lMysticl/dlqdesk"} target="_blank" rel="noreferrer">
            View source ↗
          </a>
        </div>
      </aside>
      <div className="application-body">
        <header className="topbar">
          <div className="breadcrumb">
            <Radio size={14} />
            <span>DLQDesk</span>
            <span className="slash">/</span>
            <span>{active === "replays" ? "Replay plans" : "Inbox"}</span>
          </div>
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
            <LogOut size={17} />
          </button>
        </header>
        <main id="workspace">{children}</main>
      </div>
    </div>
  );
}
