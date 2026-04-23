# Contributing

Contributions are welcome! Bug fixes and small improvements — just open a PR.
For anything larger, open an issue first so we can agree on direction before you invest time writing code.

## Reporting bugs

Please include:
- Library version and Keycloak image tag
- Minimal reproduction (ideally a failing test)
- Full error output / stack trace

## Suggesting features

Open an issue to discuss first — ideas may already be planned or explicitly ruled out.

## Submitting a pull request

1. Fork, create a branch, make your change
2. Run `./mvnw clean verify` (requires Java 17 and Docker)
3. Open the PR against `main`

**A few things that matter:**
- Tests cover the new or changed behaviour
- Fluent API intact — configuration methods must return `SELF`
- Prefer `KC_*` environment variables over CLI args
- README updated for any user-facing change
- One concern per PR

## Developer Certificate of Origin (DCO)

All commits must be signed off to certify that you wrote the code and have the right to contribute it.
Add a `Signed-off-by` trailer to every commit:

```
git commit --signoff -m "your commit message"
```

This adds a line like `Signed-off-by: Your Name <your@email.com>` to the commit, indicating your agreement with the [Developer Certificate of Origin](https://developercertificate.org/).
Pull requests containing unsigned commits will not be merged.

## Use of AI / generative tools

Using AI coding assistants is fine, but comes with two requirements:

1. **Disclose it in the PR** — mention which tool(s) you used in the PR description.
2. **Own the result** — you must understand every change well enough to explain and maintain it yourself. AI-generated code that you cannot reason about will not be merged, regardless of whether it passes tests.
3. **License compliance** — you are responsible for ensuring that AI-generated code does not introduce license-incompatible content. This project is licensed under the Apache License 2.0; all contributions must be compatible.

## Commit messages

Use the imperative mood and explain the *why* in the body, not just the *what*.

## A note on project direction

This library moves forward only — no LTS branches, no backports. Make sure to stay up to date.
