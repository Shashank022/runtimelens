# GitHub security setup for RuntimeLens

The repository contains security configuration in source control, but several GitHub security controls are account/repository settings and must be enabled after the repository exists.

## 1. Make vulnerability reporting private

RuntimeLens includes `SECURITY.md` and points security researchers to GitHub private vulnerability reporting.

After publishing the repository:

1. Open **Settings**.
2. Open **Security and quality / Advanced Security** (GitHub labels can vary slightly).
3. Enable **Private vulnerability reporting** when available.
4. Verify the repository **Security** area offers **Report a vulnerability**.

Security issues should not be opened as public Issues.

## 2. Dependabot

The repository contains `.github/dependabot.yml` for:

- Maven/build dependencies;
- GitHub Actions dependencies.

It checks weekly and groups related update PRs.

Also verify the GitHub repository settings have these enabled:

- Dependency graph;
- Dependabot alerts;
- Dependabot security updates;
- Dependabot version updates.

The checked-in `dependabot.yml` controls version-update behavior; GitHub-side alerts/security-update settings should still be verified.

## 3. Secret protection

Enable available secret-protection features:

- Secret scanning;
- Push protection;
- generic secret detection when available for the repository/plan.

No production credentials, API keys, tokens, signing keys, or private test data should be committed to RuntimeLens.

## 4. OpenSSF Scorecard

`.github/workflows/scorecard.yml` runs OpenSSF Scorecard and publishes results for the README badge.

The workflow intentionally:

- uses read-only permissions by default;
- grants only the required job-level write permissions;
- pins third-party actions to immutable commit SHAs;
- sets `persist-credentials: false` during checkout;
- uploads Scorecard SARIF to GitHub code scanning.

After the first successful run, verify the OpenSSF badge at the top of the README links to the RuntimeLens scorecard.

## 5. Protect `main`

Create a Repository Rules rule/ruleset for `main` and consider requiring:

- pull requests before merge;
- at least one approving review once the project has multiple maintainers;
- successful CI checks;
- conversation resolution;
- prevention of force pushes;
- prevention of branch deletion.

As the project grows, consider requiring signed commits/tags and release provenance.

## 6. Workflow permissions

Keep default GitHub Actions permissions minimal. RuntimeLens workflows specify permissions explicitly.

Do not add `write-all`. A job should receive only the write permission it actually requires.

Release publishing is the exception: the release job receives `contents: write` because it must create a GitHub Release.

## 7. Dependency policy

RuntimeLens v0.1 intentionally has no required third-party runtime dependencies. New dependencies should be added only when they materially improve analysis quality and should be reviewed for:

- maintenance activity;
- license compatibility;
- known vulnerabilities;
- transitive-dependency footprint;
- release provenance;
- whether the functionality belongs in the core runtime or an optional module.

## 8. Verify after publishing

A healthy repository should show:

- CI passing;
- OpenSSF Scorecard workflow running successfully;
- Dependabot configured;
- no committed generated JAR/class files;
- `SECURITY.md` visible under GitHub security policy;
- private vulnerability reporting available;
- secret scanning/push protection enabled where available.
