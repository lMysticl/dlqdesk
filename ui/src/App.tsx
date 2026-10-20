import { useMemo, useState } from "react";
import { createClient } from "./api/client";
import type { Info } from "./api/types";
import { Login } from "./components/Login";
import { Shell } from "./components/Shell";
import { InboxWorkspace } from "./inbox/InboxWorkspace";
import { ReplayWorkspace } from "./replays/ReplayWorkspace";

function Connected({
  token,
  info,
  disconnect,
}: {
  token: string;
  info: Info;
  disconnect: () => void;
}) {
  const client = useMemo(() => createClient(token), [token]);
  const [section, setSection] = useState("inbox");
  const [selected, select] = useState<string>();
  function openReplay(id: string) {
    select(id);
    setSection("replays");
  }
  return (
    <Shell demo={info.demo} active={section} onNavigate={setSection} onDisconnect={disconnect}>
      {section === "inbox" ? (
        <InboxWorkspace client={client} info={info} openReplay={openReplay} />
      ) : (
        <ReplayWorkspace
          client={client}
          selected={selected}
          select={select}
          inbox={() => setSection("inbox")}
        />
      )}
    </Shell>
  );
}
export default function App() {
  const [session, setSession] = useState<{ token: string; info: Info }>();
  async function connect(token: string) {
    const info = await createClient(token).get<Info>("info");
    sessionStorage.setItem("dlqdesk-token", token);
    setSession({ token, info });
  }
  function disconnect() {
    sessionStorage.removeItem("dlqdesk-token");
    setSession(undefined);
  }
  return session ? (
    <Connected token={session.token} info={session.info} disconnect={disconnect} />
  ) : (
    <Login initialToken={sessionStorage.getItem("dlqdesk-token") ?? ""} onConnect={connect} />
  );
}
