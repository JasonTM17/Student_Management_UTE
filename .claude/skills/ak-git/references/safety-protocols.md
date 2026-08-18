# Git Safety Protocols

## Secret Detection Patterns

### Scan Command
```bash
git diff --cached | grep -iE "(AKIA|api[_-]?key|token|password|secret|credential|private[_-]?key|mongodb://|postgres://|mysql://|redis://|-----BEGIN)"
```

### Patterns to Detect

| Category | Pattern | Example |
|----------|---------|---------|
| API Keys | `api[_-]?key`, `apiKey` | `API_KEY=abc123` |
| AWS | `AKIA[0-9A-Z]{16}` | `AKIAIOSFODNN7EXAMPLE` |
| Tokens | `token`, `auth_token`, `jwt` | `AUTH_TOKEN=xyz` |
| Passwords | `password`, `passwd`, `pwd` | `DB_PASSWORD=secret` |
| Private Keys | `-----BEGIN PRIVATE KEY-----` | PEM files |
| DB URLs | `mongodb://`, `postgres://`, `mysql://` | Connection strings |
| OAuth | `client_secret`, `oauth_token` | `CLIENT_SECRET=abc` |

### Files to Warn About
- `.env`, `.env.*` (except `.env.example`)
- `*.key`, `*.pem`, `*.p12`
- `credentials.json`, `secrets.json`
- `config/private.*`

### Action on Detection
1. **BLOCK commit immediately**
2. Do not print matching lines or credential values in the agent transcript.
3. Suggest: "Add to .gitignore or use environment variables"
4. If the owner explicitly asks to separate it, use the reversible
   `git restore --staged -- <file>` operation for that exact file.

## Branch Protection

### Never Force Push To
- `main`, `master`, `production`, `prod`, `release/*`

### Pre-Merge Checks
```bash
# Check for conflicts before merge
git merge --no-commit --no-ff origin/{branch} && git merge --abort
```

### Remote-First Operations
Always use `origin/{branch}` for comparisons:
- ✅ `git diff origin/main...origin/feature`
- ❌ `git diff main...HEAD` (includes local uncommitted)

## Error Recovery

### Undo Last Commit (unpushed; explicit owner approval required)
```bash
git reset --soft HEAD~1  # Keep changes staged
```

Prefer a new corrective commit. Never use `git reset --hard` or
`git checkout --` in AgentKit workflows; they can destroy the owner's work.
If a published commit must be reversed, use `git revert` and preserve the
audit trail.

### Abort Merge
```bash
git merge --abort
```

### Preserve or quarantine local changes
```bash
git stash push -u -m "agentkit-temporary-preservation"
```

Stashing is optional and must target a clearly named, recoverable checkpoint.
Do not delete, clean, or overwrite local changes without explicit user
authorization and an exact target check.
