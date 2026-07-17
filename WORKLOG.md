# Worklog

## 2026-07-17 — Initial build

- Goal: native Android client for a VS Code Tunnel with foreground keep-alive, graceful reconnect, session-preserving WebView behavior, and HyperOS-friendly setup guidance.
- Repository: public GitHub repository requested by Moyi.
- Default tunnel URL: `https://vscode.dev/tunnel/instance-20260610-18`.
- Security: HTTPS only; no blanket TLS bypass; no credentials committed.
- Implementation owner: Codex child agent after requirements handoff.
- Verification gates: Gradle unit tests/lint/assemble, manifest/security review, CI artifact, real-device screen-off/network-switch tests.
- Rollback: uninstall APK; server-side Code Tunnel service remains independent.
