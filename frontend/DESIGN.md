# CampusCore Frontend Design Contract

The frontend follows the Stitch **Academic Continuity** system. The canonical
tokens and screen atlas live in [`.stitch/DESIGN.md`](./.stitch/DESIGN.md).

The implementation contract is intentionally short: use Be Vietnam Pro with a
blue-first academic palette, 4px spacing rhythm, 4–8px utility radii, white
content surfaces on a cool near-white canvas, low-contrast borders, and clear
focus states. Desktop uses a fixed sidebar and a 1280px content cap; mobile
uses a 16px gutter, stacked content, and bottom navigation for frequent actions.

When a screen is updated, compare it against the matching Stitch desktop and
mobile references, including loading, empty, error, permission, and long-text
states. Do not claim full Stitch parity from a typecheck alone; use rendered
desktop and 390px viewport evidence where the runtime is available.
