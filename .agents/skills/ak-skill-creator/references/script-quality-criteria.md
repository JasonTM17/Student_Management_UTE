# Script Quality Criteria

Scripts provide deterministic reliability and token efficiency.

## When to Include Scripts

- Same code rewritten repeatedly
- Deterministic operations needed
- Complex transformations
- External tool integrations

## Cross-Platform Requirements

**Prefer:** Node.js or Python
**Avoid:** Bash scripts (not well-supported on Windows)

If bash required, provide Node.js/Python alternative.

## Testing Requirements

**Mandatory:** All scripts must have tests

```bash
# Run tests before packaging
python -m pytest scripts/tests/
# or
npm test
```

Tests must pass. No skipping failed tests.

## Environment Variables

Respect the portable AgentKit hierarchy (highest priority first):

1. `process.env` (runtime)
2. `<skill-root>/.env` (skill-specific)
3. `<project>/<adapter>/skills/.env`
4. `<project>/<adapter>/.env`
5. `<project>/.env`
6. `~/.agentkit/.env` (user runtime)

`<adapter>` is whichever project adapter contains the skill, such as `.codex`,
`.claude`, `.cursor`, or `.agents`. Do not hard-code a provider directory.

**Implementation pattern (Python):**

```python
from dotenv import load_dotenv
from runtime_paths import environment_paths

# environment_paths() returns lowest-to-highest file priority.  Do not override
# an already-set value: process.env stays highest priority.
for env_path in environment_paths(skill_dir):
    load_dotenv(env_path, override=False)
```

## Documentation Requirements

### .env.example
Show required variables without values:

```
API_KEY=
DATABASE_URL=
DEBUG=false
```

### requirements.txt (Python)
Pin major versions:

```
requests>=2.28.0
python-dotenv>=1.0.0
```

### package.json (Node.js)
Include scripts:

```json
{
  "scripts": {
    "test": "jest"
  }
}
```

## Manual Testing

Before packaging, test with real use cases:

```bash
# Example: PDF rotation script
python scripts/rotate_pdf.py input.pdf 90 output.pdf
```

Verify output matches expectations.

## Error Handling

- Clear error messages
- Graceful failures
- No silent errors
- Exit codes: 0 success, non-zero failure
