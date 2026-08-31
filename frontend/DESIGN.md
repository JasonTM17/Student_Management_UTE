# CampusCore Frontend Design Contract

The frontend follows the Stitch **Academic Continuity** system. The canonical
tokens and screen atlas live in [`.stitch/DESIGN.md`](./.stitch/DESIGN.md).

The implementation contract is intentionally short: use Be Vietnam Pro with a
blue-first academic palette, 4px spacing rhythm, 4–8px utility radii, white
content surfaces on a cool near-white canvas, low-contrast borders, and clear
focus states. Desktop uses a fixed sidebar and a 1280px content cap; mobile
uses a 16px gutter, stacked content, and bottom navigation for frequent actions.

## Token map (Stitch hex → CSS variables)

Stitch hex is the source. Existing `--portal-*` and shadcn HSL variables are the
implementation. HCMUTE `--portal-yellow` is a named chrome role (ribbon, group
marks, skip-link). Do not delete it or treat yellow as a second brand restyle.
Gold CTA `#C5B358` is allowed only on `/dashboard/register` via `.registration-workspace`
and the `registration` button variant.

| Stitch role | Hex / note | CSS variable |
| --- | --- | --- |
| Surface / background | `#F9F9FF` | `--background`, `--portal-canvas` |
| Surface lowest | `#FFFFFF` | `--card`, `--portal-surface` |
| Primary | `#003F87` | `--primary` |
| Error | `#BA1A1A` | `--destructive` |
| Success | restrained green | `--status-success` ← `--success` |
| Warning | amber | `--status-warning` ← `--accent-warm` |
| Danger | error | `--status-danger` ← `--destructive` |
| Info | primary | `--status-info` ← `--primary` |
| Neutral | muted copy | `--status-neutral` ← `--muted-foreground` |
| HCMUTE yellow chrome | ribbon / groups | `--portal-yellow` |

Each `--status-*` token has a matching `--status-*-foreground`. Shared status
and metric surfaces must use `statusToneClass` / `metricToneClass` from
`src/components/ui/status.ts`. Do not use raw Tailwind palettes
(`bg-emerald-500`, `bg-blue-500`, `bg-violet-500`, `bg-yellow-100`, or other
`bg-*-500` / `text-*-700` status colors) on shared primitives.

When a screen is updated, compare it against the matching Stitch desktop and
mobile references, including loading, empty, error, permission, and long-text
states. Do not claim full Stitch parity from a typecheck alone; use rendered
desktop and 390px viewport evidence where the runtime is available.
