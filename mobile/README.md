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
- High-frequency mobile navigation is a role-specific bottom bar; the menu
  panel exposes only routes that the active preview role may access. Navigator
  policy also rejects a direct transition to an unauthorized route.

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

The default mode is `preview`: API calls fail closed with
`MOBILE_API_PREVIEW`, so the scaffold cannot accidentally target the
incomplete Java candidate. To exercise a deliberately provisioned runtime,
set `EXPO_PUBLIC_API_MODE=live` and `EXPO_PUBLIC_API_URL` from `.env.example`.
The app uses one base URL for all requests and does not embed credentials.
The local preview entry point is deliberately labeled as a preview; it does not
represent a successful Java authentication. When live mode is selected, sign
in stays disabled until the Java auth contract exists and has runtime evidence.
The navigator keeps `signedOut`, `preview`, and `authenticated` as distinct
session states; role switching is available only inside the explicit preview
state and is never an authorization substitute.

## API seam

`src/api/client.ts` targets the Java REST API at `/api/v1`. It exposes generic
GET/POST/PATCH/PUT/DELETE helpers plus named probes for auth, identity, health,
notifications, thesis topics, and the assistant route. The assistant seam sends
`{ message, locale }` and expects the Java/web-compatible
`{ answer, model, degraded }` shape. The current Java RESTful API shell only
publishes a subset of those contracts; the remaining screen families
intentionally use representative local data until their public routes are cut
over.

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
