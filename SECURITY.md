# Security Policy

RuntimeLens analyzes source code and is intended to run in developer workstations and CI systems, so security reports are taken seriously.

## Supported versions

RuntimeLens is currently pre-1.0. Security fixes are applied to the latest published release and the `main` branch.

| Version | Supported |
| --- | --- |
| Latest release | ✅ |
| `main` | ✅ |
| Older pre-1.0 releases | Best effort |

## Reporting a vulnerability privately

**Please do not open a public GitHub issue, discussion, pull request, or social-media post for a suspected security vulnerability.** Public disclosure before a fix is available may put RuntimeLens users at risk.

Use GitHub's private vulnerability reporting flow:

**https://github.com/Shashank022/runtimelens/security/advisories/new**

On GitHub you can also navigate to **Security → Advisories → Report a vulnerability**.

Please include as much of the following as you safely can:

- affected RuntimeLens version or commit;
- operating system and Java version;
- vulnerability type and likely impact;
- minimal reproduction steps or proof of concept;
- whether the issue can expose source code, credentials, files, CI tokens, or execute commands;
- any suggested mitigation or fix;
- whether you believe active exploitation is occurring.

Do **not** include real production credentials, private customer source code, access tokens, or other secrets in the report. Redact sensitive values and provide the smallest reproduction possible.

## What happens after a report

We aim to:

1. acknowledge a valid private report within **3 business days**;
2. perform an initial severity and reproducibility assessment;
3. keep the reporter updated when meaningful progress is made;
4. prepare a fix and tests before public disclosure when feasible;
5. coordinate disclosure and credit with the reporter unless anonymity is requested.

Complex issues may require more time, especially when a fix could introduce false positives or compatibility regressions.

## Security scope

Examples of issues that should be reported privately include:

- arbitrary command execution through crafted source/project input;
- path traversal or writing outside an intended output directory;
- unintended upload or disclosure of analyzed source code;
- unsafe handling of CI credentials, tokens, or secrets;
- SARIF/JSON generation vulnerabilities that can inject executable content downstream;
- dependency or GitHub Action supply-chain compromise affecting RuntimeLens;
- bypasses that cause a security-sensitive RuntimeLens rule to behave dangerously.

Ordinary false positives, false negatives, feature requests, documentation issues, and non-security bugs may be reported through normal GitHub Issues.

## Safe research guidelines

Good-faith security research is welcome. Please avoid:

- accessing data that does not belong to you;
- degrading public services or CI infrastructure;
- social engineering;
- publishing exploit details before a coordinated fix is available;
- testing against third-party systems without authorization.

Thank you for helping keep RuntimeLens and its users safe.
