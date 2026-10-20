# Security

v0.1.x is the currently maintained release line. Do not put credentials or
customer records in public issues. Use GitHub's private vulnerability reporting
when available, or the contact options in the maintainer's public GitHub profile.

The application binds to `127.0.0.1` by default. All product API handlers require
a bearer token of at least 32 characters; the static UI is public. Tokens live
in browser tab session storage and are never put into a URL. Use a trusted TLS
reverse proxy for remote access and limit API access to trusted operators.

The deployment has one configured source and target topic. Use Kafka ACLs, TLS/SASL and a dedicated service identity for production. `dlqdesk.kafka-properties` accepts Kafka security configuration; keep it in an external protected configuration file. Source payloads and replay bytes are persisted in H2.

The local demo token is a public fixture. Replace it before deployment. The demo
is for synthetic data. There is no multi-user authorization or audit identity in
this release. See [operating limits](docs/usage.md) before exposing a deployment.
