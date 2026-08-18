"""Portable project and runtime paths shared by AgentKit Python skill scripts.

The same skill tree is mirrored below several runtime adapters.  A script must
therefore derive its configuration from the adapter that contains the script
instead of assuming a particular vendor directory such as ``.claude``.
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Iterable


ADAPTER_DIR_NAMES = (".codex", ".claude", ".cursor", ".agents")
PROJECT_MARKERS = (".git", "package.json", "pyproject.toml", "Cargo.toml", "go.mod")


def _ancestors(start: Path) -> Iterable[Path]:
    resolved = start.resolve()
    yield resolved
    yield from resolved.parents


def find_project_root(start: Path | None = None) -> Path:
    """Find the nearest project root without requiring a particular runtime."""
    candidate = (start or Path.cwd()).resolve()
    for directory in _ancestors(candidate):
        if any((directory / marker).exists() for marker in PROJECT_MARKERS):
            return directory
        if any((directory / adapter).is_dir() for adapter in ADAPTER_DIR_NAMES):
            return directory
    return candidate


def project_root_for_skill(skill_dir: Path) -> Path:
    """Prefer a marked working project, then the project containing a skill."""
    working = Path.cwd().resolve()
    for directory in _ancestors(working):
        if any((directory / marker).exists() for marker in PROJECT_MARKERS):
            return directory
        if any((directory / adapter).is_dir() for adapter in ADAPTER_DIR_NAMES):
            return directory
    return find_project_root(skill_dir)


def adapter_directory(skill_dir: Path, project_root: Path | None = None) -> Path | None:
    """Return the adapter that owns ``skill_dir``; otherwise select locally."""
    resolved_skill = skill_dir.resolve()
    root = (project_root or project_root_for_skill(resolved_skill)).resolve()
    requested = os.environ.get("AGENTKIT_ADAPTER", "").strip()
    priorities = tuple(name for name in (requested, *ADAPTER_DIR_NAMES) if name)
    for adapter in priorities:
        if adapter in ADAPTER_DIR_NAMES and (root / adapter).is_dir():
            return root / adapter
    for directory in _ancestors(resolved_skill):
        if directory.name in ADAPTER_DIR_NAMES and directory.is_dir():
            return directory
    return None


def environment_paths(
    skill_dir: Path,
    *,
    project_root: Path | None = None,
    include_user_runtime: bool = True,
) -> list[Path]:
    """Return .env locations from lowest to highest file priority.

    Process environment remains the caller's highest priority.  The user-level
    location is AgentKit-owned rather than a provider-specific config folder.
    """
    skill_dir = skill_dir.resolve()
    root = (project_root or project_root_for_skill(skill_dir)).resolve()
    adapter = adapter_directory(skill_dir, root)
    candidates: list[Path] = []
    if include_user_runtime:
        candidates.append(Path.home() / ".agentkit" / ".env")
    candidates.append(root / ".env")
    if adapter is not None:
        candidates.extend((adapter / ".env", adapter / "skills" / ".env"))
    candidates.append(skill_dir / ".env")

    seen: set[Path] = set()
    ordered: list[Path] = []
    for path in candidates:
        normalized = path.resolve(strict=False)
        if normalized not in seen:
            seen.add(normalized)
            ordered.append(normalized)
    return ordered
