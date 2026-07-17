# Security Policy

## Supported versions

Security fixes are provided for the latest published LiteChat release.

## Reporting a vulnerability

Do not open a public issue for a vulnerability involving API-key handling, backup exposure, attachment access, request routing or provider impersonation. Use GitHub's private vulnerability reporting for this repository. Include affected versions, reproduction steps, impact and any suggested mitigation.

Do not include live API keys or private conversation data. A maintainer should acknowledge a complete report within seven days and coordinate disclosure after a fix is available.

LiteChat sends requests directly to user-configured providers. Reports about a provider's own service, billing, model output or availability should be sent to that provider unless LiteChat changes the request or exposes local data incorrectly.
