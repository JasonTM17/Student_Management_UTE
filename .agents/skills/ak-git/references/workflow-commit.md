# Commit Workflow

Use this workflow from the repository root. Preserve every pre-existing staged,
unstaged, and untracked change that is outside the requested delivery scope.

## Tool 1: Inspect + Analyze
```bash
git status --short && \
git diff --check && \
git diff -- <intended-path-1> <intended-path-2> && \
git diff --name-only -- <intended-path-1> <intended-path-2> | awk -F'/' '{
  if ($0 ~ /\.(md|txt)$/) print "docs:"$0
  else if ($0 ~ /test|spec/) print "test:"$0
  else if ($0 ~ /\.(agentkit|codex|claude|cursor|agents)/) print "config:"$0
  else if ($0 ~ /package\.json|lock/) print "deps:"$0
  else print "code:"$0
}'
```

Do **not** use `git add -A`, `git reset`, or a broad checkout to make the
index look clean. If the index already contains unrelated changes, stop and
ask the owner how to separate them. Scan only the explicitly staged delivery
diff for credential-shaped material before committing; if it is suspicious,
block the commit and inspect it without printing credential values.

## Tool 2: Split Decision

NOTE:
- Search for related issues on GitHub and add to body.
- AgentKit adapter/skill updates are behavior changes when they change runtime
  behavior; use `feat`, `fix`, or `perf` as appropriate rather than treating
  them as prose automatically.

**From groups, decide:**

**A) Single commit:** Same type/scope, FILES ≤ 3, LINES ≤ 50

**B) Multi commit:** Mixed types/scopes, group by:
- Group 1: `config:` → `chore(config): ...`
- Group 2: `deps:` → `chore(deps): ...`
- Group 3: `test:` → `test: ...`
- Group 4: `code:` → `feat|fix: ...`
- Group 5: `docs:` → `docs: ...`

## Tool 3: Stage explicitly + Commit

**Single:**
```bash
git add -- <intended-path-1> <intended-path-2>
git diff --cached --check
git commit -m "type(scope): description"
```

**Multi (sequential):**
```bash
git add -- <group-1-path-1> <group-1-path-2>
git diff --cached --check
git commit -m "type(scope): desc"
```
Repeat with the next explicit group. Never remove someone else's staged work.

## Tool 4: Push (if requested)
```bash
git push && echo "✓ pushed: yes" || echo "✓ pushed: no"
```

**Only push if user explicitly requested** ("push", "commit and push").
