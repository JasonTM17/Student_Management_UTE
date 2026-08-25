import type { Locale } from '@/i18n/config';

export const en = {
  common: {
    locale: {
      label: 'Language',
      english: 'English',
      vietnamese: 'Vietnamese',
      switchToEnglish: 'Switch to English',
      switchToVietnamese: 'Chuyển sang tiếng Việt',
    },
    actions: {
      signIn: 'Sign in',
      signOut: 'Sign out',
      save: 'Save',
      saveChanges: 'Save changes',
      cancel: 'Cancel',
      confirm: 'Confirm',
      retry: 'Try again',
      refresh: 'Refresh data',
      openSchedule: 'Open schedule',
      openTool: 'Open tool',
      openView: 'Open view',
      openWorkspace: 'Open workspace',
      returnHome: 'Return to the homepage',
      openDashboard: 'Open dashboard',
      continueToWorkspace: 'Continue to workspace',
      signInToWorkspace: 'Sign in to workspace',
      reviewAdmin: 'Review admin surfaces',
      browseSections: 'Browse sections',
      clearFilters: 'Clear filters',
      addUser: 'Add user',
      createUser: 'Create user',
      search: 'Search',
      backToSignIn: 'Back to sign in',
      requestNewResetLink: 'Request a new reset link',
      tryAnotherEmail: 'Try another email',
      updatePassword: 'Update password',
      reviewProfileSettings: 'Review profile settings',
      openAnnouncements: 'Open announcements',
    },
    states: {
      loadingContent: 'Loading content',
      loading: 'Loading…',
      closeModal: 'Close modal',
      goToPreviousPage: 'Go to previous page',
      goToNextPage: 'Go to next page',
      searchPlaceholder: 'Search...',
      export: 'Export',
      noDataFound: 'No data found',
      showingResults: 'Showing',
      to: 'to',
      of: 'of',
      results: 'results',
      page: 'Page',
      perPage10: '10 per page',
      perPage25: '25 per page',
      perPage50: '50 per page',
      perPage100: '100 per page',
    },
  },
  meta: {
    defaults: {
      siteName: 'CampusCore',
      title: 'Campus academic workspace',
      description:
        'CampusCore is a focused academic workspace powered by one Java RESTful API and PostgreSQL.',
      ogAlt: 'CampusCore workspace overview',
      twitterTitle: 'CampusCore',
      twitterDescription:
        'A role-aware academic workspace for registration, schedules, grades, announcements, and thesis work.',
    },
    home: {
      title: 'Campus academic workspace',
      description:
        'CampusCore gives administrators, lecturers, and students one steady workspace backed by a single Java API contract.',
    },
    login: {
      title: 'Sign in',
      description: 'Sign in to CampusCore with your campus account.',
    },
    register: {
      title: 'Create an account',
      description: 'Create a verified CampusCore student account.',
    },
    verifyEmail: {
      title: 'Verify email',
      description: 'Verify your CampusCore email address.',
    },
    forgotPassword: {
      title: 'Forgot password',
      description: 'Request a CampusCore password reset link.',
    },
    resetPassword: {
      title: 'Reset password',
      description: 'Set a new CampusCore password and return to the workspace.',
    },
    dashboard: {
      title: 'Workspace',
      description:
        'Protected student and lecturer dashboards for CampusCore.',
    },
    admin: {
      title: 'Admin workspace',
      description: 'Protected administration routes for CampusCore.',
    },
    socialImage: {
      eyebrow: 'CampusCore',
      title: 'Academic work that stays clear from sign-in to thesis progress.',
      description:
        'Registration, teaching, people records, announcements, and thesis work in one focused workspace.',
      badges: [
        'Role-aware workspace',
        'Verified releases',
        'Single Java API',
      ],
    },
  },
  home: {
    navSubtitle: 'Campus academic workspace',
    eyebrow: 'Role-aware academic workspace',
    title: 'Keep learning, teaching, and registration connected.',
    description:
      'CampusCore brings registration, schedules, grades, announcements, and thesis work into one steady workspace for students, lecturers, and campus teams.',
    metricCards: [
      {
        title: 'Course-demo ready',
        description:
          'The portal covers the core academic flows needed for a reproducible local course demo.',
      },
      {
        title: 'Security-first',
        description:
          'Account protection and transaction safeguards stay in place without turning the interface into a status console.',
      },
      {
        title: 'Clear academic ownership',
        description:
          'The UI maps to clear owners instead of collapsing everything back into core.',
      },
    ],
    snapshotEyebrow: 'Academic snapshot',
    snapshotTitle: 'A calmer way to keep campus work moving',
    snapshotChecks: [
      'Students, lecturers, and admins start from one steady workspace',
      'Registration, grades, announcements, and thesis work stay connected in the same session',
      'One OpenAPI contract keeps the web, mobile app, and Java backend aligned',
      'A fresh PostgreSQL database is reproducible through Flyway and seed data',
    ],
    snapshotPrimaryAccessTitle: 'Primary access',
    snapshotPrimaryAccessDescription:
      'Students, lecturers, and admins all enter through one consistent sign-in experience.',
    snapshotReleaseTitle: 'Course-demo scope',
    snapshotReleaseDescription:
      'One Java API, one PostgreSQL database, and focused clients keep the demo easy to verify.',
    capabilitiesEyebrow: 'What the portal is built to do',
    capabilitiesTitle:
      'One frontend language across the critical campus workflows',
    capabilitiesDescription:
      'The interface supports day-to-day academic work with clear auth, data states, and role-aware tasks.',
    pillars: [
      {
        title: 'Identity you can trust',
        description:
          'Account access stays steady across the people-facing workflows that matter during the academic day.',
      },
      {
        title: 'Academic workflows',
        description:
          'Registration, schedules, grades, transcript views, and section work share one stable API contract.',
      },
      {
        title: 'Clear academic states',
        description:
          'Loading, empty, error, forbidden, and success states remain explicit across role workflows.',
      },
      {
        title: 'People records',
        description:
          'Student and lecturer records stay readable and role-protected in the same course database.',
      },
      {
        title: 'Release discipline',
        description:
          'Builds, runtime checks, migrations, and handoffs stay readable for a reproducible course demo.',
      },
      {
        title: 'Campus-ready shell',
        description:
          'One portal for students, lecturers, and admins with sharper states, fewer dead ends, and calmer navigation.',
      },
    ],
    whyEyebrow: 'Why one RESTful API for CampusCore',
    whyTitle: 'Keep the course project focused and reproducible',
    whyDescription:
      'CampusCore keeps the backend in one Java application so the academic contract, database migrations, authorization, and local demo remain easy to understand and verify.',
    whyPoints: [
      {
        title: 'One contract',
        description:
          'Web and mobile clients use the same `/api/v1` OpenAPI surface.',
      },
      {
        title: 'Consistent authorization',
        description:
          'Authentication and role checks are applied in one Spring Security boundary.',
      },
      {
        title: 'Reproducible data',
        description:
          'Flyway and deterministic seed data rebuild the course database from empty PostgreSQL.',
      },
      {
        title: 'Straightforward verification',
        description:
          'Focused Java, frontend, mobile, and Compose gates make the demo state traceable.',
      },
      {
        title: 'Simple local runtime',
        description:
          'The local stack needs only the Java API and PostgreSQL.',
      },
      {
        title: 'Clearer course handoff',
        description:
          'Students can explain the architecture and demonstrate core academic flows end to end.',
      },
    ],
    footerSubtitle: 'Academic workspace',
    footerDescription:
      'A campus platform built for steady sign-in, clearer ownership, and calmer day-to-day academic work.',
    footerWorkspace: 'Workspace',
    footerDelivery: 'Delivery',
    footerLinks: {
      workspace: ['Student access', 'Lecturer workflows', 'Admin tools'],
      delivery: ['Local setup', 'OpenAPI contract', 'Seeded demo data'],
    },
    footerCopyright: 'All rights reserved.',
  },
  authShell: {
    desktopSubtitle: 'Campus academic workspace',
    mobileSubtitle: 'Academic access',
  },
  login: {
    eyebrow: 'Secure access',
    title: 'Sign in to the campus workspace.',
    description:
      'Sign in with your campus account to continue across registration, grades, schedules, and updates.',
    featureTitles: ['Role-aware access', 'Session protection', 'Academic continuity'],
    featureDescriptions: [
      'Admins, lecturers, and students land in the right workspace without a second sign-in step.',
      'Sign-in stays steady across the pages people actually need during the academic day.',
      'Registration, schedules, and grades all start from the same calm entry point.',
    ],
    sectionEyebrow: 'Account access',
    heading: 'Welcome back',
    subheading: 'Sign in with your campus account to continue.',
    emailLabel: 'Email address',
    emailPlaceholder: 'you@university.edu',
    passwordLabel: 'Password',
    passwordPlaceholder: 'Enter your password',
    forgotPassword: 'Forgot password?',
    showPassword: 'Show password',
    hidePassword: 'Hide password',
    signingIn: 'Signing in',
    sessionBehaviorTitle: 'One steady session',
    sessionBehaviorDescription:
      'Move between student, lecturer, and admin work without starting over.',
    reasonMessages: {
      sessionExpired: {
        title: 'Your session ended',
        body: 'Sign in again to continue working in CampusCore.',
      },
      unauthorized: {
        title: 'Sign in required',
        body: 'Your last request needed an active session.',
      },
      signedOut: {
        title: 'Signed out',
        body: 'You have been signed out of the workspace.',
      },
    },
    runtimeNotice: {
      infoTitle: 'Preview ready',
      infoBody: 'This preview is ready for workspace review.',
      warningTitle: 'Sign-in is unavailable in this preview',
      warningBody:
        'This preview cannot reach the workspace services right now. Open the main workspace entry point or try again in a moment.',
    },
    errors: {
      fallback: 'We could not sign you in right now.',
      invalidCredentials: 'The email address or password is incorrect.',
      blocked: 'This sign-in attempt was blocked. Refresh the page and try again.',
      backendUnavailable:
        'CampusCore could not reach sign-in right now. Try again in a moment.',
      temporaryUnavailable:
        'Sign-in is temporarily unavailable. Please try again in a moment.',
      emailVerificationRequired:
        'Verify your email before signing in. Request a fresh link if needed.',
    },
    returnHomeLead: 'Need a different starting point?',
  },
  register: {
    eyebrow: 'Create an account',
    title: 'Start your CampusCore journey.',
    description: 'Create a student account, then verify your campus email before signing in.',
    featureTitles: ['Student access', 'Verified email', 'One shared portal'],
    featureDescriptions: [
      'Self-registration is available for students; staff accounts are created by administrators.',
      'A one-time link keeps new accounts protected before the first session is issued.',
      'Use the same account across registration, schedules, grades, and the assistant.',
    ],
    sectionEyebrow: 'New account',
    heading: 'Create your account',
    subheading: 'Use an email inbox you can open right now.',
    firstName: 'First name',
    lastName: 'Last name',
    emailLabel: 'Email address',
    passwordLabel: 'Password',
    confirmPasswordLabel: 'Confirm password',
    submit: 'Create account',
    submitting: 'Creating account',
    passwordHint: 'Minimum 8 characters.',
    successTitle: 'Check your inbox',
    successDescription: 'Your account is ready. Open the verification link to continue, then return here to sign in.',
    continueVerification: 'Continue to email verification',
    alreadyHaveAccount: 'Already have an account?',
    signIn: 'Sign in',
    errors: {
      mismatch: 'The passwords do not match.',
      required: 'Enter your name, email address, and password to continue.',
      tooShort: 'Choose a password with at least 8 characters.',
      fallback: 'We could not create your account right now.',
      emailExists: 'An account with this email already exists. Try signing in or resetting the password.',
    },
  },
  verifyEmail: {
    eyebrow: 'Email verification',
    title: 'Confirm your campus email.',
    description: 'The link is single-use and expires after 24 hours. We never show the token in the page after it is read.',
    featureTitles: ['Single-use link', 'No auto-login', 'Resend safely'],
    featureDescriptions: [
      'Only the latest verification challenge can be consumed.',
      'After verification, sign in again to establish a new session.',
      'If the link expires, request a fresh one without exposing account existence.',
    ],
    sectionEyebrow: 'Verify account',
    heading: 'Verify your email',
    instructions: 'Paste the token from your email, or open the latest verification link.',
    pending: 'Checking your verification link…',
    success: 'Email verified. You can now sign in to CampusCore.',
    invalid: 'This verification link is invalid or has expired.',
    expired: 'This verification link has expired. Request a fresh link below.',
    attemptsExceeded: 'This verification link was disabled after too many invalid attempts.',
    verificationUnavailable: 'Verification is temporarily unavailable. Retry the link in a moment; if it already succeeded, sign in instead.',
    tokenLabel: 'Verification token',
    tokenPlaceholder: 'Paste a token from your email',
    verify: 'Verify email',
    verifying: 'Verifying email',
    resendTitle: 'Need a new link?',
    resendDescription: 'Enter your email and we will send the latest verification link if the account is eligible.',
    resend: 'Resend link',
    resending: 'Resending link',
    emailLabel: 'Email address',
    emailPlaceholder: 'you@university.edu',
    genericResendSuccess: 'If the account is eligible, a new verification link has been sent.',
    resendThrottled: 'Please wait before requesting another verification email.',
    resendUnavailable: 'Email delivery is temporarily unavailable. Try again in a moment.',
    backToLogin: 'Back to sign in',
  },
  forgotPassword: {
    eyebrow: 'Password recovery',
    title: 'Recover account access without guessing.',
    description:
      'Use your campus email to request a reset link. The response stays consistent whether the account exists or not.',
    featureTitles: ['Verified handoff', 'Clear next steps', 'Safer messaging'],
    featureDescriptions: [
      'Password recovery stays aligned with the same sign-in experience used across the workspace.',
      'The screen keeps recovery guidance visible instead of dropping you into a dead end.',
      'Responses stay intentionally vague so the flow does not confirm whether an email exists.',
    ],
    sectionEyebrow: 'Recovery flow',
    heading: 'Forgot password',
    beforeSend: 'Enter your email and we will send password reset instructions.',
    afterSend: 'The next step is in your email inbox.',
    emailLabel: 'Email address',
    emailPlaceholder: 'you@university.edu',
    emailHint: 'Use the address tied to your campus account.',
    sendResetLink: 'Send reset link',
    sendingResetInstructions: 'Sending reset instructions',
    sentToast: 'If the email exists, a reset link has been sent.',
    failedToast: 'We could not start password recovery right now.',
    rateLimited: 'Too many recovery requests were made. Please wait and try again.',
    sentBanner:
      'If an account matches {email}, a reset link is on the way.',
    sentDescription:
      'Check spam or promotions if you do not see the message right away. You can also start over with another address.',
  },
  resetPassword: {
    eyebrow: 'Reset password',
    title: 'Set a new password and get back into CampusCore.',
    description:
      'Choose a fresh password for your campus account. Once complete, you will sign in again with the updated credentials.',
    featureTitles: ['One secure path', 'Clear requirements', 'Consistent recovery'],
    featureDescriptions: [
      'Reset tokens move back into the same protected sign-in flow instead of branching into a separate experience.',
      'Users see password guidance and validation before the form submits.',
      'Expired or invalid tokens render a stable recovery state instead of a broken page.',
    ],
    invalidTitle: 'This reset link is no longer valid',
    invalidDescription:
      'Request a new password reset link and use the latest email to continue.',
    expiredTitle: 'This reset link has expired',
    expiredDescription: 'Request a new password reset link; links expire after 30 minutes.',
    attemptsExceededTitle: 'This reset link was disabled',
    attemptsExceededDescription: 'Too many invalid attempts were made. Request a fresh reset link.',
    sectionEyebrow: 'New password',
    heading: 'Reset password',
    subheading:
      'Use a password you have not used recently and keep it unique to your campus account.',
    newPassword: 'New password',
    tokenLabel: 'Reset token',
    tokenPlaceholder: 'Paste the token from your email',
    confirmPassword: 'Confirm password',
    newPasswordPlaceholder: 'Enter a new password',
    confirmPasswordPlaceholder: 'Confirm the new password',
    minimumHint: 'Minimum 8 characters.',
    savePassword: 'Saving new password',
    resetPassword: 'Reset password',
    successToast: 'Password reset complete',
    unavailableTitle: 'Password reset is temporarily unavailable',
    errors: {
      mismatch: 'The new password and confirmation must match.',
      tooShort: 'Choose a password with at least 8 characters.',
      fallback: 'We could not reset your password.',
    },
  },
  adminShell: {
    eyebrow: 'Admin workspace',
    portalTitle: 'Academic administration',
    identityLabel: 'Signed in as',
    adminRole: 'Administrator',
    superAdminRole: 'Super administrator',
    academicYears: 'Academic years',
    preferences: 'Preferences',
    skipToContent: 'Skip to admin content',
    sidebarNavigation: 'Admin portal navigation',
    openSidebar: 'Open admin navigation',
    closeSidebar: 'Close admin navigation',
    closeOverlay: 'Close admin navigation overlay',
    menuSections: {
      overview: 'Overview',
      people: 'People and roles',
      academics: 'Academic records',
      campus: 'Campus services',
    },
    signOut: 'Sign out',
    backToDashboard: 'Back to admin dashboard',
    mobileNavigation: 'Admin mobile navigation',
  },
  admin: {
    title: 'Admin dashboard',
    description:
      'Manage campus identities, academic records, announcements, and core thesis workflows from one consistent portal.',
    managementConsoleTitle: 'Management console',
    managementConsoleDescription:
      'Open the record area that needs attention. Every retained route uses the same navigation, state, and confirmation grammar.',
    menuItems: [
      ['Thesis management', 'Manage registration rounds, topics, groups, and progress.'],
      ['User management', 'Review campus accounts, statuses, and role assignments.'],
      ['Lecturers', 'Manage lecturer records and academic ownership data.'],
      ['Courses', 'Maintain catalog structure, codes, and course metadata.'],
      ['Sections', 'Watch capacity, section ownership, and classroom attachment.'],
      ['Enrollments', 'Inspect registration outcomes and enrollment-level actions.'],
      ['Semesters', 'Control the academic timeline and current registration window.'],
      ['Departments', 'Manage departmental structure and faculty mappings.'],
      ['Classrooms', 'Track rooms, buildings, and capacity readiness.'],
      ['Announcements', 'Publish updates that flow out to the rest of the campus.'],
    ],
    stats: ['Students', 'Lecturers', 'Courses', 'Enrollments'],
    statDetails: [
      'People records reachable through the current ownership model.',
      'Active lecturer accounts and teaching-facing identities.',
      'Catalog rows available for section planning and registration.',
      'Enrollment rows available across the retained academic record views.',
    ],
    loading: 'Loading campus overview',
    unavailableTitle: 'Admin overview unavailable',
    unavailableDescription: 'Campus overview is not available right now.',
  },
  dashboardShell: {
    portalTitle: 'Academic information portal',
    identityLabel: 'Signed in as',
    menuSections: {
      overview: 'Overview',
      academic: 'Academic records',
      teaching: 'Teaching',
      campus: 'Campus services',
    },
    roles: {
      student: 'Student access',
      lecturer: 'Lecturer access',
      admin: 'Admin access',
    },
    roleDescription:
      'Keep your next action close without losing the surrounding context.',
    studentRail: {
      title: 'Student context',
      subtitle:
        'Keep the current route, academic signals, and registration shortcuts visible without crowding the main workspace.',
      currentViewLabel: 'Current view',
      signalsTitle: 'Signals',
      notificationLabel: 'Unread updates',
      localeLabel: 'Language',
      sessionSummary:
        'Your student workspace stays in the same signed-in context while you move between registration, schedules, and records.',
      quickActionsTitle: 'Quick actions',
      collapse: 'Collapse student context panel',
      expand: 'Expand student context panel',
      closeDrawer: 'Close student context panel',
      quickLinks: {
        registration: {
          title: 'Registration plan',
          description:
            'Review open sections, waitlists, and the active registration window.',
        },
        schedule: {
          title: 'Weekly schedule',
          description:
            'Keep this term’s meetings close while you compare sections.',
        },
        announcements: {
          title: 'Campus updates',
          description:
            'Open the latest shared notices affecting classes and student activity.',
        },
      },
    },
    menu: {
      dashboard: 'Dashboard',
      courseRegistration: 'Course registration',
      myCourses: 'My courses',
      schedule: 'Schedule',
      grades: 'Grades',
      transcript: 'Transcript',
      announcements: 'Announcements',
      notifications: 'Notifications',
      thesis: 'Thesis workspace',
      teachingSchedule: 'Teaching schedule',
      gradeManagement: 'Grade management',
      profileSettings: 'Profile settings',
      profile: 'Profile',
      settings: 'Settings',
    },
    notifications: {
      title: 'Notifications',
      loading: 'Loading recent alerts…',
      empty:
        'No unread alerts right now. Announcements remain the main broadcast channel for shared updates.',
      fallbackTitle: 'New update',
      fallbackContent: 'A new notification has arrived for your account.',
      openAnnouncements: 'Open announcements',
      openNotifications: 'Open notifications center',
      description:
        'Keep account alerts, academic deadlines, and workflow updates in one focused inbox.',
      refresh: 'Refresh notifications',
      markAllRead: 'Mark all as read',
      markRead: 'Mark as read',
      unread: 'Unread',
      all: 'All notifications',
      read: 'Read',
      noUnread: 'You have no unread notifications.',
      noAll: 'Your notification inbox is empty.',
      loadFailed: 'Notifications could not be loaded right now.',
      updateFailed: 'The notification could not be updated. Try again.',
      updatedCount: '{count} notifications marked as read.',
    },
    controls: {
      openSidebar: 'Open sidebar navigation',
      closeSidebar: 'Close sidebar navigation',
      collapseSidebar: 'Collapse sidebar navigation',
      expandSidebar: 'Expand sidebar navigation',
      closeOverlay: 'Close sidebar overlay',
      openStudentRail: 'Open student context panel',
      closeStudentRailOverlay: 'Close student context overlay',
      toggleNotifications: 'Toggle notifications panel',
      toggleProfile: 'Toggle profile menu',
      mobileNavigation: 'Mobile workspace navigation',
      skipToContent: 'Skip to portal content',
      sidebarNavigation: 'Student and lecturer portal navigation',
      preferences: 'Preferences',
    },
    pageDefaults: {
      description:
        'Navigate the current workflow without leaving the workspace.',
      title: 'Campus workspace',
      fallbackDescription:
        'Move through your current role surface with consistent session handling.',
    },
    signOutPage: {
      eyebrow: 'Session handoff',
      title: 'Signing you out',
      description:
        'We are closing your current workspace session and returning you to sign-in.',
      progress: 'Ending your session...',
    },
    routeDescriptions: {
      dashboard:
        'Registration, coursework, schedules, and profile tasks stay in one student workspace.',
      profile:
        'Keep contact details and credential rotation aligned with the active browser session.',
      register:
        'Browse sections and manage enrollment decisions for the current term.',
      enrollments:
        'Track the classes you are taking and the sections attached to them.',
      schedule:
        'Keep the weekly class view close while the rest of the portal stays reachable.',
      grades: 'Review published grades and current academic standing.',
      transcript: 'View cumulative academic history and semester outcomes.',
      announcements: 'Read campus-wide updates and shared notices.',
      notifications:
        'Review account alerts, academic deadlines, and workflow updates in one focused inbox.',
      lecturer:
        'Keep teaching tasks, grading queues, section context, and announcements in one lecturer workspace.',
      lecturerSchedule: 'Track assigned sections, rooms, and meeting windows.',
      lecturerGrades:
        'Review grading queues, filter by term, and move publish-ready sections forward.',
      lecturerAnnouncements:
        'Share updates with the students connected to your sections.',
      thesis:
        'Track thesis registration rounds, topics, groups, and progress.',
    },
    loading: 'Loading workspace',
  },
  studentDashboard: {
    eyebrow: 'Student workspace',
    title: 'Welcome back, {name}',
    description:
      'The current term is {semester}. Move between registration, coursework, schedules, and profile updates without leaving the student workspace.',
    currentTermFallback: 'No active term',
    currentDateLabel: 'Today',
    metrics: {
      coursesInScope: 'Courses in scope',
      confirmedEnrollments: 'Confirmed enrollments',
      pendingDecisions: 'Pending decisions',
      currentSemester: 'Current semester',
      details: [
        'Registration, section context, and current coursework remain visible from the same student workspace.',
        'Confirmed sections stay close so you can move into schedules, grades, and transcript work without losing context.',
        'Anything that still needs attention stays visible before it turns into a registration surprise.',
        'The dashboard keeps one active academic context so the rest of the student tools stay aligned.',
      ],
    },
    panels: {
      nextActions: {
        title: 'Next actions',
        description:
          'Open the student tools that usually need attention first during the current term.',
      },
      currentCourses: {
        title: 'Current courses',
        description:
          'Confirmed enrollments stay visible here so you can check course context before moving deeper into the workspace.',
        emptyTitle: 'No confirmed courses yet',
        emptyDescription:
          'Once enrollment is confirmed, your current courses will appear here.',
        sectionLabel: 'Section {section}',
      },
      referenceLinks: {
        title: 'Reference links',
        description:
          'Keep the supporting student views close without leaving the same session-backed shell.',
      },
      currentStatus: {
        title: 'Current status',
        description:
          'A quick read on the active academic context and any follow-up that still needs attention.',
        semesterSelectionTitle: 'Semester selection',
        semesterSelectionActive:
          'The dashboard is using {semester} for the current academic context.',
        semesterSelectionEmpty: 'No preferred semester is active yet.',
        enrollmentHealthTitle: 'Enrollment health',
        enrollmentHealthPending:
          '{count} registration item(s) still need attention.',
        enrollmentHealthClear:
          'No pending registration issues are blocking the current view.',
      },
    },
    quickActions: [
      ['Register courses', 'Browse available sections and make enrollment decisions.'],
      ['Open schedule', 'Check what is on the calendar this week.'],
      ['Review grades', 'See published results and academic standing.'],
    ],
    portalLinks: [
      ['My courses', 'Current registrations, section details, and status.'],
      ['Transcript', 'Semester history and cumulative academic outcomes.'],
      ['Announcements', 'Shared updates from the university and course teams.'],
    ],
    errors: {
      loadFailed: 'Your dashboard data could not be loaded.',
      unavailableTitle: 'Dashboard unavailable',
      loading: 'Loading dashboard',
    },
  },
  profile: {
    eyebrow: 'Account settings',
    title: 'Profile settings',
    description:
      'Update personal details, keep contact information current, and rotate credentials without leaving the workspace.',
    profileTitle: 'Account profile',
    profileDescription:
      'Keep the account record aligned with the information your campus teams rely on.',
    profileUpdated: 'Profile updated',
    profileSaveFailed: 'We could not save your profile changes.',
    passwordTitle: 'Password and session safety',
    passwordDescription:
      'Use a strong password and expect to sign in again after a successful change.',
    passwordUpdated: 'Password updated',
    passwordUpdateFailed: 'We could not update your password.',
    whatChangesTitle: 'What changes here',
    whatChanges: [
      'Profile edits update the browser session view after a successful save.',
      'Password rotation stays on the same authenticated route and uses the shared auth contract.',
      'Sensitive account ownership fields, such as your email, stay controlled by the service owner instead of an inline edit field.',
    ],
    fields: {
      firstName: 'First name',
      lastName: 'Last name',
      email: 'Email',
      phone: 'Phone',
      dateOfBirth: 'Date of birth',
      address: 'Address',
      currentPassword: 'Current password',
      newPassword: 'New password',
      confirmNewPassword: 'Confirm new password',
      managedHint: 'Email is managed through your campus account owner.',
      phonePlaceholder: '+66...',
      addressPlaceholder: 'Street, city, region',
      currentPasswordPlaceholder: 'Enter your current password',
      newPasswordPlaceholder: 'Choose a new password',
      confirmNewPasswordPlaceholder: 'Confirm the new password',
      passwordHint: 'Minimum 8 characters. Use something unique to this account.',
    },
    errors: {
      mismatch: 'The new password and confirmation must match.',
      tooShort: 'Choose a password with at least 8 characters.',
    },
    buttons: {
      savingChanges: 'Saving changes',
      updatingPassword: 'Updating password',
    },
  },
  lecturerDashboard: {
    eyebrow: 'Lecturer workspace',
    title: 'Welcome back, {name}',
    description:
      'Keep section work, grading queues, and teaching updates in one lecturer-focused workspace.',
    quickActionsTitle: 'Quick actions',
    quickActionsDescription:
      'Open the lecturer tools that usually drive the next teaching action.',
    gradingQueueTitle: 'Grading queue',
    gradingQueueDescription:
      'Sections nearest to final review stay visible here so grading work remains the primary next step.',
    gradingQueueEmptyTitle: 'No grading assignments',
    gradingQueueEmptyDescription:
      'Teaching sections with grading responsibility will appear here once they are active.',
    sectionsInScopeTitle: 'Sections in scope',
    sectionsInScopeDescription:
      'Assigned sections stay visible with capacity and department context before you move into schedule or grading detail.',
    sectionsInScopeEmptyTitle: 'No teaching assignments yet',
    sectionsInScopeEmptyDescription:
      'Assigned sections will appear here as soon as the current term is configured.',
    announcementsTitle: 'Latest announcements',
    announcementsDescription:
      'Broadcast teaching updates here without taking the page away from the current workload.',
    announcementsEmptyTitle: 'No new notices',
    announcementsEmptyDescription:
      'Shared notices for the lecturer workspace will show up here once they are published.',
    quickLinks: [
      ['Teaching schedule', 'Check rooms, sections, and meeting times for the current term.'],
      ['Grade management', 'Finish grading queues and move publish-ready sections forward.'],
      ['Announcements', 'Review broadcast updates that affect your sections and teaching day.'],
    ],
    metrics: {
      labels: ['Sections', 'Students', 'Ready to publish', 'Fresh notices'],
      details: [
        'Assigned teaching sections stay visible so grading and scheduling decisions remain grounded in the same term context.',
        'Enrollment volume stays close to the lecturer workspace so section-level follow-up remains visible.',
        'Publish-ready grading work surfaces early so final review does not get lost behind the rest of the workflow.',
        'Broadcast teaching updates remain visible without pulling attention away from the grading queue.',
      ],
    },
    queueStatusReady: 'Ready to publish',
    queueStatusProgress: 'In progress',
    studentsSuffix: 'students',
    gradedSuffix: 'graded',
    errors: {
      loadFailed: 'The lecturer dashboard could not load its academic data.',
      unavailableTitle: 'Lecturer dashboard unavailable',
      loading: 'Loading lecturer dashboard',
    },
  },
  lecturerGrades: {
    eyebrow: 'Lecturer workspace',
    title: 'Grade management',
    description:
      'Track grading progress for {semester}, then move publish-ready sections into the final review step.',
    allSemesters: 'all semesters',
    allSemestersOption: 'All semesters',
    selectSemester: 'Select semester for grade management',
    queueTitle: 'Grade management queue',
    queueDescription:
      'Filter by semester, review section progress, and continue into the detail route that owns grade entry and publishing.',
    emptyTitle: 'No grading sections yet',
    emptyDescription:
      'Sections with grading responsibility will appear here once the teaching load is assigned.',
    labels: {
      sections: 'Sections',
      gradesCaptured: 'Grades captured',
      readyToPublish: 'Ready to publish',
      credits: 'credits',
      enrolled: 'enrolled',
      graded: 'graded',
      published: 'published',
      sectionPrefix: 'Section',
      readyStatus: 'Ready to publish',
      manageGrades: 'Manage grades',
      enterGrades: 'Enter grades',
    },
    details: [
      'Active grading responsibilities for the selected semester remain grouped in one queue.',
      'Recorded grade entries stay visible before you move any section to final publishing.',
      'Sections that can move into the final review step are highlighted without changing the grading contract.',
    ],
    errors: {
      loadFailed: 'Grade management data could not be loaded.',
      unavailableTitle: 'Grade management unavailable',
      loading: 'Loading grade management',
    },
  },
  thesis: {
    eyebrow: 'Thesis workspace',
    title: 'Keep the whole thesis journey in one calm timeline.',
    description:
      'Registration rounds, topic choices, group membership, and recorded progress stay connected to your campus identity.',
    selectRound: 'Select registration round',
    noRound: 'No thesis registration round is available yet.',
    loading: 'Loading thesis workspace',
    loadFailed: 'Thesis data could not be loaded right now.',
    retry: 'Retry thesis workspace',
    roundStatus: 'Round status',
    registrationWindow: 'Registration window',
    topics: 'Published topics',
    groups: 'Thesis groups',
    topicsTitle: 'Find a direction that fits your group.',
    topicsDescription:
      'Published topics are grouped by department. Topic selection becomes a proposal your coordinator can review.',
    groupsTitle: 'Your group workspace',
    groupsDescription:
      'Keep membership, topic decisions, and the current group status visible in one place.',
    createGroup: 'Create a thesis group',
    addMember: 'Add member',
    chooseTopic: 'Choose topic',
    groupCreated: 'Thesis group created.',
    actionFailed: 'This action could not be completed.',
    memberCount: '{count}/3 members',
    noGroup: 'You do not have a group in this round yet.',
    noGroupDescription:
      'Create a group when registration is open, then invite up to two classmates.',
    noTopics: 'No published topics yet.',
    noTopicsDescription:
      'The department will publish the available directions before topic selection opens.',
    navigation: {
      catalog: 'Topic catalog',
      progress: 'Thesis progress',
    },
    catalogTitle: 'Published topic catalog',
    catalogDescription:
      'Scan the published directions first, then open a focused detail view before choosing a topic for your group.',
    topicDetailTitle: 'Topic detail',
    topicDetailDescription:
      'Review the full topic brief, capacity, department, and current selection state.',
    progressTitle: 'Thesis progress',
    progressDescription:
      'Follow the current group and registration-round state without inventing progress that the service has not recorded.',
    progressCurrentStage: 'Current stage',
    progressGroupRequired: 'Create or join a group to see group-specific progress.',
    progressSteps: [
      'Registration round selected',
      'Thesis group prepared',
      'Topic proposal submitted',
      'Coordinator decision recorded',
      'Latest progress status recorded',
    ],
    backToWorkspace: 'Back to thesis workspace',
    lifecycleViewsTitle: 'Thesis lifecycle views',
    lifecycleViewsDescription:
      'Open a focused screen for published topics or the latest recorded group progress.',
    status: {
      DRAFT: 'Draft',
      REGISTRATION_OPEN: 'Registration open',
      REGISTRATION_CLOSED: 'Registration closed',
      PROPOSALS_PUBLISHED: 'Topics published',
      CLOSED: 'Closed',
      CANCELLED: 'Cancelled',
      PENDING: 'Pending review',
      APPROVED: 'Approved',
      REJECTED: 'Needs changes',
      SUBMITTED: 'Submitted',
      COMPLETED: 'Completed',
    },
    progress: {
      eyebrow: 'Progress tracking',
    },
    detail: {
      groupsTitle: 'Registered groups',
      groupsDescription: 'Groups formed by students in this round.',
      noGroups: 'No groups registered yet.',
      noGroupsDescription: 'Groups will appear once students form and register.',
      maxGroups: 'Max groups',
      members: 'members',
    },
    admin: {
      title: 'Thesis administration',
      description: 'Create registration rounds, manage their lifecycle, and monitor topics and groups.',
      createRound: 'Create round',
      totalRounds: 'Total rounds',
      openRounds: 'Open for registration',
      noRounds: 'No thesis rounds yet.',
      noRoundsDescription: 'Create your first registration round to start the thesis workflow.',
      forbiddenTitle: 'Admin access required',
      forbiddenDescription: 'Only administrators can manage thesis registration rounds.',
      returnToWorkspace: 'Return to your workspace',
      roundName: 'Round name',
      roundNamePlaceholder: 'e.g. Spring 2026 Thesis Round',
      thesisType: 'Thesis type',
      registrationStart: 'Registration start',
      registrationEnd: 'Registration end',
      proposalPublishAt: 'Proposal publish date',
      createIncomplete: 'Please fill in name, registration start, and registration end.',
      created: 'Thesis round created successfully.',
      openRegistration: 'Open registration',
      closeRegistration: 'Close registration',
      publishProposals: 'Publish proposals',
      registrationOpened: 'Registration is now open.',
      registrationClosed: 'Registration is now closed.',
      proposalsPublished: 'Proposals published successfully.',
    },
  },
  assistant: {
    label: 'AI assistant',
    title: 'Thesis guide',
    description: 'Ask about the current thesis workflow and published topic context.',
    open: 'Open thesis assistant',
    close: 'Close thesis assistant',
    placeholder: 'Ask about registration, topics, groups, or progress...',
    send: 'Send message',
    thinking: 'Checking the authorized thesis context...',
    empty: 'Start with a question about your current thesis round.',
    unavailable: 'The assistant is not available right now. Your thesis data is unchanged.',
    answered: 'Answered from the curated corpus',
    noMatch: 'No matching source',
    degraded: 'Knowledge temporarily unavailable',
    sources: 'Sources',
    you: 'You',
    history: 'Conversation history',
    backToChat: 'Back to chat',
    newConversation: 'New conversation',
    deleteConversation: 'Delete conversation',
    deleteConversationConfirm: 'Delete this conversation? Its messages will be permanently removed.',
    historyLoading: 'Loading your conversations…',
    historyUnavailable: 'Conversation history is temporarily unavailable.',
    historyEmpty: 'No saved conversations yet.',
    untitledConversation: 'Thesis conversation',
    model: 'Model',
    stop: 'Stop generating',
    retry: 'Retry',
    quotaExceeded: 'Daily AI limit reached. Try again tomorrow or continue with the curated sources.',
    cancelled: 'Generation stopped. You can retry when you are ready.',
    feedbackUp: 'Mark answer helpful',
    feedbackDown: 'Mark answer not helpful',
    feedbackUnavailable: 'Your feedback could not be saved. Try again.',
    offline: 'No network connection. Check your connection and try again.',
    sessionExpired: 'Your session has expired. Please sign in again.',
    forbidden: 'This account is not allowed to use the assistant.',
  },
} as const;

type DeepWiden<T> = T extends string
  ? string
  : T extends readonly (infer U)[]
    ? readonly DeepWiden<U>[]
    : T extends object
      ? { readonly [K in keyof T]: DeepWiden<T[K]> }
      : T;

export type I18nMessages = DeepWiden<typeof en>;

export const vi: I18nMessages = {
  common: {
    locale: {
      label: 'Ngôn ngữ',
      english: 'Tiếng Anh',
      vietnamese: 'Tiếng Việt',
      switchToEnglish: 'Switch to English',
      switchToVietnamese: 'Chuyển sang tiếng Việt',
    },
    actions: {
      signIn: 'Đăng nhập',
      signOut: 'Đăng xuất',
      save: 'Lưu',
      saveChanges: 'Lưu thay đổi',
      cancel: 'Hủy',
      confirm: 'Xác nhận',
      retry: 'Thử lại',
      refresh: 'Làm mới dữ liệu',
      openSchedule: 'Mở thời khóa biểu',
      openTool: 'Mở công cụ',
      openView: 'Mở màn hình',
      openWorkspace: 'Mở workspace',
      returnHome: 'Quay về trang chủ',
      openDashboard: 'Mở dashboard',
      continueToWorkspace: 'Tiếp tục vào workspace',
      signInToWorkspace: 'Đăng nhập vào workspace',
      reviewAdmin: 'Mở khu quản trị',
      browseSections: 'Xem lớp học phần',
      clearFilters: 'Xóa bộ lọc',
      addUser: 'Thêm người dùng',
      createUser: 'Tạo người dùng',
      search: 'Tìm kiếm',
      backToSignIn: 'Quay lại đăng nhập',
      requestNewResetLink: 'Yêu cầu liên kết mới',
      tryAnotherEmail: 'Thử email khác',
      updatePassword: 'Cập nhật mật khẩu',
      reviewProfileSettings: 'Xem cài đặt hồ sơ',
      openAnnouncements: 'Mở thông báo',
    },
    states: {
      loadingContent: 'Đang tải nội dung',
      loading: 'Đang tải…',
      closeModal: 'Đóng hộp thoại',
      goToPreviousPage: 'Trang trước',
      goToNextPage: 'Trang sau',
      searchPlaceholder: 'Tìm kiếm...',
      export: 'Xuất dữ liệu',
      noDataFound: 'Không có dữ liệu',
      showingResults: 'Hiển thị',
      to: 'đến',
      of: 'trên',
      results: 'kết quả',
      page: 'Trang',
      perPage10: '10 dòng / trang',
      perPage25: '25 dòng / trang',
      perPage50: '50 dòng / trang',
      perPage100: '100 dòng / trang',
    },
  },
  meta: {
    defaults: {
      siteName: 'CampusCore',
      title: 'Không gian học vụ CampusCore',
      description:
        'CampusCore là workspace học vụ tập trung, chạy bằng một Java RESTful API và PostgreSQL.',
      ogAlt: 'Tổng quan workspace CampusCore',
      twitterTitle: 'CampusCore',
      twitterDescription:
        'Workspace học vụ theo vai trò cho đăng ký, lịch học, điểm, thông báo và luận văn.',
    },
    home: {
      title: 'Không gian học vụ CampusCore',
      description:
        'CampusCore mang đến một workspace ổn định cho quản trị, giảng viên và sinh viên trên cùng một contract Java API.',
    },
    login: {
      title: 'Đăng nhập',
      description: 'Đăng nhập CampusCore bằng tài khoản campus của bạn.',
    },
    register: {
      title: 'Tạo tài khoản',
      description: 'Tạo tài khoản sinh viên CampusCore đã xác minh.',
    },
    verifyEmail: {
      title: 'Xác minh email',
      description: 'Xác minh địa chỉ email CampusCore của bạn.',
    },
    forgotPassword: {
      title: 'Quên mật khẩu',
      description: 'Yêu cầu liên kết đặt lại mật khẩu CampusCore.',
    },
    resetPassword: {
      title: 'Đặt lại mật khẩu',
      description: 'Tạo mật khẩu mới cho CampusCore và quay lại workspace.',
    },
    dashboard: {
      title: 'Workspace',
      description:
        'Các dashboard được bảo vệ cho sinh viên và giảng viên trong CampusCore.',
    },
    admin: {
      title: 'Workspace quản trị',
      description: 'Các route quản trị được bảo vệ của CampusCore.',
    },
    socialImage: {
      eyebrow: 'CampusCore',
      title: 'Công việc học vụ rõ ràng từ đăng nhập đến tiến độ luận văn.',
      description:
        'Đăng ký học phần, giảng dạy, hồ sơ, thông báo và luận văn trong một workspace tập trung.',
      badges: [
        'Workspace theo vai trò',
        'Phát hành đã xác minh',
        'Một Java API',
      ],
    },
  },
  home: {
    navSubtitle: 'Không gian học vụ CampusCore',
    eyebrow: 'Không gian học vụ theo vai trò',
    title: 'Kết nối học tập, giảng dạy và đăng ký học phần.',
    description:
      'CampusCore gom đăng ký học phần, thời khóa biểu, điểm, thông báo và luận văn vào một không gian làm việc ổn định cho sinh viên, giảng viên và quản trị.',
    metricCards: [
      {
        title: 'Sẵn sàng cho demo môn học',
        description:
          'Portal bao phủ các luồng học vụ cốt lõi cần cho một demo local có thể tái lập.',
      },
      {
        title: 'Ưu tiên bảo mật',
        description:
          'Các lớp bảo vệ tài khoản và giao dịch vẫn được giữ vững mà không biến giao diện thành bảng chẩn đoán kỹ thuật.',
      },
      {
        title: 'Ownership học vụ rõ ràng',
        description:
          'UI bám theo owner rõ ràng thay vì dồn tất cả trở lại một lõi duy nhất.',
      },
    ],
    snapshotEyebrow: 'Tổng quan học vụ',
    snapshotTitle: 'Một cách bình tĩnh hơn để giữ campus luôn chạy',
    snapshotChecks: [
      'Sinh viên, giảng viên và quản trị cùng đi vào một workspace ổn định',
      'Đăng ký học phần, điểm, thông báo và luận văn nối tiếp trong cùng một phiên',
      'Một contract OpenAPI giữ web, mobile và Java backend đồng bộ',
      'Flyway và seed data dựng lại PostgreSQL sạch một cách ổn định',
    ],
    snapshotPrimaryAccessTitle: 'Lối vào chính',
    snapshotPrimaryAccessDescription:
      'Sinh viên, giảng viên và quản trị cùng đi qua một trải nghiệm đăng nhập nhất quán.',
    snapshotReleaseTitle: 'Phạm vi demo môn học',
    snapshotReleaseDescription:
      'Một Java API, một PostgreSQL và các client tập trung giúp demo dễ kiểm chứng.',
    capabilitiesEyebrow: 'Portal này được xây để làm gì',
    capabilitiesTitle: 'Một ngôn ngữ giao diện cho các luồng campus quan trọng',
    capabilitiesDescription:
      'Giao diện hỗ trợ công việc học vụ hằng ngày với xác thực, trạng thái dữ liệu và tác vụ theo vai trò rõ ràng.',
    pillars: [
      {
        title: 'Định danh đáng tin',
        description:
          'Đăng nhập được giữ ổn định trên những luồng mà người dùng thật sự cần trong ngày học vụ.',
      },
      {
        title: 'Luồng học vụ',
        description:
          'Đăng ký, thời khóa biểu, điểm, bảng điểm và công việc section dùng chung một contract API ổn định.',
      },
      {
        title: 'Trạng thái học vụ rõ ràng',
        description:
          'Loading, empty, error, forbidden và success được thể hiện rõ trên các luồng theo vai trò.',
      },
      {
        title: 'Hồ sơ người dùng',
        description:
          'Dữ liệu sinh viên và giảng viên được phân quyền rõ trong cùng database đồ án.',
      },
      {
        title: 'Kỷ luật phát hành',
        description:
          'Build, kiểm tra runtime, migration và bàn giao luôn rõ cho một demo môn học tái lập được.',
      },
      {
        title: 'Portal sẵn cho campus',
        description:
          'Một cổng chung cho sinh viên, giảng viên và quản trị với trạng thái rõ hơn, ít ngõ cụt hơn và điều hướng điềm tĩnh hơn.',
      },
    ],
    whyEyebrow: 'Vì sao CampusCore dùng một RESTful API',
    whyTitle: 'Giữ đồ án tập trung và dễ tái lập',
    whyDescription:
      'CampusCore giữ backend trong một ứng dụng Java để contract học vụ, migration database, phân quyền và demo local dễ hiểu và dễ kiểm chứng.',
    whyPoints: [
      {
        title: 'Một contract',
        description:
          'Web và mobile cùng dùng bề mặt OpenAPI `/api/v1`.',
      },
      {
        title: 'Phân quyền nhất quán',
        description:
          'Xác thực và role check nằm trong một ranh giới Spring Security.',
      },
      {
        title: 'Dữ liệu tái lập',
        description:
          'Flyway và seed data dựng lại database đồ án từ PostgreSQL trống.',
      },
      {
        title: 'Kiểm chứng đơn giản',
        description:
          'Các gate Java, frontend, mobile và Compose giúp truy dấu trạng thái demo.',
      },
      {
        title: 'Runtime local gọn',
        description:
          'Stack local chỉ cần Java API và PostgreSQL.',
      },
      {
        title: 'Bàn giao đồ án rõ hơn',
        description:
          'Sinh viên có thể giải thích kiến trúc và demo các luồng học vụ từ đầu đến cuối.',
      },
    ],
    footerSubtitle: 'Không gian học vụ',
    footerDescription:
      'Nền tảng campus tập trung vào đăng nhập ổn định, owner rõ ràng và công việc học vụ hằng ngày bớt rối hơn.',
    footerWorkspace: 'Workspace',
    footerDelivery: 'Triển khai',
    footerLinks: {
      workspace: ['Khu sinh viên', 'Luồng giảng viên', 'Công cụ quản trị'],
      delivery: ['Thiết lập local', 'Contract OpenAPI', 'Dữ liệu demo đã seed'],
    },
    footerCopyright: 'Mọi quyền được bảo lưu.',
  },
  authShell: {
    desktopSubtitle: 'Không gian học vụ CampusCore',
    mobileSubtitle: 'Truy cập học vụ',
  },
  login: {
    eyebrow: 'Truy cập an toàn',
    title: 'Đăng nhập vào không gian học vụ CampusCore.',
    description:
      'Dùng cùng một phiên trình duyệt đã được bảo vệ để di chuyển giữa đăng ký, điểm, thông báo và không gian theo vai trò.',
    featureTitles: ['Vào đúng vai trò', 'Phiên đăng nhập an toàn', 'Luồng học vụ liền mạch'],
    featureDescriptions: [
      'Quản trị, giảng viên và sinh viên đi vào đúng không gian làm việc mà không cần một bước đăng nhập thứ hai.',
      'Đăng nhập được giữ nhất quán trên những màn quan trọng nhất trong ngày học vụ.',
      'Đăng ký, thời khóa biểu và điểm luôn đi qua cùng một điểm vào rõ ràng.',
    ],
    sectionEyebrow: 'Truy cập tài khoản',
    heading: 'Chào mừng bạn quay lại',
    subheading: 'Đăng nhập bằng tài khoản campus để tiếp tục.',
    emailLabel: 'Địa chỉ email',
    emailPlaceholder: 'you@university.edu',
    passwordLabel: 'Mật khẩu',
    passwordPlaceholder: 'Nhập mật khẩu của bạn',
    forgotPassword: 'Quên mật khẩu?',
    showPassword: 'Hiện mật khẩu',
    hidePassword: 'Ẩn mật khẩu',
    signingIn: 'Đang đăng nhập',
    sessionBehaviorTitle: 'Một phiên làm việc liền mạch',
    sessionBehaviorDescription:
      'Tiếp tục công việc giữa các khu vực sinh viên, giảng viên và quản trị mà không phải bắt đầu lại từ đầu.',
    reasonMessages: {
      sessionExpired: {
        title: 'Phiên của bạn đã kết thúc',
        body: 'Hãy đăng nhập lại để tiếp tục làm việc trong CampusCore.',
      },
      unauthorized: {
        title: 'Cần đăng nhập',
        body: 'Yêu cầu trước đó cần một phiên đang hoạt động.',
      },
      signedOut: {
        title: 'Đã đăng xuất',
        body: 'Bạn đã được đăng xuất khỏi không gian làm việc.',
      },
    },
    runtimeNotice: {
      infoTitle: 'Bản xem trước đã sẵn sàng',
      infoBody: 'Bản xem trước này đã sẵn sàng để rà giao diện workspace.',
      warningTitle: 'Đăng nhập chưa sẵn sàng trên bản xem trước này',
      warningBody:
        'Bản xem trước hiện chưa kết nối được tới các dịch vụ workspace. Hãy mở lối vào chính của workspace hoặc thử lại sau ít phút.',
    },
    errors: {
      fallback: 'Hiện chưa thể đăng nhập.',
      invalidCredentials: 'Email hoặc mật khẩu không đúng.',
      blocked: 'Lần đăng nhập này đã bị chặn. Hãy làm mới trang rồi thử lại.',
      backendUnavailable:
        'CampusCore hiện chưa kết nối được tới dịch vụ đăng nhập. Hãy thử lại sau ít phút.',
      temporaryUnavailable:
        'Đăng nhập tạm thời chưa sẵn sàng. Vui lòng thử lại sau ít phút.',
      emailVerificationRequired:
        'Hãy xác minh email trước khi đăng nhập. Bạn có thể yêu cầu một liên kết mới.',
    },
    returnHomeLead: 'Cần một điểm vào khác?',
  },
  register: {
    eyebrow: 'Tạo tài khoản',
    title: 'Bắt đầu hành trình CampusCore.',
    description: 'Tạo tài khoản sinh viên, sau đó xác minh email campus trước khi đăng nhập.',
    featureTitles: ['Quyền sinh viên', 'Email đã xác minh', 'Một cổng học vụ'],
    featureDescriptions: [
      'Sinh viên có thể tự đăng ký; tài khoản giảng viên và quản trị do admin tạo.',
      'Liên kết dùng một lần bảo vệ tài khoản mới trước khi cấp phiên đầu tiên.',
      'Dùng cùng tài khoản cho đăng ký học, lịch, điểm và trợ lý.',
    ],
    sectionEyebrow: 'Tài khoản mới',
    heading: 'Tạo tài khoản',
    subheading: 'Dùng hộp thư bạn có thể mở ngay bây giờ.',
    firstName: 'Tên',
    lastName: 'Họ',
    emailLabel: 'Địa chỉ email',
    passwordLabel: 'Mật khẩu',
    confirmPasswordLabel: 'Xác nhận mật khẩu',
    submit: 'Tạo tài khoản',
    submitting: 'Đang tạo tài khoản',
    passwordHint: 'Tối thiểu 8 ký tự.',
    successTitle: 'Kiểm tra hộp thư',
    successDescription: 'Tài khoản đã sẵn sàng. Mở liên kết xác minh để tiếp tục, rồi quay lại đây đăng nhập.',
    continueVerification: 'Tiếp tục xác minh email',
    alreadyHaveAccount: 'Đã có tài khoản?',
    signIn: 'Đăng nhập',
    errors: {
      mismatch: 'Hai mật khẩu không trùng nhau.',
      required: 'Hãy nhập họ tên, địa chỉ email và mật khẩu để tiếp tục.',
      tooShort: 'Hãy chọn mật khẩu có ít nhất 8 ký tự.',
      fallback: 'Hiện chưa thể tạo tài khoản.',
      emailExists: 'Email này đã có tài khoản. Hãy đăng nhập hoặc đặt lại mật khẩu.',
    },
  },
  verifyEmail: {
    eyebrow: 'Xác minh email',
    title: 'Xác nhận email campus của bạn.',
    description: 'Liên kết chỉ dùng một lần và hết hạn sau 24 giờ. Token không được giữ lại trên trang sau khi đọc.',
    featureTitles: ['Liên kết một lần', 'Không tự đăng nhập', 'Gửi lại an toàn'],
    featureDescriptions: [
      'Chỉ challenge xác minh mới nhất được phép tiêu thụ.',
      'Sau khi xác minh, hãy đăng nhập lại để tạo phiên mới.',
      'Nếu liên kết hết hạn, bạn có thể yêu cầu liên kết mới mà không làm lộ tài khoản.',
    ],
    sectionEyebrow: 'Xác minh tài khoản',
    heading: 'Xác minh email',
    instructions: 'Dán token từ email hoặc mở liên kết xác minh mới nhất.',
    pending: 'Đang kiểm tra liên kết xác minh…',
    success: 'Email đã được xác minh. Bạn có thể đăng nhập CampusCore.',
    invalid: 'Liên kết xác minh không hợp lệ hoặc đã hết hạn.',
    expired: 'Liên kết xác minh đã hết hạn. Hãy yêu cầu liên kết mới bên dưới.',
    attemptsExceeded: 'Liên kết xác minh đã bị vô hiệu sau quá nhiều lần thử không hợp lệ.',
    verificationUnavailable: 'Dịch vụ xác minh tạm thời chưa sẵn sàng. Hãy thử lại liên kết sau ít phút; nếu đã xác minh thành công, hãy đăng nhập.',
    tokenLabel: 'Token xác minh',
    tokenPlaceholder: 'Dán token từ email',
    verify: 'Xác minh email',
    verifying: 'Đang xác minh email',
    resendTitle: 'Cần liên kết mới?',
    resendDescription: 'Nhập email và chúng tôi sẽ gửi liên kết mới nếu tài khoản đủ điều kiện.',
    resend: 'Gửi lại liên kết',
    resending: 'Đang gửi lại liên kết',
    emailLabel: 'Địa chỉ email',
    emailPlaceholder: 'you@university.edu',
    genericResendSuccess: 'Nếu tài khoản đủ điều kiện, liên kết xác minh mới đã được gửi.',
    resendThrottled: 'Vui lòng chờ trước khi yêu cầu thêm email xác minh.',
    resendUnavailable: 'Dịch vụ gửi email tạm thời chưa sẵn sàng. Hãy thử lại sau.',
    backToLogin: 'Quay lại đăng nhập',
  },
  forgotPassword: {
    eyebrow: 'Khôi phục mật khẩu',
    title: 'Lấy lại quyền truy cập mà không cần đoán mò.',
    description:
      'Dùng email campus để yêu cầu liên kết đặt lại mật khẩu. Phản hồi sẽ giữ nhất quán dù tài khoản có tồn tại hay không.',
    featureTitles: ['Bàn giao đã xác minh', 'Bước kế tiếp rõ ràng', 'Thông điệp an toàn hơn'],
    featureDescriptions: [
      'Khôi phục mật khẩu vẫn đi cùng một trải nghiệm đăng nhập nhất quán trong toàn bộ workspace.',
      'Màn hình luôn giữ hướng dẫn khôi phục hiển thị thay vì đưa bạn vào ngõ cụt.',
      'Phản hồi được giữ mơ hồ có chủ đích để không xác nhận email có tồn tại hay không.',
    ],
    sectionEyebrow: 'Luồng khôi phục',
    heading: 'Quên mật khẩu',
    beforeSend: 'Nhập email của bạn và chúng tôi sẽ gửi hướng dẫn đặt lại mật khẩu.',
    afterSend: 'Bước tiếp theo đang ở trong hộp thư email của bạn.',
    emailLabel: 'Địa chỉ email',
    emailPlaceholder: 'you@university.edu',
    emailHint: 'Dùng địa chỉ được gắn với tài khoản campus của bạn.',
    sendResetLink: 'Gửi liên kết đặt lại',
    sendingResetInstructions: 'Đang gửi hướng dẫn đặt lại',
    sentToast: 'Nếu email tồn tại, một liên kết đặt lại đã được gửi.',
    failedToast: 'Hiện chưa thể bắt đầu khôi phục mật khẩu.',
    rateLimited: 'Có quá nhiều yêu cầu khôi phục. Vui lòng chờ rồi thử lại.',
    sentBanner:
      'Nếu có tài khoản khớp với {email}, một liên kết đặt lại đang được gửi đi.',
    sentDescription:
      'Hãy kiểm tra cả thư rác hoặc quảng cáo nếu chưa thấy thư ngay. Bạn cũng có thể bắt đầu lại với email khác.',
  },
  resetPassword: {
    eyebrow: 'Đặt lại mật khẩu',
    title: 'Tạo mật khẩu mới và quay lại CampusCore.',
    description:
      'Chọn mật khẩu mới cho tài khoản campus của bạn. Sau khi xong, bạn sẽ đăng nhập lại bằng thông tin vừa cập nhật.',
    featureTitles: ['Một đường đi an toàn', 'Yêu cầu rõ ràng', 'Khôi phục nhất quán'],
    featureDescriptions: [
      'Reset token quay lại cùng luồng đăng nhập được bảo vệ thay vì tách ra thành một trải nghiệm khác.',
      'Người dùng thấy yêu cầu mật khẩu và kiểm tra hợp lệ trước khi gửi form.',
      'Token hết hạn hoặc không hợp lệ vẫn hiển thị trạng thái khôi phục ổn định thay vì một trang lỗi.',
    ],
    invalidTitle: 'Liên kết đặt lại này không còn hợp lệ',
    invalidDescription:
      'Hãy yêu cầu một liên kết đặt lại mật khẩu mới và dùng email mới nhất để tiếp tục.',
    expiredTitle: 'Liên kết đặt lại đã hết hạn',
    expiredDescription: 'Hãy yêu cầu liên kết mới; mỗi liên kết chỉ có hiệu lực trong 30 phút.',
    attemptsExceededTitle: 'Liên kết đặt lại đã bị vô hiệu',
    attemptsExceededDescription: 'Đã có quá nhiều lần thử không hợp lệ. Hãy yêu cầu liên kết mới.',
    sectionEyebrow: 'Mật khẩu mới',
    heading: 'Đặt lại mật khẩu',
    subheading:
      'Hãy dùng mật khẩu bạn chưa dùng gần đây và giữ nó là duy nhất cho tài khoản campus của bạn.',
    newPassword: 'Mật khẩu mới',
    tokenLabel: 'Token đặt lại',
    tokenPlaceholder: 'Dán token từ email',
    confirmPassword: 'Xác nhận mật khẩu',
    newPasswordPlaceholder: 'Nhập mật khẩu mới',
    confirmPasswordPlaceholder: 'Xác nhận mật khẩu mới',
    minimumHint: 'Tối thiểu 8 ký tự.',
    savePassword: 'Đang lưu mật khẩu mới',
    resetPassword: 'Đặt lại mật khẩu',
    successToast: 'Đặt lại mật khẩu thành công',
    unavailableTitle: 'Dịch vụ đặt lại mật khẩu tạm thời chưa sẵn sàng',
    errors: {
      mismatch: 'Mật khẩu mới và phần xác nhận phải trùng nhau.',
      tooShort: 'Hãy chọn mật khẩu có ít nhất 8 ký tự.',
      fallback: 'Hiện chưa thể đặt lại mật khẩu.',
    },
  },
  adminShell: {
    eyebrow: 'Workspace quản trị',
    portalTitle: 'Cổng quản trị học vụ',
    identityLabel: 'Đăng nhập với vai trò',
    adminRole: 'Quản trị viên',
    superAdminRole: 'Quản trị viên cấp cao',
    academicYears: 'Năm học',
    preferences: 'Tùy chọn',
    skipToContent: 'Chuyển đến nội dung quản trị',
    sidebarNavigation: 'Điều hướng cổng quản trị',
    openSidebar: 'Mở điều hướng quản trị',
    closeSidebar: 'Đóng điều hướng quản trị',
    closeOverlay: 'Đóng lớp phủ điều hướng quản trị',
    menuSections: {
      overview: 'Tổng quan',
      people: 'Người dùng và vai trò',
      academics: 'Hồ sơ học vụ',
      campus: 'Dịch vụ nhà trường',
    },
    signOut: 'Đăng xuất',
    backToDashboard: 'Quay lại dashboard quản trị',
    mobileNavigation: 'Điều hướng quản trị trên mobile',
  },
  admin: {
    title: 'Dashboard quản trị',
    description:
      'Quản lý tài khoản, hồ sơ học vụ, thông báo và luồng luận văn cốt lõi trong một cổng thống nhất.',
    managementConsoleTitle: 'Bảng điều hướng quản trị',
    managementConsoleDescription:
      'Mở khu dữ liệu cần xử lý. Mọi route được giữ lại đều dùng cùng điều hướng, trạng thái và luồng xác nhận.',
    menuItems: [
      ['Quản lý luận văn', 'Quản lý đợt đăng ký, đề tài, nhóm và tiến độ.'],
      ['Quản lý người dùng', 'Xem tài khoản campus, trạng thái và phân quyền.'],
      ['Giảng viên', 'Quản lý hồ sơ giảng viên và dữ liệu sở hữu học vụ.'],
      ['Môn học', 'Duy trì danh mục, mã môn và metadata môn học.'],
      ['Lớp học phần', 'Theo dõi sức chứa, owner của section và phòng học đi kèm.'],
      ['Đăng ký học', 'Kiểm tra kết quả đăng ký và các tác vụ ở mức enrollment.'],
      ['Học kỳ', 'Kiểm soát dòng thời gian học vụ và cửa sổ đăng ký hiện tại.'],
      ['Bộ môn', 'Quản lý cấu trúc bộ môn và ánh xạ khoa.'],
      ['Phòng học', 'Theo dõi phòng, tòa nhà và mức sẵn sàng về sức chứa.'],
      ['Thông báo', 'Phát hành cập nhật ra toàn campus.'],
    ],
    stats: ['Sinh viên', 'Giảng viên', 'Môn học', 'Đăng ký học'],
    statDetails: [
      'Bản ghi con người có thể truy cập theo mô hình owner hiện tại.',
      'Tài khoản giảng viên đang hoạt động và danh tính phục vụ giảng dạy.',
      'Các dòng catalog sẵn sàng cho hoạch định section và đăng ký.',
      'Các dòng đăng ký hiện có trong những màn hồ sơ học vụ được giữ lại.',
    ],
    loading: 'Đang tải tổng quan campus',
    unavailableTitle: 'Tổng quan quản trị chưa sẵn sàng',
    unavailableDescription: 'Hiện chưa thể tải tổng quan campus.',
  },
  dashboardShell: {
    portalTitle: 'Cổng thông tin học vụ',
    identityLabel: 'Đăng nhập với vai trò',
    menuSections: {
      overview: 'Tổng quan',
      academic: 'Hồ sơ học vụ',
      teaching: 'Giảng dạy',
      campus: 'Dịch vụ nhà trường',
    },
    roles: {
      student: 'Truy cập sinh viên',
      lecturer: 'Truy cập giảng viên',
      admin: 'Truy cập quản trị',
    },
    roleDescription:
      'Giữ tác vụ kế tiếp trong tầm tay mà không làm mất ngữ cảnh xung quanh.',
    studentRail: {
      title: 'Ngữ cảnh sinh viên',
      subtitle:
        'Giữ lối đi hiện tại, tín hiệu học vụ và lối tắt đăng ký ở gần mà không làm chật khu làm việc chính.',
      currentViewLabel: 'Màn hiện tại',
      signalsTitle: 'Tín hiệu nhanh',
      notificationLabel: 'Cập nhật chưa đọc',
      localeLabel: 'Ngôn ngữ',
      sessionSummary:
        'Không gian sinh viên giữ cùng một ngữ cảnh đăng nhập khi bạn chuyển giữa đăng ký, lịch học và hồ sơ học tập.',
      quickActionsTitle: 'Lối tắt nhanh',
      collapse: 'Thu gọn cột ngữ cảnh sinh viên',
      expand: 'Mở rộng cột ngữ cảnh sinh viên',
      closeDrawer: 'Đóng cột ngữ cảnh sinh viên',
      quickLinks: {
        registration: {
          title: 'Kế hoạch đăng ký',
          description:
            'Xem section đang mở, danh sách chờ và cửa sổ đăng ký hiện tại.',
        },
        schedule: {
          title: 'Lịch học trong tuần',
          description:
            'Giữ lịch học kỳ này ở gần khi bạn so sánh các section.',
        },
        announcements: {
          title: 'Cập nhật campus',
          description:
            'Mở các thông báo chung ảnh hưởng đến lớp học và hoạt động sinh viên.',
        },
      },
    },
    menu: {
      dashboard: 'Dashboard',
      courseRegistration: 'Đăng ký học phần',
      myCourses: 'Môn học của tôi',
      schedule: 'Thời khóa biểu',
      grades: 'Điểm số',
      transcript: 'Bảng điểm',
      announcements: 'Thông báo',
      notifications: 'Trung tâm thông báo',
      thesis: 'Không gian luận văn',
      teachingSchedule: 'Lịch giảng dạy',
      gradeManagement: 'Quản lý điểm',
      profileSettings: 'Cài đặt hồ sơ',
      profile: 'Hồ sơ',
      settings: 'Cài đặt',
    },
    notifications: {
      title: 'Thông báo',
      loading: 'Đang tải các cảnh báo gần đây…',
      empty:
        'Hiện chưa có cảnh báo chưa đọc. Thông báo vẫn là kênh broadcast chính cho các cập nhật dùng chung.',
      fallbackTitle: 'Cập nhật mới',
      fallbackContent: 'Tài khoản của bạn vừa nhận một thông báo mới.',
      openAnnouncements: 'Mở trang thông báo',
      openNotifications: 'Mở trung tâm thông báo',
      description:
        'Giữ cảnh báo tài khoản, hạn học vụ và cập nhật quy trình trong một hộp thư tập trung.',
      refresh: 'Làm mới thông báo',
      markAllRead: 'Đánh dấu đã đọc tất cả',
      markRead: 'Đánh dấu đã đọc',
      unread: 'Chưa đọc',
      all: 'Tất cả thông báo',
      read: 'Đã đọc',
      noUnread: 'Bạn không có thông báo chưa đọc.',
      noAll: 'Hộp thư thông báo đang trống.',
      loadFailed: 'Hiện chưa thể tải thông báo.',
      updateFailed: 'Không thể cập nhật thông báo này. Hãy thử lại.',
      updatedCount: 'Đã đánh dấu {count} thông báo là đã đọc.',
    },
    controls: {
      openSidebar: 'Mở điều hướng sidebar',
      closeSidebar: 'Đóng điều hướng sidebar',
      collapseSidebar: 'Thu gọn điều hướng sidebar',
      expandSidebar: 'Mở rộng điều hướng sidebar',
      closeOverlay: 'Đóng lớp phủ sidebar',
      openStudentRail: 'Mở cột ngữ cảnh sinh viên',
      closeStudentRailOverlay: 'Đóng lớp phủ cột ngữ cảnh sinh viên',
      toggleNotifications: 'Bật tắt bảng thông báo',
      toggleProfile: 'Bật tắt menu hồ sơ',
      mobileNavigation: 'Điều hướng workspace trên mobile',
      skipToContent: 'Chuyển đến nội dung cổng thông tin',
      sidebarNavigation: 'Điều hướng cổng sinh viên và giảng viên',
      preferences: 'Tùy chọn',
    },
    pageDefaults: {
      description:
        'Đi giữa các bước hiện tại mà không rời khỏi không gian làm việc.',
      title: 'Không gian campus',
      fallbackDescription:
        'Di chuyển trong khu vực theo vai trò hiện tại với phiên đăng nhập nhất quán.',
    },
    signOutPage: {
      eyebrow: 'Kết thúc phiên',
      title: 'Đang đăng xuất',
      description:
        'Hệ thống đang đóng phiên làm việc hiện tại và đưa bạn trở lại màn hình đăng nhập.',
      progress: 'Đang kết thúc phiên làm việc...',
    },
    routeDescriptions: {
      dashboard:
        'Giữ đăng ký, môn học, lịch học và tác vụ hồ sơ trong cùng một không gian sinh viên.',
      profile:
        'Giữ thông tin liên hệ và vòng đời thông tin xác thực khớp với phiên trình duyệt đang hoạt động.',
      register:
        'Xem các section và quản lý quyết định enrollment cho học kỳ hiện tại.',
      enrollments:
        'Theo dõi các môn đang học và section gắn với từng môn.',
      schedule:
        'Giữ góc nhìn thời khóa biểu theo tuần trong tầm tay khi phần còn lại của portal vẫn sẵn sàng.',
      grades: 'Xem điểm đã công bố và trạng thái học tập hiện tại.',
      transcript: 'Xem lịch sử học tập tích lũy và kết quả theo học kỳ.',
      announcements: 'Đọc các cập nhật dùng chung trên toàn campus.',
      notifications:
        'Xem cảnh báo tài khoản, hạn học vụ và cập nhật quy trình trong một hộp thư tập trung.',
      lecturer:
        'Giữ tác vụ giảng dạy, hàng chờ chấm điểm, ngữ cảnh section và thông báo trong cùng một không gian giảng viên.',
      lecturerSchedule: 'Theo dõi section được giao, phòng học và khung giờ lên lớp.',
      lecturerGrades:
        'Xem hàng chờ chấm điểm, lọc theo học kỳ và đẩy các lớp học phần sẵn sàng sang bước công bố.',
      lecturerAnnouncements:
        'Chia sẻ cập nhật với sinh viên đang gắn với các section của bạn.',
      thesis:
        'Theo dõi đợt đăng ký đề tài, nhóm sinh viên và tiến độ luận văn.',
    },
    loading: 'Đang tải workspace',
  },
  studentDashboard: {
    eyebrow: 'Không gian sinh viên',
    title: 'Chào mừng quay lại, {name}',
    description:
      'Học kỳ hiện tại là {semester}. Di chuyển giữa đăng ký, môn học, lịch học và cập nhật hồ sơ mà không rời khỏi không gian sinh viên.',
    currentTermFallback: 'Chưa có học kỳ hoạt động',
    currentDateLabel: 'Hôm nay',
    metrics: {
      coursesInScope: 'Môn học trong phạm vi',
      confirmedEnrollments: 'Đăng ký đã xác nhận',
      pendingDecisions: 'Mục chờ xử lý',
      currentSemester: 'Học kỳ hiện tại',
      details: [
        'Đăng ký, ngữ cảnh section và coursework hiện tại luôn ở cùng một không gian sinh viên.',
        'Các section đã xác nhận luôn ở gần để bạn chuyển qua lịch học, điểm số và bảng điểm mà không mất ngữ cảnh.',
        'Những mục còn cần xử lý vẫn hiển thị sớm trước khi thành bất ngờ trong đợt đăng ký.',
        'Bảng điều khiển giữ một ngữ cảnh học vụ đang hoạt động để các công cụ còn lại luôn đồng bộ.',
      ],
    },
    panels: {
      nextActions: {
        title: 'Bước tiếp theo',
        description:
          'Mở các công cụ sinh viên thường cần xử lý đầu tiên trong học kỳ hiện tại.',
      },
      currentCourses: {
        title: 'Môn học hiện tại',
        description:
          'Các đăng ký đã xác nhận luôn nằm ở đây để bạn xem lại ngữ cảnh trước khi đi sâu hơn.',
        emptyTitle: 'Chưa có môn học đã xác nhận',
        emptyDescription:
          'Khi đăng ký được xác nhận, các môn hiện tại sẽ xuất hiện tại đây.',
        sectionLabel: 'Lớp học phần {section}',
      },
      referenceLinks: {
        title: 'Liên kết tham chiếu',
        description:
          'Giữ các màn hỗ trợ trong tầm tay mà vẫn ở trong cùng một không gian có phiên đăng nhập bảo vệ.',
      },
      currentStatus: {
        title: 'Trạng thái hiện tại',
        description:
          'Một góc nhìn nhanh về ngữ cảnh học vụ đang dùng và các việc còn cần chú ý.',
        semesterSelectionTitle: 'Lựa chọn học kỳ',
        semesterSelectionActive:
          'Dashboard đang dùng {semester} làm ngữ cảnh học vụ hiện tại.',
        semesterSelectionEmpty: 'Chưa có học kỳ ưu tiên nào đang hoạt động.',
        enrollmentHealthTitle: 'Tình trạng đăng ký',
        enrollmentHealthPending:
          'Vẫn còn {count} mục đăng ký cần được xử lý.',
        enrollmentHealthClear:
          'Không có vấn đề đăng ký nào đang chặn góc nhìn hiện tại.',
      },
    },
    quickActions: [
      ['Đăng ký học phần', 'Xem các section đang mở và đưa ra quyết định đăng ký.'],
      ['Mở thời khóa biểu', 'Kiểm tra lịch học của tuần này.'],
      ['Xem điểm số', 'Xem kết quả đã công bố và tình trạng học tập.'],
    ],
    portalLinks: [
      ['Môn học của tôi', 'Đăng ký hiện tại, chi tiết section và trạng thái.'],
      ['Bảng điểm', 'Lịch sử theo học kỳ và kết quả học tập tích lũy.'],
      ['Thông báo', 'Cập nhật chung từ trường và các nhóm học phần.'],
    ],
    errors: {
      loadFailed: 'Hiện chưa thể tải dữ liệu dashboard của bạn.',
      unavailableTitle: 'Dashboard chưa sẵn sàng',
      loading: 'Đang tải dashboard',
    },
  },
  profile: {
    eyebrow: 'Cài đặt tài khoản',
    title: 'Cài đặt hồ sơ',
    description:
      'Cập nhật thông tin cá nhân, giữ dữ liệu liên hệ mới nhất và xoay vòng thông tin xác thực mà không rời khỏi không gian làm việc.',
    profileTitle: 'Hồ sơ tài khoản',
    profileDescription:
      'Giữ hồ sơ tài khoản khớp với thông tin mà các nhóm campus đang dựa vào.',
    profileUpdated: 'Đã cập nhật hồ sơ',
    profileSaveFailed: 'Hiện chưa thể lưu thay đổi hồ sơ.',
    passwordTitle: 'Mật khẩu và an toàn phiên',
    passwordDescription:
      'Dùng mật khẩu mạnh và chờ đăng nhập lại sau khi đổi thành công.',
    passwordUpdated: 'Đã cập nhật mật khẩu',
    passwordUpdateFailed: 'Hiện chưa thể cập nhật mật khẩu.',
    whatChangesTitle: 'Những gì thay đổi ở đây',
    whatChanges: [
      'Chỉnh sửa hồ sơ sẽ cập nhật lại góc nhìn phiên trình duyệt sau khi lưu thành công.',
      'Đổi mật khẩu vẫn nằm trên cùng route đã xác thực và dùng chung auth contract.',
      'Các trường sở hữu nhạy cảm như email vẫn do service owner kiểm soát, không cho sửa trực tiếp trong form.',
    ],
    fields: {
      firstName: 'Tên',
      lastName: 'Họ',
      email: 'Email',
      phone: 'Số điện thoại',
      dateOfBirth: 'Ngày sinh',
      address: 'Địa chỉ',
      currentPassword: 'Mật khẩu hiện tại',
      newPassword: 'Mật khẩu mới',
      confirmNewPassword: 'Xác nhận mật khẩu mới',
      managedHint: 'Email do owner của tài khoản campus quản lý.',
      phonePlaceholder: '+66...',
      addressPlaceholder: 'Số nhà, thành phố, khu vực',
      currentPasswordPlaceholder: 'Nhập mật khẩu hiện tại',
      newPasswordPlaceholder: 'Chọn mật khẩu mới',
      confirmNewPasswordPlaceholder: 'Xác nhận mật khẩu mới',
      passwordHint: 'Tối thiểu 8 ký tự. Hãy dùng mật khẩu riêng cho tài khoản này.',
    },
    errors: {
      mismatch: 'Mật khẩu mới và phần xác nhận phải trùng nhau.',
      tooShort: 'Hãy chọn mật khẩu có ít nhất 8 ký tự.',
    },
    buttons: {
      savingChanges: 'Đang lưu thay đổi',
      updatingPassword: 'Đang cập nhật mật khẩu',
    },
  },
  lecturerDashboard: {
    eyebrow: 'Không gian giảng viên',
    title: 'Chào mừng quay lại, {name}',
    description:
      'Giữ công việc section, hàng chờ chấm điểm và cập nhật giảng dạy trong cùng một không gian giảng viên.',
    quickActionsTitle: 'Tác vụ nhanh',
    quickActionsDescription:
      'Mở các công cụ giảng viên thường dẫn đến tác vụ tiếp theo trong ngày.',
    gradingQueueTitle: 'Hàng chờ chấm điểm',
    gradingQueueDescription:
      'Các section gần bước rà soát cuối luôn ở đây để việc chấm điểm vẫn là ưu tiên chính.',
    gradingQueueEmptyTitle: 'Chưa có phân công chấm điểm',
    gradingQueueEmptyDescription:
      'Các section có trách nhiệm chấm điểm sẽ xuất hiện tại đây khi được kích hoạt.',
    sectionsInScopeTitle: 'Các section trong phạm vi',
    sectionsInScopeDescription:
      'Các section được giao luôn hiển thị cùng sức chứa và ngữ cảnh bộ môn trước khi bạn đi sâu vào lịch hoặc chấm điểm.',
    sectionsInScopeEmptyTitle: 'Chưa có phân công giảng dạy',
    sectionsInScopeEmptyDescription:
      'Các section được giao sẽ xuất hiện tại đây ngay khi học kỳ hiện tại được cấu hình.',
    announcementsTitle: 'Thông báo mới nhất',
    announcementsDescription:
      'Các cập nhật ảnh hưởng đến giảng dạy được đưa lên đây mà không làm bạn rời khỏi khối lượng công việc hiện tại.',
    announcementsEmptyTitle: 'Chưa có thông báo mới',
    announcementsEmptyDescription:
      'Các thông báo chung cho không gian giảng viên sẽ xuất hiện ở đây sau khi được phát hành.',
    quickLinks: [
      ['Lịch giảng dạy', 'Kiểm tra phòng học, lớp học phần và thời gian dạy của học kỳ hiện tại.'],
      ['Quản lý điểm', 'Xử lý hàng chờ chấm điểm và đưa các lớp học phần đã sẵn sàng sang bước công bố.'],
      ['Thông báo', 'Xem các cập nhật chung ảnh hưởng đến lớp học phần và ngày giảng dạy của bạn.'],
    ],
    metrics: {
      labels: ['Lớp học phần', 'Sinh viên', 'Sẵn sàng công bố', 'Thông báo mới'],
      details: [
        'Các lớp học phần giảng dạy luôn hiển thị để quyết định về lịch và chấm điểm vẫn bám theo đúng học kỳ.',
        'Khối lượng đăng ký luôn ở gần không gian giảng viên để việc theo dõi theo lớp học phần không bị mất.',
        'Công việc chấm điểm sẵn sàng công bố nổi lên sớm để không bị chìm giữa các việc khác.',
        'Các cập nhật broadcast ảnh hưởng đến giảng dạy vẫn hiện ra mà không kéo sự chú ý khỏi hàng chờ chấm điểm.',
      ],
    },
    queueStatusReady: 'Sẵn sàng công bố',
    queueStatusProgress: 'Đang xử lý',
    studentsSuffix: 'sinh viên',
    gradedSuffix: 'đã chấm',
    errors: {
      loadFailed: 'Hiện chưa thể tải dữ liệu học thuật của dashboard giảng viên.',
      unavailableTitle: 'Dashboard giảng viên chưa sẵn sàng',
      loading: 'Đang tải dashboard giảng viên',
    },
  },
  lecturerGrades: {
    eyebrow: 'Không gian giảng viên',
    title: 'Quản lý điểm',
    description:
      'Theo dõi tiến độ chấm điểm cho {semester}, rồi đưa các lớp học phần đã sẵn sàng sang bước rà soát cuối.',
    allSemesters: 'tất cả học kỳ',
    allSemestersOption: 'Tất cả học kỳ',
    selectSemester: 'Chọn học kỳ cho quản lý điểm',
    queueTitle: 'Hàng chờ quản lý điểm',
    queueDescription:
      'Lọc theo học kỳ, xem tiến độ từng lớp học phần và đi tiếp vào màn chi tiết để nhập điểm và công bố.',
    emptyTitle: 'Chưa có lớp học phần cần chấm điểm',
    emptyDescription:
      'Các section có trách nhiệm chấm điểm sẽ xuất hiện tại đây khi phân công giảng dạy đã sẵn sàng.',
    labels: {
      sections: 'Lớp học phần',
      gradesCaptured: 'Điểm đã nhập',
      readyToPublish: 'Sẵn sàng công bố',
      credits: 'tín chỉ',
      enrolled: 'đăng ký',
      graded: 'đã chấm',
      published: 'đã công bố',
      sectionPrefix: 'Lớp học phần',
      readyStatus: 'Sẵn sàng công bố',
      manageGrades: 'Quản lý điểm',
      enterGrades: 'Nhập điểm',
    },
    details: [
      'Các trách nhiệm chấm điểm đang hoạt động được gom vào cùng một hàng chờ theo học kỳ đã chọn.',
      'Các bản ghi điểm đã nhập luôn hiện ra trước khi bạn chuyển lớp học phần sang bước công bố cuối.',
      'Các lớp học phần có thể đi sang bước rà soát cuối được làm nổi bật mà không thay đổi quy trình chấm điểm.',
    ],
    errors: {
      loadFailed: 'Hiện chưa thể tải dữ liệu quản lý điểm.',
      unavailableTitle: 'Quản lý điểm chưa sẵn sàng',
      loading: 'Đang tải quản lý điểm',
    },
  },
  thesis: {
    eyebrow: 'Không gian luận văn',
    title: 'Giữ toàn bộ hành trình đề tài trong một timeline rõ ràng.',
    description:
      'Đợt đăng ký, lựa chọn đề tài, thành viên nhóm và tiến độ đã ghi nhận được nối với cùng một danh tính campus.',
    selectRound: 'Chọn đợt đăng ký đề tài',
    noRound: 'Chưa có đợt đăng ký đề tài nào.',
    loading: 'Đang tải không gian luận văn',
    loadFailed: 'Hiện chưa thể tải dữ liệu luận văn.',
    retry: 'Thử tải lại không gian luận văn',
    roundStatus: 'Trạng thái đợt',
    registrationWindow: 'Cửa sổ đăng ký',
    topics: 'Đề tài đã công bố',
    groups: 'Nhóm đề tài',
    topicsTitle: 'Tìm hướng đề tài phù hợp với nhóm.',
    topicsDescription:
      'Các đề tài đã công bố được gom theo bộ môn. Lựa chọn của nhóm sẽ trở thành đề xuất để điều phối viên xét duyệt.',
    groupsTitle: 'Không gian nhóm của bạn',
    groupsDescription:
      'Giữ thành viên, lựa chọn đề tài và trạng thái hiện tại của nhóm ở cùng một nơi.',
    createGroup: 'Tạo nhóm đề tài',
    addMember: 'Thêm thành viên',
    chooseTopic: 'Chọn đề tài',
    groupCreated: 'Đã tạo nhóm đề tài.',
    actionFailed: 'Không thể hoàn tất tác vụ này.',
    memberCount: '{count}/3 thành viên',
    noGroup: 'Bạn chưa có nhóm trong đợt này.',
    noGroupDescription:
      'Tạo nhóm khi đăng ký đang mở, sau đó mời thêm tối đa hai bạn cùng lớp.',
    noTopics: 'Chưa có đề tài được công bố.',
    noTopicsDescription:
      'Bộ môn sẽ công bố các hướng đề tài trước khi mở bước lựa chọn.',
    navigation: {
      catalog: 'Danh mục đề tài',
      progress: 'Tiến độ luận văn',
    },
    catalogTitle: 'Danh mục đề tài đã công bố',
    catalogDescription:
      'Xem nhanh các hướng đã công bố, sau đó mở chi tiết trước khi chọn đề tài cho nhóm.',
    topicDetailTitle: 'Chi tiết đề tài',
    topicDetailDescription:
      'Xem đầy đủ mô tả, số nhóm tối đa, bộ môn và trạng thái lựa chọn hiện tại.',
    progressTitle: 'Tiến độ luận văn',
    progressDescription:
      'Theo dõi trạng thái nhóm và đợt đăng ký hiện tại mà không tự suy diễn dữ liệu dịch vụ chưa ghi nhận.',
    progressCurrentStage: 'Giai đoạn hiện tại',
    progressGroupRequired: 'Hãy tạo hoặc tham gia nhóm để xem tiến độ gắn với nhóm.',
    progressSteps: [
      'Đã chọn đợt đăng ký',
      'Đã chuẩn bị nhóm luận văn',
      'Đã gửi đề xuất đề tài',
      'Đã ghi nhận quyết định điều phối',
      'Đã ghi nhận trạng thái tiến độ mới nhất',
    ],
    backToWorkspace: 'Quay lại không gian luận văn',
    lifecycleViewsTitle: 'Các màn hình trong vòng đời luận văn',
    lifecycleViewsDescription:
      'Mở màn hình tập trung cho đề tài đã công bố hoặc tiến độ nhóm mới nhất.',
    status: {
      DRAFT: 'Bản nháp',
      REGISTRATION_OPEN: 'Đang mở đăng ký',
      REGISTRATION_CLOSED: 'Đã đóng đăng ký',
      PROPOSALS_PUBLISHED: 'Đã công bố đề tài',
      CLOSED: 'Đã đóng',
      CANCELLED: 'Đã hủy',
      PENDING: 'Chờ xét duyệt',
      APPROVED: 'Đã duyệt',
      REJECTED: 'Cần chỉnh sửa',
      SUBMITTED: 'Đã gửi',
      COMPLETED: 'Đã hoàn thành',
    },
    progress: {
      eyebrow: 'Theo dõi tiến độ',
    },
    detail: {
      groupsTitle: 'Nhóm đã đăng ký',
      groupsDescription: 'Các nhóm do sinh viên thành lập trong đợt.',
      noGroups: 'Chưa có nhóm nào đăng ký.',
      noGroupsDescription: 'Nhóm sẽ xuất hiện khi sinh viên thành lập và đăng ký.',
      maxGroups: 'Số nhóm tối đa',
      members: 'thành viên',
    },
    admin: {
      title: 'Quản trị luận văn',
      description: 'Tạo đợt đăng ký, quản lý vòng đời đợt, và theo dõi đề tài cùng nhóm.',
      createRound: 'Tạo đợt',
      totalRounds: 'Tổng số đợt',
      openRounds: 'Đang mở đăng ký',
      noRounds: 'Chưa có đợt luận văn nào.',
      noRoundsDescription: 'Tạo đợt đăng ký đầu tiên để bắt đầu quy trình luận văn.',
      forbiddenTitle: 'Cần quyền quản trị',
      forbiddenDescription: 'Chỉ quản trị viên mới có thể quản lý các đợt đăng ký luận văn.',
      returnToWorkspace: 'Quay lại không gian của bạn',
      roundName: 'Tên đợt',
      roundNamePlaceholder: 'VD: Đợt luận văn Xuân 2026',
      thesisType: 'Loại luận văn',
      registrationStart: 'Bắt đầu đăng ký',
      registrationEnd: 'Kết thúc đăng ký',
      proposalPublishAt: 'Ngày công bố đề tài',
      createIncomplete: 'Vui lòng điền tên, thời gian bắt đầu và kết thúc đăng ký.',
      created: 'Đã tạo đợt luận văn thành công.',
      openRegistration: 'Mở đăng ký',
      closeRegistration: 'Đóng đăng ký',
      publishProposals: 'Công bố đề tài',
      registrationOpened: 'Đã mở đăng ký.',
      registrationClosed: 'Đã đóng đăng ký.',
      proposalsPublished: 'Đã công bố đề tài thành công.',
    },
  },
  assistant: {
    label: 'Trợ lý AI',
    title: 'Hướng dẫn luận văn',
    description: 'Hỏi về quy trình luận văn và ngữ cảnh đề tài đã được công bố.',
    open: 'Mở trợ lý luận văn',
    close: 'Đóng trợ lý luận văn',
    placeholder: 'Hỏi về đăng ký, đề tài, nhóm hoặc tiến độ...',
    send: 'Gửi tin nhắn',
    thinking: 'Đang kiểm tra ngữ cảnh luận văn được phép xem...',
    empty: 'Bắt đầu bằng câu hỏi về đợt luận văn hiện tại của bạn.',
    unavailable: 'Trợ lý hiện chưa sẵn sàng. Dữ liệu luận văn của bạn không bị thay đổi.',
    answered: 'Trả lời từ kho kiến thức đã duyệt',
    noMatch: 'Chưa có nguồn phù hợp',
    degraded: 'Kho kiến thức tạm thời gián đoạn',
    sources: 'Nguồn tham khảo',
    you: 'Bạn',
    history: 'Lịch sử hội thoại',
    backToChat: 'Quay lại chat',
    newConversation: 'Hội thoại mới',
    deleteConversation: 'Xóa hội thoại',
    deleteConversationConfirm: 'Xóa hội thoại này? Tin nhắn sẽ bị xóa vĩnh viễn.',
    historyLoading: 'Đang tải hội thoại của bạn…',
    historyUnavailable: 'Lịch sử hội thoại tạm thời chưa sẵn sàng.',
    historyEmpty: 'Chưa có hội thoại nào được lưu.',
    untitledConversation: 'Hội thoại luận văn',
    model: 'Mô hình',
    stop: 'Dừng tạo câu trả lời',
    retry: 'Thử lại',
    quotaExceeded: 'Bạn đã chạm giới hạn AI trong ngày. Hãy thử lại vào ngày mai hoặc tiếp tục với nguồn đã duyệt.',
    cancelled: 'Đã dừng tạo câu trả lời. Bạn có thể thử lại khi sẵn sàng.',
    feedbackUp: 'Đánh dấu câu trả lời hữu ích',
    feedbackDown: 'Đánh dấu câu trả lời chưa hữu ích',
    feedbackUnavailable: 'Hiện chưa thể lưu phản hồi của bạn. Hãy thử lại.',
    offline: 'Không có kết nối mạng. Hãy kiểm tra mạng rồi thử lại.',
    sessionExpired: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
    forbidden: 'Tài khoản hiện không được phép dùng trợ lý.',
  },
};

export const dictionaries = {
  en,
  vi,
} as const satisfies Record<Locale, I18nMessages>;

export function getMessages(locale: Locale) {
  return dictionaries[locale];
}
