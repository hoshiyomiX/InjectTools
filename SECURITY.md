# Security Policy

## Supported Versions

We actively support the following versions of InjectTools with security updates:

| Version | Supported          |
| ------- | ------------------ |
| 1.2.x   | :white_check_mark: |
| 1.1.x   | :white_check_mark: |
| < 1.1   | :x:                |

## Reporting a Vulnerability

We take the security of InjectTools seriously. If you have discovered a security vulnerability, we appreciate your help in disclosing it to us in a responsible manner.

### How to Report

**Please do not report security vulnerabilities through public GitHub issues.**

Instead, please report them via:

1. **GitHub Security Advisory (Preferred)**
   - Go to the [Security Advisories](https://github.com/hoshiyomiX/InjectTools/security/advisories) page
   - Click "Report a vulnerability"
   - Fill in the details

2. **Email**
   - Send an email to: security@hoshiyomi.dev (if available)
   - Or contact via Telegram: [@hoshiyomi_id](https://t.me/hoshiyomi_id)

### What to Include

Please include the following information:

- **Type of vulnerability** (e.g., buffer overflow, SQL injection, XSS, etc.)
- **Full path of the affected file(s)** with line numbers
- **Steps to reproduce** the issue
- **Proof-of-concept or exploit code** (if possible)
- **Impact** of the vulnerability
- **Suggested fix** (if you have one)

### Response Timeline

| Stage | Timeline |
|-------|----------|
| Initial Response | Within 48 hours |
| Vulnerability Confirmation | Within 7 days |
| Fix Development | Depends on severity |
| Security Release | Within 14 days of fix |

### Disclosure Policy

- We follow **Coordinated Vulnerability Disclosure (CVD)**
- We will keep you informed of our progress
- We will credit you in the security advisory (unless you prefer to remain anonymous)
- Please do not disclose the vulnerability publicly until we have released a fix

## Security Best Practices

When using InjectTools, please follow these security recommendations:

### For Users

1. **Only download APKs from official sources**
   - GitHub Releases (official)
   - Avoid third-party APK sites

2. **Verify APK signature** (if you know how)
   ```bash
   apksigner verify --print-certs InjectTools-x.x.x.apk
   ```

3. **Keep the app updated**
   - Always use the latest version
   - Enable auto-update if available

4. **Use responsibly**
   - Only scan domains you own or have permission to test
   - Follow your local laws and regulations

### For Developers

1. **Never commit sensitive data**
   - API keys
   - Keystore files
   - Debug logs with sensitive info

2. **Use ProGuard/R8**
   - Keep it enabled in release builds
   - Review obfuscation rules

3. **Validate all inputs**
   - User inputs
   - Network responses
   - File contents

4. **Keep dependencies updated**
   - Check for security advisories
   - Use Dependabot for automated updates

## Security Features

InjectTools includes the following security features:

- **Network validation** - Detects VPN interception and fake DNS
- **Certificate transparency** - Uses crt.sh for subdomain discovery
- **Local storage encryption** - Sensitive data stored securely
- **No telemetry** - We don't collect any user data
- **Open source** - Code is auditable by anyone

## Known Security Considerations

1. **SSL/TLS** - The app connects to external servers (crt.sh) over HTTPS
2. **DNS Resolution** - Uses system DNS for domain lookups
3. **Target Host** - User-specified target host is stored locally

## Contact

For any security concerns, reach out to:
- Telegram: [@hoshiyomi_id](https://t.me/hoshiyomi_id)
- GitHub: [@hoshiyomiX](https://github.com/hoshiyomiX)

---

Thank you for helping keep InjectTools and our users safe! 🛡️
