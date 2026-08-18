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
      openAnalytics: 'Open analytics',
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
      loading: 'Loading...',
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
      title: 'Campus operations workspace',
      description:
        'CampusCore is a campus operations workspace for identity, academics, finance, engagement, people data, and analytics.',
      ogAlt: 'CampusCore workspace overview',
      twitterTitle: 'CampusCore',
      twitterDescription:
        'A campus operations workspace for academics, finance, engagement, analytics, and secure browser sessions.',
    },
    home: {
      title: 'Campus operations workspace',
      description:
        'CampusCore gives operators, lecturers, and students one steady workspace while keeping auth, academics, finance, analytics, and people workflows inside clear service boundaries.',
    },
    login: {
      title: 'Sign in',
      description: 'Sign in to CampusCore with your campus account.',
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
      title: 'Campus operations that stay calm under real load.',
      description:
        'Registration, teaching, billing, people data, and reporting in one workspace with clear service ownership.',
      badges: [
        'Role-aware workspace',
        'Verified releases',
        'Service-owned domains',
      ],
    },
  },
  home: {
    navSubtitle: 'Campus operations workspace',
    eyebrow: 'Campus operations workspace',
    title: 'Keep academic operations moving without the usual friction.',
    description:
      'CampusCore brings registration, schedules, billing, announcements, and reporting into one steady workspace for students, lecturers, and campus teams.',
    metricCards: [
      {
        title: 'Operationally ready',
        description:
          'The product is shaped for real campus operations, from day-to-day sign-in to release handoff.',
      },
      {
        title: 'Security-first',
        description:
          'Account protection and transaction safeguards stay in place without turning the interface into a status console.',
      },
      {
        title: 'Operational clarity',
        description:
          'The UI maps to clear owners instead of collapsing everything back into core.',
      },
    ],
    snapshotEyebrow: 'Operational readout',
    snapshotTitle: 'A calmer way to keep campus work moving',
    snapshotChecks: [
      'Students, lecturers, and admins start from one steady workspace',
      'Registration, billing, announcements, and reporting stay connected in the same session',
      'Each domain can change on its own release path without dragging the whole product',
      'Public access stays simple while internal service traffic remains separate',
    ],
    snapshotPrimaryAccessTitle: 'Primary access',
    snapshotPrimaryAccessDescription:
      'Students, lecturers, and admins all enter through one consistent sign-in experience.',
    snapshotReleaseTitle: 'Delivery posture',
    snapshotReleaseDescription:
      'Releases, verification, and runtime checks stay traceable from build to browser.',
    capabilitiesEyebrow: 'What the portal is built to do',
    capabilitiesTitle:
      'One frontend language across the critical campus workflows',
    capabilitiesDescription:
      'The interface is designed for live campus operations, with calmer defaults for auth, data states, and role-aware tasks.',
    pillars: [
      {
        title: 'Identity you can trust',
        description:
          'Account access stays steady across the people-facing workflows that matter during the academic day.',
      },
      {
        title: 'Academic workflows',
        description:
          'Registration, schedules, grades, transcript views, and section operations live behind clear service boundaries.',
      },
      {
        title: 'Operational visibility',
        description:
          'Dashboards and reporting move through analytics ownership instead of leaking through unrelated domains.',
      },
      {
        title: 'People ownership',
        description:
          'Student and lecturer records remain readable through the frontend without dragging the UI back into a monolith.',
      },
      {
        title: 'Release discipline',
        description:
          'Deployments, runtime checks, and service handoffs stay readable as the platform grows.',
      },
      {
        title: 'Campus-ready shell',
        description:
          'One portal for students, lecturers, and admins with sharper states, fewer dead ends, and calmer navigation.',
      },
    ],
    whyEyebrow: 'Why microservices for CampusCore',
    whyTitle: 'Split ownership where campus operations actually split',
    whyDescription:
      'CampusCore is split into services because campus work does not peak or fail in one place at a time. Identity, academics, finance, engagement, people data, and analytics move at different speeds and deserve different operating rhythms.',
    whyPoints: [
      {
        title: 'Smaller blast radius',
        description:
          'A grading issue should not take down finance, and a billing change should not block sign-in.',
      },
      {
        title: 'Independent auth ownership',
        description:
          'Identity and sign-in can evolve without forcing unrelated academic or finance changes.',
      },
      {
        title: 'Scaling by workload',
        description:
          'Analytics, announcements, and enrollment-heavy flows can scale without dragging the entire platform with them.',
      },
      {
        title: 'Release verification',
        description:
          'Clear service ownership makes it easier to trace what changed, what shipped, and what is running now.',
      },
      {
        title: 'Stronger edge boundaries',
        description:
          'People-facing routes stay clear while internal service contracts remain out of the browser path.',
      },
      {
        title: 'Clearer operator handoff',
        description:
          'Operators can reason about incidents and deployments one domain at a time instead of untangling one giant surface.',
      },
    ],
    footerSubtitle: 'Operational workspace',
    footerDescription:
      'A campus platform built for steady sign-in, clearer ownership, and calmer day-to-day operations.',
    footerWorkspace: 'Workspace',
    footerDelivery: 'Delivery',
    footerLinks: {
      workspace: ['Student access', 'Lecturer workflows', 'Admin operations'],
      delivery: ['Deployment handoff', 'Release verification', 'Operational monitoring'],
    },
    footerCopyright: 'All rights reserved.',
  },
  authShell: {
    desktopSubtitle: 'Campus operations workspace',
    mobileSubtitle: 'Academic access',
  },
  login: {
    eyebrow: 'Secure access',
    title: 'Sign in to the campus workspace.',
    description:
      'Sign in with your campus account to continue across registration, billing, schedules, and updates.',
    featureTitles: ['Role-aware access', 'Session protection', 'Operational continuity'],
    featureDescriptions: [
      'Admins, lecturers, and students land in the right workspace without a second sign-in step.',
      'Sign-in stays steady across the pages people actually need during the academic day.',
      'Registration, billing, and reporting all start from the same calm entry point.',
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
    },
    returnHomeLead: 'Need a different starting point?',
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
    sectionEyebrow: 'New password',
    heading: 'Reset password',
    subheading:
      'Use a password you have not used recently and keep it unique to your campus account.',
    newPassword: 'New password',
    confirmPassword: 'Confirm password',
    newPasswordPlaceholder: 'Enter a new password',
    confirmPasswordPlaceholder: 'Confirm the new password',
    minimumHint: 'Minimum 8 characters.',
    savePassword: 'Saving new password',
    resetPassword: 'Reset password',
    successToast: 'Password reset complete',
    errors: {
      mismatch: 'The new password and confirmation must match.',
      tooShort: 'Choose a password with at least 8 characters.',
      fallback: 'We could not reset your password.',
    },
  },
  adminShell: {
    eyebrow: 'Admin workspace',
    signOut: 'Sign out',
    backToDashboard: 'Back to admin dashboard',
  },
  admin: {
    title: 'Admin dashboard',
    description:
      'Keep the campus workspace aligned across people, academics, finance, and reporting without leaving the admin shell.',
    managementConsoleTitle: 'Management console',
    managementConsoleDescription:
      'Jump straight into the workspace that needs attention. Each area keeps the same admin shell, confirmation flow, and route grammar.',
    menuItems: [
      ['User management', 'Review campus accounts, statuses, and role assignments.'],
      ['Lecturers', 'Manage lecturer records and academic ownership data.'],
      ['Courses', 'Maintain catalog structure, codes, and course metadata.'],
      ['Sections', 'Watch capacity, section ownership, and classroom attachment.'],
      ['Enrollments', 'Inspect registration outcomes and enrollment-level actions.'],
      ['Semesters', 'Control the academic timeline and current registration window.'],
      ['Departments', 'Manage departmental structure and faculty mappings.'],
      ['Classrooms', 'Track rooms, buildings, and capacity readiness.'],
      ['Analytics', 'Review operational reporting and top-level data health.'],
      ['Invoices', 'Handle tuition invoicing, balances, and payment review.'],
      ['Announcements', 'Publish updates that flow out to the rest of the campus.'],
    ],
    stats: ['Students', 'Lecturers', 'Courses', 'Enrollments'],
    statDetails: [
      'People records reachable through the current ownership model.',
      'Active lecturer accounts and teaching-facing identities.',
      'Catalog rows available for section planning and registration.',
      'Enrollment rows reflected across academic and analytics views.',
    ],
    loading: 'Loading campus overview',
    unavailableTitle: 'Admin overview unavailable',
    unavailableDescription: 'Campus overview is not available right now.',
  },
  adminAnalytics: {
    title: 'Reports and analytics',
    description:
      'Track enrollment volume, grading distribution, and classroom utilization without leaving the same admin grammar used across live record management.',
    overviewSectionTitle: 'Overview stats',
    refreshData: 'Refresh data',
    loading: 'Loading analytics overview',
    unavailableTitle: 'Analytics unavailable',
    unavailableDescription: 'Analytics data could not be loaded right now.',
    stats: [
      'Students',
      'Lecturers',
      'Courses',
      'Sections',
      'Enrollments',
      'Departments',
      'Faculties',
      'Classrooms',
    ],
    statDetails: [
      'Active student records in the current analytics snapshot.',
      'Teaching-facing identities available to academic operations.',
      'Catalog rows currently feeding sections and registration.',
      'Live section records currently visible to reporting.',
      'Enrollment throughput reflected across academic reporting.',
      'Department records tied to faculty and staffing views.',
      'Faculty groupings available for academic segmentation.',
      'Rooms currently available for section occupancy tracking.',
    ],
    panels: {
      enrollmentsBySemester: {
        title: 'Enrollments by semester',
        description:
          'Compare academic terms by current enrollment volume without leaving the reporting workspace.',
        emptyTitle: 'No semester enrollment data yet',
        emptyDescription:
          'Once enrollment rows are available, this panel will show which academic terms are carrying the most volume.',
      },
      gradeDistribution: {
        title: 'Grade distribution',
        description:
          'Watch published grades settle across the current academic workload.',
        emptyTitle: 'No published grades yet',
        emptyDescription:
          'This panel becomes more useful as sections publish final grades.',
      },
      sectionOccupancy: {
        title: 'Section occupancy',
        description:
          'Surface the sections that are close to capacity before they become an operational problem.',
        emptyTitle: 'No section occupancy data',
        emptyDescription:
          'Section and enrollment counts need to be present before occupancy can be visualized.',
      },
      enrollmentTrends: {
        title: 'Enrollment trends',
        description:
          'Review monthly intake, completions, and drop activity in one operational readout.',
        emptyTitle: 'No recent trend data',
        emptyDescription:
          'Trend cards appear when monthly enrollment activity is available.',
      },
    },
    tableHeaders: {
      course: 'Course',
      section: 'Section',
      semester: 'Semester',
      capacity: 'Capacity',
      enrolled: 'Enrolled',
      occupancy: 'Occupancy',
      students: 'students',
      grades: {
        enrolled: 'Enrolled',
        completed: 'Completed',
        dropped: 'Dropped',
      },
    },
    cockpit: {
      kpis: {
        serviceHealth: 'Service health',
        serviceHealthDetail: 'Observed service targets in the local operator stack.',
        registrationPressure: 'Registration pressure',
        registrationPressureDetail: 'Full or near-full sections that need attention.',
        paymentRisk: 'Payment risk',
        paymentRiskDetail: 'Failed, overdue, or pending finance work.',
        notificationHealth: 'Notification health',
        notificationHealthDetail: 'Unread or warning-level operational messages.',
      },
      labels: {
        healthy: 'healthy',
        sectionsAtRisk: 'sections at risk',
        failedPayments: 'failed payments',
        unreadNotifications: 'unread',
        totalInvoiced: 'Invoiced',
        paidAmount: 'Paid',
        outstanding: 'Outstanding',
        waitlist: 'Waitlist',
        full: 'Full',
        nearCapacity: 'Near capacity',
        averageOccupancy: 'Average occupancy',
        noAttention: 'No immediate operator action is queued.',
      },
      panels: {
        enrollmentFlow: {
          title: 'Enrollment movement',
          description:
            'Twelve-month enrollment, completion, and drop activity with structured buckets for localized reporting.',
        },
        registrationPressure: {
          title: 'Registration pressure',
          description:
            'Sections closest to capacity, with active waitlist signals visible before students hit a dead end.',
        },
        financeFunnel: {
          title: 'Finance checkout posture',
          description:
            'Invoice exposure and provider payment activity in one reconciliation view.',
        },
        notificationDelivery: {
          title: 'Notification delivery',
          description:
            'Unread, warning, and error-level messages that may require an operator follow-up.',
        },
        operatorLinks: {
          title: 'Operator drill-down',
          description:
            'Use these internal links for detailed metrics, logs, traces, and alert review.',
        },
        actionQueue: {
          title: 'Attention queue',
          description:
            'The highest-value operational follow-ups from enrollment, finance, and notification signals.',
        },
      },
      actions: {
        open: 'Open',
        opensInNewTab: 'opens in a new tab',
      },
    },
  },
  dashboardShell: {
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
        'Keep the current route, billing signals, and registration shortcuts visible without crowding the main workspace.',
      currentViewLabel: 'Current view',
      signalsTitle: 'Signals',
      notificationLabel: 'Unread updates',
      localeLabel: 'Language',
      sessionSummary:
        'Your student workspace stays in the same signed-in context while you move between registration, billing, and records.',
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
        billing: {
          title: 'Billing status',
          description:
            'Check outstanding invoices and payment follow-up without leaving the workspace.',
        },
        announcements: {
          title: 'Campus updates',
          description:
            'Open the latest shared notices affecting classes, billing, and student activity.',
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
      invoices: 'Invoices',
      announcements: 'Announcements',
      thesis: 'Thesis workspace',
      teachingSchedule: 'Teaching schedule',
      gradeManagement: 'Grade management',
      profileSettings: 'Profile settings',
      profile: 'Profile',
      settings: 'Settings',
    },
    notifications: {
      title: 'Notifications',
      loading: 'Loading recent alerts...',
      empty:
        'No unread alerts right now. Announcements remain the main broadcast channel for shared updates.',
      fallbackTitle: 'New update',
      fallbackContent: 'A new notification has arrived for your account.',
      openAnnouncements: 'Open announcements',
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
        'Registration, coursework, billing, and profile tasks stay in one student workspace.',
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
      invoices: 'Review billing status and payment history.',
      announcements: 'Read campus-wide updates and shared notices.',
      lecturer:
        'Keep teaching tasks, grading queues, section context, and announcements in one lecturer workspace.',
      lecturerSchedule: 'Track assigned sections, rooms, and meeting windows.',
      lecturerGrades:
        'Review grading queues, filter by term, and move publish-ready sections forward.',
      lecturerAnnouncements:
        'Share updates with the students connected to your sections.',
      thesis:
        'Track thesis registration rounds, group decisions, defense councils, and published results.',
    },
    loading: 'Loading workspace',
  },
  studentDashboard: {
    eyebrow: 'Student workspace',
    title: 'Welcome back, {name}',
    description:
      'The current term is {semester}. Move between registration, coursework, billing, and profile updates without leaving the student workspace.',
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
      ['Check invoices', 'Keep track of outstanding balances and payment status.'],
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
      'Keep section operations, grading queues, and teaching updates in one lecturer-focused workspace.',
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
      'Broadcast updates that affect teaching operations surface here without taking the page away from the current workload.',
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
        'Broadcast updates that affect teaching operations remain visible without pulling attention away from the grading queue.',
      ],
    },
    queueStatusReady: 'Ready to publish',
    queueStatusProgress: 'In progress',
    studentsSuffix: 'students',
    gradedSuffix: 'graded',
    errors: {
      loadFailed: 'The lecturer dashboard could not load its operational data.',
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
      'Registration rounds, topic choices, group membership, council reviews, and final results stay connected to your campus identity.',
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
      'Keep membership and topic decisions visible before the round moves into council scheduling.',
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
    status: {
      DRAFT: 'Draft',
      REGISTRATION_OPEN: 'Registration open',
      REGISTRATION_CLOSED: 'Registration closed',
      PROPOSALS_PUBLISHED: 'Topics published',
      DEFENSE_SCHEDULED: 'Defense scheduled',
      SCORING_OPEN: 'Scoring open',
      RESULTS_PUBLISHED: 'Results published',
      CLOSED: 'Closed',
      CANCELLED: 'Cancelled',
      PENDING: 'Pending review',
      APPROVED: 'Approved',
      REJECTED: 'Needs changes',
      SUBMITTED: 'Submitted',
      COMPLETED: 'Completed',
    },
    review: {
      eyebrow: 'Review & evaluation',
      title: 'Score thesis defenses with a calm, structured workflow.',
      description:
        'Select a council, then submit scores and comments for each defending group. Scores range from 0 to 10.',
      selectCouncil: 'Select defense council',
      noCouncil: 'No defense council is available for this round.',
      noCouncilDescription:
        'Councils are scheduled by the coordinator once registration closes.',
      councilReview: 'Council review',
      room: 'Room',
      members: 'Council members',
      memberCount: 'members',
      groupsTitle: 'Defending groups',
      groupsDescription: 'Submit a score and comment for each group.',
      noGroups: 'No groups in this round yet.',
      noGroupsDescription: 'Groups will appear here once students form and register.',
      groupLabel: 'Group',
      score: 'Score (0-10)',
      comment: 'Comment',
      commentPlaceholder: 'Strengths, weaknesses, suggestions...',
      submit: 'Submit review',
      submitted: 'Review submitted successfully.',
      invalidScore: 'Please enter a valid score between 0 and 10.',
      unauthorized: 'Only lecturers and admins can submit reviews.',
      unauthorizedDescription: 'This workspace is restricted to evaluator roles.',
    },
    progress: {
      eyebrow: 'Progress tracking',
      title: 'Follow every milestone from proposal to result.',
      description:
        'See where your group stands in the thesis journey — proposal, approval, midterm, defense, and final result.',
      noRound: 'No thesis registration round is available yet.',
      noRoundDescription: 'Progress will appear once a round is published.',
      overall: 'Overall progress',
      timeline: 'Milestone timeline',
      timelineDescription: 'Each milestone unlocks as the previous one completes.',
      proposal: 'Proposal submitted',
      proposalDescription: 'Your group has formed and chosen a topic.',
      approval: 'Coordinator approval',
      approvalDescription: 'Your coordinator reviews and approves the group.',
      midterm: 'Midterm report',
      midtermDescription: 'Submit your progress report at the checkpoint.',
      defense: 'Defense',
      defenseDescription: 'Present and defend your thesis before the council.',
      result: 'Final result',
      resultDescription: 'Scores are finalized and published.',
    },
    detail: {
      councils: 'Defense councils',
      councilsTitle: 'Defense councils',
      councilsDescription: 'Councils scheduled for this round.',
      noCouncils: 'No councils scheduled yet.',
      noCouncilsDescription: 'Councils appear here once the coordinator schedules defense sessions.',
      groupsTitle: 'Registered groups',
      groupsDescription: 'Groups formed by students in this round.',
      noGroups: 'No groups registered yet.',
      noGroupsDescription: 'Groups will appear once students form and register.',
      maxGroups: 'Max groups',
      members: 'members',
    },
    councils: {
      eyebrow: 'Council management',
      title: 'Schedule and manage defense councils.',
      description:
        'Create councils, assign defense dates and rooms, and open scoring once the council is ready.',
      empty: 'No councils scheduled for this round.',
      emptyDescription: 'Councils appear here once the coordinator creates them.',
      schedule: 'Schedule',
      scheduleTitle: 'Schedule defense council',
      room: 'Room',
      roomPlaceholder: 'e.g. Room A101',
      date: 'Date & time',
      scheduledAt: 'Scheduled at',
      openScoring: 'Open scoring',
      scoringOpened: 'Scoring is now open for this council.',
      scheduled: 'Council scheduled successfully.',
    },
    admin: {
      title: 'Thesis administration',
      description: 'Create registration rounds, manage their lifecycle, and monitor topics, groups, and councils.',
      createRound: 'Create round',
      totalRounds: 'Total rounds',
      openRounds: 'Open for registration',
      noRounds: 'No thesis rounds yet.',
      noRoundsDescription: 'Create your first registration round to start the thesis workflow.',
      roundName: 'Round name',
      roundNamePlaceholder: 'e.g. Spring 2026 Thesis Round',
      thesisType: 'Thesis type',
      registrationStart: 'Registration start',
      registrationEnd: 'Registration end',
      proposalPublishAt: 'Proposal publish date',
      reportDate: 'Report date',
      defenseDate: 'Defense date',
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
    placeholder: 'Ask about registration, topics, groups, or reviews...',
    send: 'Send message',
    thinking: 'Checking the authorized thesis context...',
    empty: 'Start with a question about your current thesis round.',
    unavailable: 'The assistant is not available right now. Your thesis data is unchanged.',
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
      openAnalytics: 'Mở phân tích',
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
      loading: 'Đang tải...',
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
      title: 'Workspace vận hành campus',
      description:
        'CampusCore là workspace vận hành campus cho định danh, học vụ, tài chính, tương tác, dữ liệu con người và phân tích.',
      ogAlt: 'Tổng quan workspace CampusCore',
      twitterTitle: 'CampusCore',
      twitterDescription:
        'Workspace vận hành campus cho học vụ, tài chính, tương tác, phân tích và phiên trình duyệt an toàn.',
    },
    home: {
      title: 'Workspace vận hành campus',
      description:
        'CampusCore mang đến một workspace ổn định cho đội vận hành, giảng viên và sinh viên, đồng thời giữ ranh giới rõ ràng giữa auth, học vụ, tài chính, phân tích và dữ liệu con người.',
    },
    login: {
      title: 'Đăng nhập',
      description: 'Đăng nhập CampusCore bằng tài khoản campus của bạn.',
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
      title: 'Vận hành campus vẫn vững dưới tải thực tế.',
      description:
        'Đăng ký học phần, giảng dạy, học phí, dữ liệu con người và báo cáo trong một workspace có owner rõ ràng giữa các service.',
      badges: [
        'Workspace theo vai trò',
        'Phát hành đã xác minh',
        'Service có owner riêng',
      ],
    },
  },
  home: {
    navSubtitle: 'Không gian vận hành campus',
    eyebrow: 'Không gian vận hành campus',
    title: 'Giữ vận hành học vụ trôi chảy, rõ ràng và đáng tin cậy.',
    description:
      'CampusCore gom đăng ký học phần, thời khóa biểu, học phí, thông báo và báo cáo vào một không gian làm việc ổn định cho sinh viên, giảng viên và đội ngũ vận hành.',
    metricCards: [
      {
        title: 'Sẵn sàng cho vận hành',
        description:
          'Sản phẩm được dựng cho vận hành campus thực tế, từ đăng nhập hằng ngày đến bàn giao phát hành.',
      },
      {
        title: 'Ưu tiên bảo mật',
        description:
          'Các lớp bảo vệ tài khoản và giao dịch vẫn được giữ vững mà không biến giao diện thành bảng chẩn đoán kỹ thuật.',
      },
      {
        title: 'Vận hành rõ ràng',
        description:
          'UI bám theo owner rõ ràng thay vì dồn tất cả trở lại một lõi duy nhất.',
      },
    ],
    snapshotEyebrow: 'Nhịp vận hành',
    snapshotTitle: 'Một cách bình tĩnh hơn để giữ campus luôn chạy',
    snapshotChecks: [
      'Sinh viên, giảng viên và quản trị cùng đi vào một workspace ổn định',
      'Đăng ký học phần, học phí, thông báo và báo cáo luôn nối tiếp trong cùng một phiên',
      'Mỗi domain có thể đổi theo nhịp riêng mà không kéo cả hệ thống đi theo',
      'Lối vào công khai vẫn gọn cho người dùng, còn lưu lượng nội bộ được giữ tách riêng',
    ],
    snapshotPrimaryAccessTitle: 'Lối vào chính',
    snapshotPrimaryAccessDescription:
      'Sinh viên, giảng viên và quản trị cùng đi qua một trải nghiệm đăng nhập nhất quán.',
    snapshotReleaseTitle: 'Nhịp triển khai',
    snapshotReleaseDescription:
      'Các bước phát hành, kiểm chứng và kiểm tra runtime luôn lần theo được từ build đến trình duyệt.',
    capabilitiesEyebrow: 'Portal này được xây để làm gì',
    capabilitiesTitle: 'Một ngôn ngữ giao diện cho các luồng campus quan trọng',
    capabilitiesDescription:
      'Giao diện được thiết kế cho vận hành campus thực tế, với mặc định điềm tĩnh hơn cho xác thực, trạng thái dữ liệu và tác vụ theo vai trò.',
    pillars: [
      {
        title: 'Định danh đáng tin',
        description:
          'Đăng nhập được giữ ổn định trên những luồng mà người dùng thật sự cần trong ngày học vụ.',
      },
      {
        title: 'Luồng học vụ',
        description:
          'Đăng ký, thời khóa biểu, điểm, bảng điểm và vận hành section nằm sau các ranh giới dịch vụ rõ ràng.',
      },
      {
        title: 'Quan sát vận hành',
        description:
          'Dashboard và báo cáo đi theo owner analytics thay vì rò rỉ qua các domain không liên quan.',
      },
      {
        title: 'Owner dữ liệu con người',
        description:
          'Dữ liệu sinh viên và giảng viên vẫn hiển thị trên frontend mà không kéo UI quay lại kiểu monolith.',
      },
      {
        title: 'Kỷ luật phát hành',
        description:
          'Triển khai, kiểm tra runtime và bàn giao service vẫn rõ ràng khi hệ thống lớn dần.',
      },
      {
        title: 'Portal sẵn cho campus',
        description:
          'Một cổng chung cho sinh viên, giảng viên và quản trị với trạng thái rõ hơn, ít ngõ cụt hơn và điều hướng điềm tĩnh hơn.',
      },
    ],
    whyEyebrow: 'Vì sao CampusCore chọn microservices',
    whyTitle: 'Tách owner đúng nơi vận hành campus thực sự đã tách',
    whyDescription:
      'CampusCore được tách thành microservices vì vận hành campus không tăng tải hay gặp sự cố ở một chỗ duy nhất. Định danh, học vụ, tài chính, tương tác, dữ liệu con người và phân tích đều có nhịp vận hành riêng.',
    whyPoints: [
      {
        title: 'Giảm blast radius',
        description:
          'Một lỗi nhập điểm không nên kéo tài chính xuống, và thay đổi billing không nên chặn đăng nhập.',
      },
      {
        title: 'Auth có owner độc lập',
        description:
          'Định danh và đăng nhập có thể thay đổi mà không buộc học vụ hay tài chính phải đi theo.',
      },
      {
        title: 'Scale theo tải',
        description:
          'Analytics, announcements và luồng enrollment có thể scale riêng mà không kéo cả hệ thống theo.',
      },
      {
        title: 'Kiểm chứng phát hành rõ',
        description:
          'Owner rõ ràng giúp truy dấu phần nào vừa đổi, vừa phát hành và đang chạy dễ hơn.',
      },
      {
        title: 'Ranh giới edge mạnh hơn',
        description:
          'Lối vào cho người dùng vẫn gọn, còn contract nội bộ không bị đẩy ra trước trình duyệt.',
      },
      {
        title: 'Bàn giao vận hành rõ hơn',
        description:
          'Đội vận hành có thể xử lý sự cố và triển khai theo từng domain thay vì gỡ một khối khổng lồ.',
      },
    ],
    footerSubtitle: 'Workspace vận hành',
    footerDescription:
      'Nền tảng campus tập trung vào đăng nhập ổn định, owner rõ ràng và vận hành hằng ngày bớt rối hơn.',
    footerWorkspace: 'Workspace',
    footerDelivery: 'Triển khai',
    footerLinks: {
      workspace: ['Khu sinh viên', 'Luồng giảng viên', 'Vận hành quản trị'],
      delivery: ['Bàn giao triển khai', 'Xác minh phát hành', 'Theo dõi vận hành'],
    },
    footerCopyright: 'Mọi quyền được bảo lưu.',
  },
  authShell: {
    desktopSubtitle: 'Không gian vận hành campus',
    mobileSubtitle: 'Truy cập học vụ',
  },
  login: {
    eyebrow: 'Truy cập an toàn',
    title: 'Đăng nhập vào không gian vận hành campus.',
    description:
      'Dùng cùng một phiên trình duyệt đã được bảo vệ để di chuyển giữa học vụ, tài chính, thông báo và bảng điều khiển vận hành.',
    featureTitles: ['Vào đúng vai trò', 'Phiên đăng nhập an toàn', 'Luồng vận hành liền mạch'],
    featureDescriptions: [
      'Quản trị, giảng viên và sinh viên đi vào đúng không gian làm việc mà không cần một bước đăng nhập thứ hai.',
      'Đăng nhập được giữ nhất quán trên những màn quan trọng nhất trong ngày học vụ.',
      'Các luồng học vụ, tài chính, tương tác và báo cáo luôn đi qua cùng một điểm vào điềm tĩnh.',
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
    },
    returnHomeLead: 'Cần một điểm vào khác?',
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
    sectionEyebrow: 'Mật khẩu mới',
    heading: 'Đặt lại mật khẩu',
    subheading:
      'Hãy dùng mật khẩu bạn chưa dùng gần đây và giữ nó là duy nhất cho tài khoản campus của bạn.',
    newPassword: 'Mật khẩu mới',
    confirmPassword: 'Xác nhận mật khẩu',
    newPasswordPlaceholder: 'Nhập mật khẩu mới',
    confirmPasswordPlaceholder: 'Xác nhận mật khẩu mới',
    minimumHint: 'Tối thiểu 8 ký tự.',
    savePassword: 'Đang lưu mật khẩu mới',
    resetPassword: 'Đặt lại mật khẩu',
    successToast: 'Đặt lại mật khẩu thành công',
    errors: {
      mismatch: 'Mật khẩu mới và phần xác nhận phải trùng nhau.',
      tooShort: 'Hãy chọn mật khẩu có ít nhất 8 ký tự.',
      fallback: 'Hiện chưa thể đặt lại mật khẩu.',
    },
  },
  adminShell: {
    eyebrow: 'Workspace quản trị',
    signOut: 'Đăng xuất',
    backToDashboard: 'Quay lại dashboard quản trị',
  },
  admin: {
    title: 'Dashboard quản trị',
    description:
      'Giữ workspace campus nhất quán giữa dữ liệu con người, học vụ, tài chính và báo cáo mà không rời khỏi admin shell.',
    managementConsoleTitle: 'Bảng điều hướng quản trị',
    managementConsoleDescription:
      'Đi thẳng vào khu cần xử lý. Mỗi khu vẫn dùng cùng admin shell, confirmation flow và route grammar.',
    menuItems: [
      ['Quản lý người dùng', 'Xem tài khoản campus, trạng thái và phân quyền.'],
      ['Giảng viên', 'Quản lý hồ sơ giảng viên và dữ liệu sở hữu học vụ.'],
      ['Môn học', 'Duy trì danh mục, mã môn và metadata môn học.'],
      ['Lớp học phần', 'Theo dõi sức chứa, owner của section và phòng học đi kèm.'],
      ['Đăng ký học', 'Kiểm tra kết quả đăng ký và các tác vụ ở mức enrollment.'],
      ['Học kỳ', 'Kiểm soát dòng thời gian học vụ và cửa sổ đăng ký hiện tại.'],
      ['Bộ môn', 'Quản lý cấu trúc bộ môn và ánh xạ khoa.'],
      ['Phòng học', 'Theo dõi phòng, tòa nhà và mức sẵn sàng về sức chứa.'],
      ['Phân tích', 'Xem báo cáo vận hành và sức khỏe dữ liệu ở mức tổng quan.'],
      ['Hóa đơn', 'Xử lý học phí, số dư và đối soát thanh toán.'],
      ['Thông báo', 'Phát hành cập nhật ra toàn campus.'],
    ],
    stats: ['Sinh viên', 'Giảng viên', 'Môn học', 'Đăng ký học'],
    statDetails: [
      'Bản ghi con người có thể truy cập theo mô hình owner hiện tại.',
      'Tài khoản giảng viên đang hoạt động và danh tính phục vụ giảng dạy.',
      'Các dòng catalog sẵn sàng cho hoạch định section và đăng ký.',
      'Các dòng enrollment đã phản ánh vào góc nhìn học vụ và analytics.',
    ],
    loading: 'Đang tải tổng quan campus',
    unavailableTitle: 'Tổng quan quản trị chưa sẵn sàng',
    unavailableDescription: 'Hiện chưa thể tải tổng quan campus.',
  },
  adminAnalytics: {
    title: 'Báo cáo và phân tích',
    description:
      'Theo dõi lưu lượng enrollment, phân bố điểm và sử dụng phòng học mà vẫn ở trong cùng admin grammar dùng cho quản trị dữ liệu sống.',
    overviewSectionTitle: 'Chỉ số tổng quan',
    refreshData: 'Làm mới dữ liệu',
    loading: 'Đang tải tổng quan phân tích',
    unavailableTitle: 'Phân tích chưa sẵn sàng',
    unavailableDescription: 'Hiện chưa thể tải dữ liệu phân tích.',
    stats: [
      'Sinh viên',
      'Giảng viên',
      'Môn học',
      'Lớp học phần',
      'Đăng ký học',
      'Bộ môn',
      'Khoa',
      'Phòng học',
    ],
    statDetails: [
      'Bản ghi sinh viên đang hoạt động trong snapshot analytics hiện tại.',
      'Danh tính hướng giảng dạy hiện sẵn sàng cho vận hành học vụ.',
      'Các dòng catalog đang cấp dữ liệu cho lớp học phần và đăng ký.',
      'Các bản ghi lớp học phần trực tiếp hiện hiển thị trong báo cáo.',
      'Lưu lượng đăng ký học đang được phản ánh xuyên suốt các báo cáo học vụ.',
      'Bản ghi bộ môn gắn với góc nhìn khoa và nhân sự.',
      'Các nhóm khoa đang sẵn sàng cho phân đoạn học vụ.',
      'Các phòng hiện sẵn sàng cho việc theo dõi mức đầy của section.',
    ],
    panels: {
      enrollmentsBySemester: {
        title: 'Đăng ký theo học kỳ',
        description:
          'So sánh các học kỳ theo lưu lượng đăng ký hiện tại mà không rời màn hình báo cáo.',
        emptyTitle: 'Chưa có dữ liệu đăng ký theo học kỳ',
        emptyDescription:
          'Khi có dữ liệu enrollment, khối này sẽ cho biết học kỳ nào đang mang nhiều lưu lượng nhất.',
      },
      gradeDistribution: {
        title: 'Phân bố điểm',
        description:
          'Theo dõi cách điểm số đã công bố đang phân bổ theo khối lượng học vụ hiện tại.',
        emptyTitle: 'Chưa có điểm đã công bố',
        emptyDescription:
          'Khối này sẽ hữu ích hơn khi các lớp học phần bắt đầu công bố điểm cuối kỳ.',
      },
      sectionOccupancy: {
        title: 'Mức đầy của lớp học phần',
        description:
          'Đưa các lớp học phần gần đầy lên sớm trước khi chúng trở thành vấn đề vận hành.',
        emptyTitle: 'Chưa có dữ liệu mức đầy',
        emptyDescription:
          'Cần có số lượng lớp học phần và đăng ký học trước khi hiển thị được mức đầy.',
      },
      enrollmentTrends: {
        title: 'Xu hướng đăng ký học',
        description:
          'Xem lượt vào học, hoàn tất và rút môn theo tháng trong một màn đọc vận hành thống nhất.',
        emptyTitle: 'Chưa có dữ liệu xu hướng gần đây',
        emptyDescription:
          'Các thẻ xu hướng sẽ xuất hiện khi có hoạt động đăng ký học theo tháng.',
      },
    },
    tableHeaders: {
      course: 'Môn học',
      section: 'Lớp học phần',
      semester: 'Học kỳ',
      capacity: 'Sức chứa',
      enrolled: 'Đã đăng ký',
      occupancy: 'Mức đầy',
      students: 'sinh viên',
      grades: {
        enrolled: 'Đăng ký',
        completed: 'Hoàn tất',
        dropped: 'Rút môn',
      },
    },
    cockpit: {
      kpis: {
        serviceHealth: 'Sức khỏe service',
        serviceHealthDetail: 'Các service target đang được operator stack theo dõi.',
        registrationPressure: 'Áp lực đăng ký',
        registrationPressureDetail: 'Lớp đã đầy hoặc gần đầy cần được chú ý.',
        paymentRisk: 'Rủi ro thanh toán',
        paymentRiskDetail: 'Thanh toán lỗi, quá hạn hoặc còn chờ xử lý.',
        notificationHealth: 'Tình trạng thông báo',
        notificationHealthDetail: 'Thông báo chưa đọc hoặc cảnh báo cần theo dõi.',
      },
      labels: {
        healthy: 'ổn định',
        sectionsAtRisk: 'lớp có rủi ro',
        failedPayments: 'thanh toán lỗi',
        unreadNotifications: 'chưa đọc',
        totalInvoiced: 'Đã lập hóa đơn',
        paidAmount: 'Đã thanh toán',
        outstanding: 'Còn phải thu',
        waitlist: 'Danh sách chờ',
        full: 'Đã đầy',
        nearCapacity: 'Gần đầy',
        averageOccupancy: 'Mức đầy trung bình',
        noAttention: 'Hiện chưa có tác vụ vận hành cần xử lý ngay.',
      },
      panels: {
        enrollmentFlow: {
          title: 'Dòng đăng ký học',
          description:
            'Hoạt động đăng ký, hoàn tất và rút môn trong 12 tháng với bucket có cấu trúc cho báo cáo song ngữ.',
        },
        registrationPressure: {
          title: 'Áp lực đăng ký',
          description:
            'Các lớp gần đạt sức chứa nhất, kèm tín hiệu danh sách chờ trước khi sinh viên gặp ngõ cụt.',
        },
        financeFunnel: {
          title: 'Tình trạng checkout học phí',
          description:
            'Mức phải thu và hoạt động thanh toán theo provider trong một góc nhìn đối soát.',
        },
        notificationDelivery: {
          title: 'Luồng thông báo',
          description:
            'Thông báo chưa đọc, cảnh báo và lỗi có thể cần quản trị viên theo dõi.',
        },
        operatorLinks: {
          title: 'Đi sâu vận hành',
          description:
            'Dùng các liên kết nội bộ này để xem metric, log, trace và alert chi tiết.',
        },
        actionQueue: {
          title: 'Hàng đợi cần chú ý',
          description:
            'Các việc vận hành đáng xử lý nhất từ tín hiệu đăng ký, tài chính và thông báo.',
        },
      },
      actions: {
        open: 'Mở',
        opensInNewTab: 'mở trong tab mới',
      },
    },
  },
  dashboardShell: {
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
        'Giữ lối đi hiện tại, tín hiệu học phí và lối tắt đăng ký ở gần mà không làm chật khu làm việc chính.',
      currentViewLabel: 'Màn hiện tại',
      signalsTitle: 'Tín hiệu nhanh',
      notificationLabel: 'Cập nhật chưa đọc',
      localeLabel: 'Ngôn ngữ',
      sessionSummary:
        'Không gian sinh viên giữ cùng một ngữ cảnh đăng nhập khi bạn chuyển giữa đăng ký, hóa đơn và hồ sơ học tập.',
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
        billing: {
          title: 'Tình trạng học phí',
          description:
            'Kiểm tra số dư và việc cần theo dõi về thanh toán mà không rời khỏi workspace.',
        },
        announcements: {
          title: 'Cập nhật campus',
          description:
            'Mở các thông báo chung ảnh hưởng đến lớp học, học phí và hoạt động sinh viên.',
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
      invoices: 'Hóa đơn',
      announcements: 'Thông báo',
      thesis: 'Không gian luận văn',
      teachingSchedule: 'Lịch giảng dạy',
      gradeManagement: 'Quản lý điểm',
      profileSettings: 'Cài đặt hồ sơ',
      profile: 'Hồ sơ',
      settings: 'Cài đặt',
    },
    notifications: {
      title: 'Thông báo',
      loading: 'Đang tải các cảnh báo gần đây...',
      empty:
        'Hiện chưa có cảnh báo chưa đọc. Thông báo vẫn là kênh broadcast chính cho các cập nhật dùng chung.',
      fallbackTitle: 'Cập nhật mới',
      fallbackContent: 'Tài khoản của bạn vừa nhận một thông báo mới.',
      openAnnouncements: 'Mở trang thông báo',
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
        'Giữ đăng ký, môn học, hóa đơn và tác vụ hồ sơ trong cùng một không gian sinh viên.',
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
      invoices: 'Xem tình trạng hóa đơn và lịch sử thanh toán.',
      announcements: 'Đọc các cập nhật dùng chung trên toàn campus.',
      lecturer:
        'Giữ tác vụ giảng dạy, hàng chờ chấm điểm, ngữ cảnh section và thông báo trong cùng một không gian giảng viên.',
      lecturerSchedule: 'Theo dõi section được giao, phòng học và khung giờ lên lớp.',
      lecturerGrades:
        'Xem hàng chờ chấm điểm, lọc theo học kỳ và đẩy các lớp học phần sẵn sàng sang bước công bố.',
      lecturerAnnouncements:
        'Chia sẻ cập nhật với sinh viên đang gắn với các section của bạn.',
      thesis:
        'Theo dõi đợt đăng ký đề tài, nhóm sinh viên, hội đồng phản biện và kết quả đã công bố.',
    },
    loading: 'Đang tải workspace',
  },
  studentDashboard: {
    eyebrow: 'Không gian sinh viên',
    title: 'Chào mừng quay lại, {name}',
    description:
      'Học kỳ hiện tại là {semester}. Di chuyển giữa đăng ký, môn học, hóa đơn và cập nhật hồ sơ mà không rời khỏi không gian sinh viên.',
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
      ['Kiểm tra hóa đơn', 'Theo dõi số dư chưa thanh toán và trạng thái thanh toán.'],
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
      'Giữ vận hành section, hàng chờ chấm điểm và cập nhật giảng dạy trong cùng một không gian giảng viên.',
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
      loadFailed: 'Hiện chưa thể tải dữ liệu vận hành của dashboard giảng viên.',
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
      'Đợt đăng ký, lựa chọn đề tài, thành viên nhóm, hội đồng phản biện và kết quả cuối cùng được nối với cùng một danh tính campus.',
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
      'Giữ thành viên và lựa chọn đề tài ở gần nhau trước khi đợt chuyển sang bước lập hội đồng.',
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
    status: {
      DRAFT: 'Bản nháp',
      REGISTRATION_OPEN: 'Đang mở đăng ký',
      REGISTRATION_CLOSED: 'Đã đóng đăng ký',
      PROPOSALS_PUBLISHED: 'Đã công bố đề tài',
      DEFENSE_SCHEDULED: 'Đã xếp lịch bảo vệ',
      SCORING_OPEN: 'Đang chấm điểm',
      RESULTS_PUBLISHED: 'Đã công bố kết quả',
      CLOSED: 'Đã đóng',
      CANCELLED: 'Đã hủy',
      PENDING: 'Chờ xét duyệt',
      APPROVED: 'Đã duyệt',
      REJECTED: 'Cần chỉnh sửa',
      SUBMITTED: 'Đã gửi',
      COMPLETED: 'Đã hoàn thành',
    },
    review: {
      eyebrow: 'Chấm điểm & phản biện',
      title: 'Chấm điểm bảo vệ luận văn bằng quy trình rõ ràng, tinh tế.',
      description:
        'Chọn hội đồng, sau đó gửi điểm và nhận xét cho từng nhóm bảo vệ. Thang điểm từ 0 đến 10.',
      selectCouncil: 'Chọn hội đồng bảo vệ',
      noCouncil: 'Chưa có hội đồng bảo vệ nào trong đợt này.',
      noCouncilDescription:
        'Hội đồng được điều phối viên lập lịch sau khi đóng đăng ký.',
      councilReview: 'Chấm điểm hội đồng',
      room: 'Phòng',
      members: 'Thành viên hội đồng',
      memberCount: 'thành viên',
      groupsTitle: 'Nhóm bảo vệ',
      groupsDescription: 'Gửi điểm và nhận xét cho từng nhóm.',
      noGroups: 'Chưa có nhóm nào trong đợt này.',
      noGroupsDescription: 'Các nhóm sẽ xuất hiện tại đây sau khi sinh viên thành lập và đăng ký.',
      groupLabel: 'Nhóm',
      score: 'Điểm (0-10)',
      comment: 'Nhận xét',
      commentPlaceholder: 'Điểm mạnh, điểm yếu, gợi ý...',
      submit: 'Gửi đánh giá',
      submitted: 'Đã gửi đánh giá thành công.',
      invalidScore: 'Vui lòng nhập điểm hợp lệ từ 0 đến 10.',
      unauthorized: 'Chỉ giảng viên và quản trị viên mới được chấm điểm.',
      unauthorizedDescription: 'Không gian này chỉ dành cho vai trò người đánh giá.',
    },
    progress: {
      eyebrow: 'Theo dõi tiến độ',
      title: 'Theo dõi từng mốc từ đề xuất đến kết quả.',
      description:
        'Xem nhóm đang ở đâu trong hành trình luận văn — đề xuất, phê duyệt, báo cáo giữa kỳ, bảo vệ và kết quả cuối cùng.',
      noRound: 'Chưa có đợt đăng ký đề tài nào.',
      noRoundDescription: 'Tiến độ sẽ xuất hiện khi đợt đăng ký được công bố.',
      overall: 'Tiến độ tổng',
      timeline: 'Timeline các mốc',
      timelineDescription: 'Mỗi mốc được mở khóa khi mốc trước hoàn thành.',
      proposal: 'Đã gửi đề xuất',
      proposalDescription: 'Nhóm đã thành lập và chọn đề tài.',
      approval: 'Điều phối viên phê duyệt',
      approvalDescription: 'Điều phối viên xét duyệt và phê duyệt nhóm.',
      midterm: 'Báo cáo giữa kỳ',
      midtermDescription: 'Nộp báo cáo tiến độ tại mốc kiểm tra.',
      defense: 'Bảo vệ',
      defenseDescription: 'Trình bày và bảo vệ luận văn trước hội đồng.',
      result: 'Kết quả cuối cùng',
      resultDescription: 'Điểm được chốt và công bố.',
    },
    detail: {
      councils: 'Hội đồng bảo vệ',
      councilsTitle: 'Hội đồng bảo vệ',
      councilsDescription: 'Các hội đồng được lập lịch cho đợt này.',
      noCouncils: 'Chưa có hội đồng nào được lập lịch.',
      noCouncilsDescription: 'Hội đồng sẽ xuất hiện khi điều phối viên lập lịch bảo vệ.',
      groupsTitle: 'Nhóm đã đăng ký',
      groupsDescription: 'Các nhóm do sinh viên thành lập trong đợt.',
      noGroups: 'Chưa có nhóm nào đăng ký.',
      noGroupsDescription: 'Nhóm sẽ xuất hiện khi sinh viên thành lập và đăng ký.',
      maxGroups: 'Số nhóm tối đa',
      members: 'thành viên',
    },
    councils: {
      eyebrow: 'Quản lý hội đồng',
      title: 'Lập lịch và quản lý hội đồng bảo vệ.',
      description:
        'Tạo hội đồng, gán ngày giờ và phòng bảo vệ, và mở chấm điểm khi hội đồng sẵn sàng.',
      empty: 'Chưa có hội đồng nào được lập lịch cho đợt này.',
      emptyDescription: 'Hội đồng sẽ xuất hiện khi điều phối viên tạo.',
      schedule: 'Lập lịch',
      scheduleTitle: 'Lập lịch hội đồng bảo vệ',
      room: 'Phòng',
      roomPlaceholder: 'VD: Phòng A101',
      date: 'Ngày giờ',
      scheduledAt: 'Đã lịch',
      openScoring: 'Mở chấm điểm',
      scoringOpened: 'Đã mở chấm điểm cho hội đồng này.',
      scheduled: 'Đã lập lịch hội đồng thành công.',
    },
    admin: {
      title: 'Quản trị luận văn',
      description: 'Tạo đợt đăng ký, quản lý vòng đời đợt, và theo dõi đề tài, nhóm, hội đồng.',
      createRound: 'Tạo đợt',
      totalRounds: 'Tổng số đợt',
      openRounds: 'Đang mở đăng ký',
      noRounds: 'Chưa có đợt luận văn nào.',
      noRoundsDescription: 'Tạo đợt đăng ký đầu tiên để bắt đầu quy trình luận văn.',
      roundName: 'Tên đợt',
      roundNamePlaceholder: 'VD: Đợt luận văn Xuân 2026',
      thesisType: 'Loại luận văn',
      registrationStart: 'Bắt đầu đăng ký',
      registrationEnd: 'Kết thúc đăng ký',
      proposalPublishAt: 'Ngày công bố đề tài',
      reportDate: 'Ngày báo cáo',
      defenseDate: 'Ngày bảo vệ',
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
    placeholder: 'Hỏi về đăng ký, đề tài, nhóm hoặc phản biện...',
    send: 'Gửi tin nhắn',
    thinking: 'Đang kiểm tra ngữ cảnh luận văn được phép xem...',
    empty: 'Bắt đầu bằng câu hỏi về đợt luận văn hiện tại của bạn.',
    unavailable: 'Trợ lý hiện chưa sẵn sàng. Dữ liệu luận văn của bạn không bị thay đổi.',
  },
};

export const dictionaries = {
  en,
  vi,
} as const satisfies Record<Locale, I18nMessages>;

export function getMessages(locale: Locale) {
  return dictionaries[locale];
}
