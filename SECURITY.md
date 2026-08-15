# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 0.1.x   | :white_check_mark: |

## Security & Privacy Architecture

NotifPlus is designed with a strict **Zero-Trust Local-Only Architecture**:
- **Zero Internet Access**: The application does NOT declare or use `android.permission.INTERNET`.
- **Sandbox Storage**: All notification payloads, attachments, and preferences are stored strictly in private internal storage (`filesDir`, `noBackup="true"`).
- **Anti-Leakage**: Sensitive message payloads and notification data are never forwarded to log output (`Logcat`).
- **Biometric Protection**: App access can be protected using device biometric authentication (AndroidX Biometric).

## Reporting a Vulnerability

If you discover a potential security vulnerability within NotifPlus, please report it responsibly:

1. **Do NOT** open a public GitHub issue for security-critical findings.
2. Submit your report directly via GitHub Private Vulnerability Reporting or contact the maintainer: **Adrian Syah Abidin**.
3. Include details of the vulnerability, reproduction steps, and potential impact.

We take security and privacy seriously and will respond promptly to validate and address reported issues.
