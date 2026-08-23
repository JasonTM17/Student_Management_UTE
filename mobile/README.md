# CampusCore Mobile

Expo + React Native + TypeScript mobile client for the Java REST API course
project.

## Design contract

The UI follows the canonical `Academic Continuity` evidence in
`frontend/.stitch/DESIGN.md`:

- Be Vietnam Pro is bundled with Expo and loaded before the navigator renders;
  the platform system font remains the runtime fallback for native text that
  cannot use the bundled family.
- The canvas is `#F9F9FF`, cards are white, and primary actions use `#003F87`
  with `#0056B3` for filled emphasis.
- Spacing uses a 4px baseline, with a 16px mobile gutter and 8px card radius.
- Interactive controls use a minimum 44px touch target.
- High-frequency mobile navigation is a role-specific bottom bar; the menu
  panel exposes only routes that the active preview role may access. Navigator
  policy also rejects a direct transition to an unauthorized route.

## Run

```powershell
npm ci
npx expo start
```

The dependency-free screen-atlas regression can run now:

```powershell
npm test --prefix mobile
```

It verifies 21 registered screens and the shared Stitch token anchors. Run
`npm run typecheck --prefix mobile` from the repository root for the complete
TypeScript gate.

The default mode is `live`: API calls target the Java REST API at
`EXPO_PUBLIC_API_URL`. Preview mode remains an explicit local-only option for
design inspection and is never treated as authenticated evidence.
The app uses one base URL for all requests and does not embed credentials. The
local preview entry point is deliberately labeled as a preview; it does not
represent a successful Java authentication. When live mode is selected, sign
in calls `/auth/login`, stores the returned bearer access token in the in-memory
API client together with the body refresh token, and enters the role-specific
home screen only after the Java auth candidate returns a token. Refresh and
logout requests send the in-memory refresh token in the request body, matching
the Java mobile-token contract; an explicit refresh call stores the rotated
access and refresh tokens returned by Java. A live request that receives `401`
will attempt one Java refresh-token rotation and retry once, excluding
login/refresh/logout to avoid auth loops. The navigator keeps `signedOut`,
`preview`, and `authenticated` as distinct session states; role switching is
available only inside the explicit preview state and is never an authorization
substitute.

For live API access, Android Emulator uses `http://10.0.2.2:4010/api/v1`.
iOS Simulator and Expo web can use `http://127.0.0.1:4010/api/v1`. A physical
device must set `EXPO_PUBLIC_API_URL` to the development machine's LAN address,
and the host firewall must allow that development port.

## API seam

`src/api/client.ts` targets the Java REST API at `/api/v1`. It exposes generic
GET/POST/PATCH/PUT/DELETE helpers plus named probes for auth, identity, health,
notifications, thesis topics, and the assistant route. The assistant seam sends
`{ message, locale }` and expects the Java/web-compatible
`{ answer, model, degraded, reasonCode, citations }` shape. The assistant
screen renders provenance in live mode and only uses local responses in the
explicit preview state. Retained role routes must show an explicit
not-available state when their Java contract is not yet present.

## Screen atlas

The registry contains 21 navigable screens:

- Auth: sign in.
- Student: dashboard, schedule, courses, grades, attendance, registration,
  notifications, profile.
- Thesis: topics, topic detail, registration, progress.
- Assistant: academic assistant/chatbot.
- Staff: admin dashboard, students, lecturers; lecturer dashboard,
  schedule, grading, attendance.

This is a scaffold, not a claim of live Expo, device, API, or visual validation.
