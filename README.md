<p align="center"><img src="docs/assets/cover.svg" alt="DLQDesk: inspect, fix and replay failed Kafka messages" width="100%" /></p>

**Inspect failed Kafka messages. Fix the JSON and send them again.**

Java 21 / 25 · Spring Boot · React · TypeScript · Self-hosted · MIT

[![Build and verify](https://github.com/lMysticl/dlqdesk/actions/workflows/ci.yml/badge.svg)](https://github.com/lMysticl/dlqdesk/actions/workflows/ci.yml)

[Download](https://github.com/lMysticl/dlqdesk/releases/latest) · [User guide](docs/usage.md) · [Architecture](docs/architecture.md)

![DLQDesk showing failed Kafka messages, a JSON editor and delivery history](docs/assets/workspace.png)

_Real application capture using the included sample messages._

## What it does

- Shows messages from a Kafka dead-letter topic: the place failed messages are sent.
- Lets you edit JSON and review exactly what will be sent.
- Replays selected messages at a chosen rate and keeps a delivery history.

Inspection and replay leave the original messages and production consumer offsets unchanged.

## Try the demo

Requires **Java 21 or 25, Docker and Git**.

```sh
git clone https://github.com/lMysticl/dlqdesk.git
cd dlqdesk
```

Download [dlqdesk-0.2.0.jar](https://github.com/lMysticl/dlqdesk/releases/download/v0.2.0/dlqdesk-0.2.0.jar) into the `dlqdesk` folder, then run:

```sh
docker compose up -d --wait
java -jar dlqdesk-0.2.0.jar --spring.profiles.active=demo --dlqdesk.bootstrap-servers=localhost:59092
```

1. Open [localhost:8092](http://localhost:8092/).
2. Use **Use the bundled local demo token**, connect and choose **Add 12 demo records**.
3. Inspect a message, fix its JSON if needed, select it and choose **Create preview**.
4. Review the preview, then choose **Start replay**. Check the delivery history.

Stop the demo broker with `docker compose down`. Local history is saved in `./data/`; the bundled demo token is public.

## Use your own topics

Follow the [deployment guide](docs/usage.md#deployment) to configure your token, broker and topics. Run without the `demo` profile.

v0.2.0 supports one application instance, one source/target pair and up to 100 JSON messages per replay. **SENT means Kafka accepted the message, not that the receiving application processed it.** Uncertain delivery needs review; replay can produce duplicates. See [delivery semantics](docs/usage.md#delivery-semantics-and-recovery) and [security](SECURITY.md).

## For developers

[Build and test](CONTRIBUTING.md) · [Test coverage](docs/validation.md) · [Compatibility](docs/compatibility.md) · [Engineering brief](docs/project-brief.md) · [Design](docs/design.md) · [Technical article](docs/article.md)

Built by [Pavel Putrenkov](https://github.com/lMysticl). [MIT license](LICENSE).
