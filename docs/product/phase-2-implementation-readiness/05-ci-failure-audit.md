# CI Failure Audit: Frontend PostCSS Build Failure

## Scope

This is a docs-only audit of the current CI failure observed on docs-only PRs. It does not change CI, frontend dependencies, lockfiles, or build configuration.

Inspected files:

- `.github/workflows/ci-cd.yml`
- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/postcss.config.js`
- `frontend/vite.config.ts`
- `frontend/tailwind.config.js`

## Observed Failure

Docs-only PRs fail the `Build & Test` job during `npm run build` because Vite/PostCSS attempts to load the PostCSS plugin declared in `frontend/postcss.config.js`:

```js
export default {
  plugins: {
    '@tailwindcss/postcss': {}
  }
}
```

The reported error is:

```text
Cannot find module '@tailwindcss/postcss'
Require stack:
frontend/postcss.config.js
```

## Exact Likely Root Cause

The frontend PostCSS config references `@tailwindcss/postcss`, but that package is not installed by the frontend dependency graph.

`frontend/package.json` includes:

- `tailwindcss` in `devDependencies`
- `postcss` in `devDependencies`
- `autoprefixer` in `devDependencies`

It does not include:

- `@tailwindcss/postcss`

`frontend/package-lock.json` mirrors this state: the root package dev dependencies include `tailwindcss`, `postcss`, and `autoprefixer`, but not `@tailwindcss/postcss`. Because CI uses `npm ci`, it installs exactly what the lockfile describes. After that clean install, `npm run build` loads `frontend/postcss.config.js`, sees `@tailwindcss/postcss`, and fails because the module is absent from `node_modules`.

This is most likely a Tailwind v4 configuration/dependency mismatch: the project has `tailwindcss` v4 configured via the separate `@tailwindcss/postcss` PostCSS adapter, but only `tailwindcss` itself is listed as a dev dependency.

## Dependency vs Install Step

The missing module is a dependency declaration problem, not an install-command problem.

The CI install step is:

```yaml
- name: Frontend install & build
  working-directory: frontend
  run: |
    npm ci
    npm run build
```

`npm ci` is appropriate for CI and correctly uses `frontend/package-lock.json`. The failure is expected because neither `frontend/package.json` nor `frontend/package-lock.json` declares `@tailwindcss/postcss`. Changing the install command alone would not safely fix this unless it also changed dependency resolution behavior, which would reduce CI reproducibility.

## Why Docs-Only PRs Still Run Frontend Build

`.github/workflows/ci-cd.yml` runs on every pull request targeting `main`:

```yaml
on:
  pull_request:
    branches: [main]
```

The `test` job is named `Build & Test` and has no path filter, job-level condition, or step-level condition that excludes docs-only changes. Its comment explicitly says it runs on every push and PR.

As a result, all PRs, including docs-only PRs, execute:

1. Maven package
2. Node setup
3. `npm ci`
4. `npm run build`
5. frontend artifact upload

The later Docker build/push and deploy jobs are restricted to `main` branch pushes, but the frontend build is part of the always-on PR validation job.

## Minimal Safe Fix Options

### Option A: Add the Missing PostCSS Adapter Dependency

Add `@tailwindcss/postcss` to `frontend/devDependencies` and update `frontend/package-lock.json` with the matching lockfile entry.

Expected effect:

- Keeps the current Tailwind v4-style `frontend/postcss.config.js` intact.
- Preserves the current CI flow and `npm ci` behavior.
- Makes `npm run build` able to resolve the configured PostCSS plugin.

Risk:

- Low, because it aligns dependencies with the existing config.
- Requires a lockfile update generated with the same package manager workflow.
- Could expose unrelated frontend build/type-check issues after PostCSS resolution succeeds.

### Option B: Change PostCSS Config to Match Installed Packages

Change `frontend/postcss.config.js` to use only installed plugins, such as `tailwindcss` and/or `autoprefixer`, if that is compatible with the Tailwind version in use.

Expected effect:

- Avoids adding a new package.
- Makes the config reflect currently declared dependencies.

Risk:

- Medium to high for Tailwind v4, because Tailwind v4 expects the PostCSS integration to use `@tailwindcss/postcss` rather than the older Tailwind v3 PostCSS plugin shape.
- May trade the current missing-module error for a Tailwind integration/configuration error.
- Could silently change CSS generation behavior if downgraded to an older configuration pattern.

### Option C: Downgrade or Pin Tailwind to a v3-Compatible Setup

Adjust Tailwind-related dependencies and PostCSS config to a known Tailwind v3 pattern.

Expected effect:

- Restores compatibility with older `tailwindcss` PostCSS plugin usage.
- May be attractive if the app intentionally does not need Tailwind v4.

Risk:

- Medium, because it changes the frontend styling toolchain rather than just adding the missing adapter.
- Requires coordinated `package.json`, lockfile, and config changes.
- May conflict with any current or planned Tailwind v4 usage.

### Option D: Add CI Path Filtering for Docs-Only PRs

Modify the workflow so docs-only PRs do not run the frontend build.

Expected effect:

- Prevents unrelated frontend failures from blocking docs-only PRs.
- Reduces CI time for documentation changes.

Risk:

- Medium, because it changes CI coverage semantics.
- Can mask repository-wide breakage on docs-only PRs, leaving `main` or non-docs PRs to detect the frontend issue later.
- Does not fix the underlying frontend dependency/config mismatch.

### Option E: Split Docs Validation from Full Build Validation

Add a separate docs-only workflow or conditional job while preserving full build validation for code-affecting changes.

Expected effect:

- Gives docs-only PRs a targeted green path.
- Keeps full frontend/backend build validation where it is most relevant.

Risk:

- Medium, because workflow branching can become harder to reason about.
- Requires careful path patterns to avoid accidentally skipping build validation for mixed docs/code PRs.
- Still leaves the current frontend build failure to be fixed separately.

## Recommended Next PR

The next PR to make CI green should use Option A:

1. Add `@tailwindcss/postcss` to `frontend/devDependencies`.
2. Regenerate/update `frontend/package-lock.json` with the package manager already used by the project.
3. Run the existing frontend build path locally or in CI: `npm ci` followed by `npm run build` from `frontend`.
4. Keep `.github/workflows/ci-cd.yml` unchanged for this fix PR unless the team separately decides to optimize docs-only CI behavior.

This is the smallest safe fix because it addresses the direct root cause without changing CI semantics or rewriting the frontend styling toolchain. CI path filtering for docs-only PRs can be considered in a separate readiness/optimization PR after the build is green.

