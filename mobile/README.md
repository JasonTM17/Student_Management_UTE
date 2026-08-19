# CampusCore Mobile

Lightweight Expo + React Native + TypeScript scaffold for the CampusCore mobile
surface. The implementation keeps navigation local and dependency-free so the
screen atlas can be explored before the mobile runtime is connected to every
domain contract.

## Design contract

The UI follows the canonical `Academic Continuity` evidence in
`frontend/.stitch/DESIGN.md`:

- Be Vietnam Pro is the preferred typeface; the platform system font remains the
  runtime fallback when the font is not bundled.
- The canvas is `#F9F9FF`, cards are white, and primary actions use `#003F87`
  with `#0056B3` for filled emphasis.
- Spacing uses a 4px baseline, with a 16px mobile gutter and 8px card radius.
- Interactive controls use a minimum 44px touch target.
- High-frequency mobile navigation is a bottom bar; the menu panel exposes the
  complete screen registry.

## Run later

Dependencies are intentionally not installed in this worker checkout. When a
runtime owner is ready to exercise the scaffold:

```powershell
npm install
npx expo start
```

The dependency-free screen-atlas regression can run now:

```powershell
npm test --prefix mobile
```

It verifies 23 registered screens and the shared Stitch token anchors. The
real `npm run typecheck` remains deferred until Expo/React Native dependencies
are provisioned in a bounded environment; source transpile has been checked,
but transpile is not a substitute for Expo typechecking.

Set `EXPO_PUBLIC_API_URL` from `.env.example` when the Java REST API is not
reachable at the local default. The app uses one base URL for all requests and
does not embed credentials.

## API seam

`src/api/client.ts` targets the Java REST API at `/api/v1`. It exposes generic
GET/POST/PATCH/PUT/DELETE helpers plus named probes for auth, identity, health,
notifications, thesis topics, and the assistant route. The current Java
RESTful API shell only publishes a subset of those contracts; the remaining
screen families intentionally use representative local data until their public
routes are cut over.

## Screen atlas

The registry contains 23 navigable screens:

- Auth: sign in.
- Student: dashboard, schedule, courses, grades, attendance, registration,
  invoices, notifications, profile.
- Thesis: topics, topic detail, registration, progress, evaluation.
- Assistant: academic assistant/chatbot.
- Operations: admin dashboard, students, lecturers; lecturer dashboard,
  schedule, grading, attendance.

This is a scaffold, not a claim of live Expo, device, API, or visual validation.
