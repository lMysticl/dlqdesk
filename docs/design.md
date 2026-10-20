# Design contract — Porcelain & Vermilion

The user selected reference B on 20 October 2026 after rejecting the previous administration-style composition. The new artistic reference preceded this implementation. Its palette is shared by both products: porcelain `#f7f1e7`, cocoa `#342d29`, lavender `#d8d1e8`, plum `#3e273c` and vermilion `#e9472b`. Text-bearing action backgrounds use a deeper vermilion `#d13923` to preserve contrast.

## Composition

DLQDesk is a record workshop. A lavender message index spans the top. The working spread contains source coordinates and original provenance, a dominant plum JSON editor and a preview/rate margin. The route below distinguishes source, review and explicit publication. Recent plans and the delivery ledger use the same typographic family; broker acknowledgements stay distinct from downstream processing success.

## Type and implementation

Newsreader supplies the serif masthead, headings, large counts and italic margin notes. Geist Sans carries controls and Geist Mono carries machine data. Fonts ship locally with SIL OFL licenses and pinned source hashes in `ui/public/fonts/provenance.json`. There is no runtime font CDN. Product glyphs, routes and covers are code-native vectors; reference images are not used as application screenshots.

## Interaction and states

- Horizontal navigation and compact headings preserve working space. There is no persistent sidebar, marketing hero or generic KPI tile grid.
- At narrow widths the score and workshop stack in reading order. Source notes remain reachable; long IDs, JSON and errors wrap. Only delivery tables can scroll horizontally.
- All actions are named, statuses use text and color, focus is visible, and a skip link reaches the workspace. Native dialogs retain Escape and focus restoration.
- Empty, loading, failed refresh, selected, disabled, read-only, confirmation and recovery states use the same visual language. Failed refresh retains the last successful result with a visible retry.
- The server remains the source of truth. Preview does not publish. Confirmation precedes replay; SENT and UNKNOWN preserve their documented meaning.

## Verification

Use the documented frontend checks and actual packaged-JAR browser journeys. Inspect desktop and mobile captures against reference B, font loading, login, dialogs, failure detail, selected records and replay. The automated browser target is Chromium, including a mobile viewport; this is not evidence of physical-device, Safari or Firefox testing. Java 21/25 on Ubuntu/Windows is a separate compatibility gate.

## Sources

- [Newsreader sources](https://github.com/google/fonts/tree/8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5/ofl/newsreader): the locally bundled editorial typeface and OFL.
- [Geist typography](https://vercel.com/geist/typography): functional hierarchy and numeric rhythm.
- [Figma UI3 rationale](https://www.figma.com/blog/behind-our-redesign-ui3/): keep working content central. These examples are precedents, not a popularity ranking or an endorsement.

Reference B is an artistic target with illustrative content. Every shipped count, offset, payload and state comes from the actual application API.
