# Distribution packet — dlqdesk

Prepared 20 October 2026. These are drafts. Repository docs are published with the code; no external post, comment or message has been sent.

## First destination: a concrete problem

https://www.reddit.com/r/apachekafka/comments/1q1hg5l/how_do_you_handle_dlq_fix_replay/

The source was inspected. The thread asks how people repair and replay dead letters and includes manually built workflows. It is a January 2026 thread, so relevance does not establish that a new comment is welcome now. The public community-rules page did not expose its rules; check current rules and the live thread before posting.

Candidate reply:

> Disclosure: I maintain DLQDesk, a small MIT-licensed Java/React recovery tool. The workflow is inspect a Kafka dead letter, repair JSON, freeze a fingerprint-checked preview and explicitly publish to an allowlisted target. A persistent ledger separates broker-acknowledged entries from pending and uncertain ones; resume does not resend confirmed entries. It preserves keys/headers and does not move consumer-group offsets. It is single-instance and JSON-focused, not a general broker console. The runnable demo and limitations are at https://github.com/lMysticl/dlqdesk. I'd be interested in a concrete repair/replay case it cannot express.

## Technical article

[The complete article](article.md) includes a runnable example, actual screenshots, implementation boundaries and the precise tested versions. Publish it to an author-owned blog or DEV after author review. DEV's [code of conduct](https://dev.to/code-of-conduct) requires disclosure of AI assistance; the draft includes it. Keep the GitHub article as the source version and use a canonical URL if the chosen publication supports it.

## Show HN candidate

Title: **Show HN: DLQDesk – Review and replay Kafka dead letters with a durable ledger**

Link: https://github.com/lMysticl/dlqdesk

Opening comment: Disclosure: I maintain DLQDesk, a small MIT-licensed Java/React recovery tool. The workflow is inspect a Kafka dead letter, repair JSON, freeze a fingerprint-checked preview and explicitly publish to an allowlisted target. A persistent ledger separates broker-acknowledged entries from pending and uncertain ones; resume does not resend confirmed entries. It preserves keys/headers and does not move consumer-group offsets. It is single-instance and JSON-focused, not a general broker console. The runnable demo and limitations are at https://github.com/lMysticl/dlqdesk. I'd be interested in a concrete repair/replay case it cannot express.

[Show HN guidance](https://news.ycombinator.com/showhn.html) calls for something people can try and an author available to discuss it. Link the runnable repository/release, not only an article. Do not request votes or claim adoption that has not been observed. Publish each tool when the author can respond to technical questions, rather than posting both repeatedly.

## Professional portfolio

Use the existing [engineering brief](project-brief.md), the release and a real workspace screenshot in an author-owned professional profile. Keep Java 21/25, Spring Boot, React, TypeScript, transactional recovery or Kafka acknowledgement handling attached to evidence. No ATS ranking, customer adoption or commercial support claim is established by this work.

## Concrete publication handoff

Ready inputs: article, screenshot, release download, exact CI, ownership disclosure and tailored reply above. Before sending anything outside GitHub delivery, select the exact destination and author account and approve the final text. Credentials and external messages are outside the current Git delivery authority.

## Measure actual attention

Use the repository's stars/forks, release-asset download counts and GitHub traffic views/clones where owner access permits. Record the observation date and platform link. Compare actual periods before and after each publication; a download count is not a unique user or successful installation. No growth estimate or guaranteed star target is claimed.
