#!/bin/sh
# Fast checks run by the global Stop hook (~/.agents/hooks/verify-stop.sh) when an agent finishes: compile or typecheck
# only for the kinds of files that changed. Tests stay with the agent (CLAUDE.md "Verification") and CI.
CHANGED=$( (git diff HEAD --name-only; git ls-files -o --exclude-standard) 2>/dev/null)
has() { printf "%s\n" "$CHANGED" | grep -E -q "$1"; }
run() { OUT=$("$@" 2>&1) || { printf "%s\n" "$OUT" | grep -v "^Note:" | tail -30; exit 1; }; }
changed() { printf "%s\n" "$CHANGED" | grep -E "$1" | while IFS= read -r f; do [ -f "$f" ] && printf "%s\n" "$f"; done; }
has '\.java$' && run ./gradlew compileJava compileTestJava --offline -q
exit 0
