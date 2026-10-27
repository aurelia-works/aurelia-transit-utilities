Aurelia Transit Utilities (ATU): client-side quality-of-life tools for Minecraft Transit Railway 4.0.5 on Fabric 1.20.1.

## Verification (before saying a task is done)
Run the checks below and paste their result lines (the build result, test counts, failures; not whole logs) in the final reply. A task is not done until they pass or each failure is named with its cause.
- Build: `./gradlew build --offline` (compile and jar)
- Test: `./gradlew test --offline` (pure-logic unit tests; runSelftest boots the game, so ask Jah first)
- Lint: none set up
- UI: yes (dashboard and tool screens in game). Look at an in-game screenshot only when Jah asks you to run `./gradlew runClient --offline`; otherwise say not seen.

## Review and PRs
- Review rules: `REVIEW.md`. PR description: `.github/pull_request_template.md`.
- Before opening a PR, run `/code-review` on the branch diff. At most 3 review rounds; after that, re-scope and review only the diff added since the last round.

## Checks and CI
- Do not run repo-wide checks. Locally run plain `./gradlew test --offline` (the quiet-gradle hook narrows it to changed classes). CI (`.github/workflows/ci.yml`) runs the full `./gradlew build test` on every PR and push to main.
- Don't run `runClient` or `runSelftest` unless Jah asks: they boot the game.
- One request = one branch = one PR titled `type(scope): what now works` (skill `ship`). Open the PR only when Jah asks.
