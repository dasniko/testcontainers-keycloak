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

## Commit messages

Use the imperative mood and explain the *why* in the body, not just the *what*.

## A note on project direction

This library moves forward only — no LTS branches, no backports. Make sure to stay up to date.
