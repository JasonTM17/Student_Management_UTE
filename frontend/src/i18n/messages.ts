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
      openWorkspace: 'Open campus portal',
      returnHome: 'Return to the homepage',
      openDashboard: 'Open dashboard',
      continueToWorkspace: 'Continue to campus portal',
      signInToWorkspace: 'Sign in to campus portal',
      reviewAdmin: 'Open admin tools',
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
    statuses: {
      OPEN: 'Open',
      CLOSED: 'Closed',
      ACTIVE: 'Active',
      INACTIVE: 'Inactive',
      LOCKED: 'Locked',
      SUSPENDED: 'Suspended',
      DISABLED: 'Disabled',
      ENROLLED: 'Registered',
      CONFIRMED: 'Confirmed',
      PENDING: 'Pending review',
      DROPPED: 'Dropped',
      CANCELLED: 'Cancelled',
      COMPLETED: 'Completed',
      PUBLISHED: 'Published',
      DRAFT: 'Draft',
      ARCHIVED: 'Archived',
      APPROVED: 'Approved',
      REJECTED: 'Not approved',
      IN_PROGRESS: 'In progress',
      NONE: 'No grades yet',
      PARTIAL: 'Partially graded',
      ALL_GRADED: 'All grades entered',
      NOT_GRADED: 'Not graded yet',
      APPEALED: 'Under review',
      PENDING_REVIEW: 'Pending review',
      UNKNOWN: 'Not available',
    },
    campusErrors: {
      network:
        'CampusCore could not reach the campus portal right now. Try again in a moment.',
      validation: 'Please check the details and try again.',
      conflict:
        'That change could not be completed. Please check the current records and try again.',
      unauthorized: 'Please sign in again to continue.',
      forbidden: 'You do not have access to this action.',
      notFound: 'That record is no longer available.',
      server:
        'Something went wrong on campus systems. Please try again in a moment.',
      unknown: 'We could not complete that action. Please try again.',
    },
  },
  meta: {
    defaults: {
      siteName: 'CampusCore',
      title: 'Campus academic portal',
      description:
        'CampusCore is a focused academic portal for registration, schedules, grades, announcements, and thesis work.',
      ogAlt: 'CampusCore academic portal overview',
      twitterTitle: 'CampusCore',
      twitterDescription:
        'A clear academic portal for registration, schedules, grades, announcements, and thesis work.',
    },
    home: {
      title: 'Campus academic portal',
      description:
        'CampusCore gives administrators, lecturers, and students one steady campus portal.',
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
      description: 'Set a new CampusCore password and return to the campus portal.',
    },
    dashboard: {
      title: 'Campus portal',
      description:
        'Protected student and lecturer dashboards for CampusCore.',
    },
    admin: {
      title: 'Admin portal',
      description: 'Protected administration routes for CampusCore.',
    },
    socialImage: {
      eyebrow: 'CampusCore',
      title: 'Academic work that stays clear from sign-in to thesis progress.',
      description:
        'Registration, teaching, people records, announcements, and thesis work in one focused portal.',
      badges: [
        'A portal shaped around each role',
        'Verified releases',
        'One academic portal',
      ],
    },
  },
  home: {
    navSubtitle: 'Campus academic portal',
    eyebrow: 'A portal shaped around each role',
    title: 'Academic work, from registration to thesis.',
    description:
      'Students, lecturers, and admins share one CampusCore portal.',
    skipToContent: 'Skip to content',
    metricCards: [
      {
        title: 'Ready for class',
        description:
          'The portal covers the core academic tasks needed for a focused class demonstration.',
      },
      {
        title: 'Security-first',
        description:
          'Account protection and safe enrolment checks stay in place without exposing technical details.',
      },
      {
        title: 'Clear academic ownership',
        description:
          'Each task points to a clear person or team instead of leaving responsibility unclear.',
      },
    ],
    snapshotEyebrow: 'Academic snapshot',
    snapshotTitle: 'A calmer way to keep campus work moving',
    snapshotChecks: [
      'Students, lecturers, and admins start from one steady portal',
      'Registration, grades, announcements, and thesis work stay connected after sign-in',
      'Web and mobile stay aligned with the same campus records',
      'A clean course setup is ready for a focused class demonstration',
    ],
    snapshotPrimaryAccessTitle: 'Primary access',
    snapshotPrimaryAccessDescription:
      'Students, lecturers, and admins each enter through their own campus portal.',
    snapshotReleaseTitle: 'Class demonstration scope',
    snapshotReleaseDescription:
      'One campus portal and focused apps keep the class demonstration easy to follow.',
    capabilitiesEyebrow: 'What the portal is built to do',
    capabilitiesTitle:
      'One clear experience across the key campus tasks',
    capabilitiesDescription:
      'The interface supports day-to-day academic work with clear sign-in, data states, and role-based tasks.',
    pillars: [
      {
        title: 'Identity you can trust',
        description:
          'Account access stays steady across the people-facing workflows that matter during the academic day.',
      },
      {
        title: 'Academic workflows',
        description:
          'Registration, schedules, grades, transcript views, and section work share the same campus records.',
      },
      {
        title: 'Clear academic status',
        description:
          'Every screen shows whether work is still loading, empty, blocked, or complete.',
      },
      {
        title: 'People records',
        description:
          'Student and lecturer records stay readable and protected in one academic system.',
      },
      {
        title: 'Release discipline',
        description:
          'Course setup and handoff notes stay clear for a focused class demonstration.',
      },
      {
        title: 'Campus-ready shell',
        description:
          'One portal for students, lecturers, and admins with sharper states, fewer dead ends, and calmer navigation.',
      },
    ],
    whyEyebrow: 'Why CampusCore stays on one campus portal',
    whyTitle: 'Keep the course project focused and reproducible',
    whyDescription:
      'CampusCore keeps academic records in one campus portal so registration, access, and the class demonstration remain easy to understand.',
    whyPoints: [
      {
        title: 'One campus portal',
        description:
          'Students, lecturers, and admins work from the same CampusCore home.',
      },
      {
        title: 'The right place to start',
        description:
          'Each person enters the area that matches their campus role.',
      },
      {
        title: 'Records you can trust',
        description:
          'Courses, schedules, and grades stay aligned with the current term.',
      },
      {
        title: 'Straightforward verification',
        description:
          'Focused web and mobile checks make the campus experience easy to follow.',
      },
      {
        title: 'A focused course setup',
        description:
          'The class demonstration stays focused on the campus portal and its records.',
      },
      {
        title: 'Clearer course handoff',
        description:
          'Students can explain the architecture and demonstrate core academic flows end to end.',
      },
    ],
    footerSubtitle: 'Academic portal',
    footerDescription:
      'A campus platform built for steady sign-in, clearer ownership, and calmer day-to-day academic work.',
    footerWorkspace: 'Campus portal',
    footerDelivery: 'Delivery',
    footerLinks: {
      workspace: ['Student access', 'Lecturer workflows', 'Admin tools'],
      delivery: ['Sign in', 'Campus portal', 'Admin'],
    },
    footerNav: {
      workspace: [
        { href: '/login?portal=student', label: 'Student access' },
        { href: '/login?portal=lecturer', label: 'Lecturer workflows' },
        { href: '/login?portal=admin', label: 'Admin tools' },
      ],
      delivery: [
        { href: '/login', label: 'Sign in' },
        { href: '/dashboard', label: 'Campus portal' },
        { href: '/admin', label: 'Admin' },
      ],
    },
    processKicker: 'Academic path',
    processSteps: ['Register', 'Schedule', 'Grades', 'Thesis'],
    roleLanes: {
      student: {
        title: 'Student',
        rows: ['Register', 'Schedule', 'Grades'],
        action: 'Sign in to register',
        href: '/login?portal=student',
      },
      lecturer: {
        title: 'Lecturer',
        rows: ['Gradebook', 'Teaching schedule'],
        action: 'Open faculty sign-in',
        href: '/login?portal=lecturer',
      },
      admin: {
        title: 'Admin',
        rows: ['People', 'Course catalog'],
        action: 'Open operations sign-in',
        href: '/login?portal=admin',
      },
    },
    publicProof: [
      {
        title: 'One campus portal',
        description:
          'Students, lecturers, and admins share the same CampusCore home.',
      },
      {
        title: 'Records you can trust',
        description:
          'Courses, schedules, and grades stay aligned with the current term.',
      },
      {
        title: 'Clear academic status',
        description:
          'Every screen shows whether work is still loading, empty, blocked, or complete.',
      },
    ],
    identityTabs: ['Student', 'Lecturer', 'Admin'],
    identityRows: [
      { code: 'SE101', label: 'Software Engineering', meta: 'Mon 07:00' },
      { code: 'MA201', label: 'Discrete Math', meta: 'Tue 09:00' },
      { code: 'TH400', label: 'Thesis seminar', meta: 'Wed 13:00' },
    ],
    lecturerIdentityRows: [
      { code: 'SE101', label: 'Software Engineering', meta: 'Room A2' },
      { code: 'SE204', label: 'Database systems', meta: 'Room B1' },
      { code: 'TH400', label: 'Thesis seminar', meta: 'Hall 3' },
    ],
    adminIdentityRows: [
      { code: 'USR', label: 'User records', meta: 'Open' },
      { code: 'CAT', label: 'Course catalog', meta: 'Review' },
      { code: 'ENR', label: 'Enrollment window', meta: 'Active' },
    ],
    footerCopyright: 'All rights reserved.',
  },
  authShell: {
    desktopSubtitle: 'Campus academic portal',
    mobileSubtitle: 'Academic access',
    lecturerSubtitle: 'Faculty portal',
    adminSubtitle: 'Campus operations',
  },
  login: {
    eyebrow: 'Secure access',
    title: 'Sign in to the campus portal.',
    description:
      'Sign in with your campus account to continue across registration, grades, schedules, and updates.',
    featureTitles: ['The right place to start', 'One sign-in', 'Ready for class'],
    featureDescriptions: [
      'Students, lecturers, and admins each arrive in the area they need.',
      'Sign in once and keep working through registration, grades, and teaching.',
      'The day starts from a calm campus entry, not a stack of extra steps.',
    ],
    sectionEyebrow: 'Account access',
    heading: 'Welcome back',
    subheading: 'Sign in with your campus account to continue.',
    emailLabel: 'Email address',
    emailPlaceholder: 'you@university.edu',
    officeSupport: 'Campus academic office',
    passwordLabel: 'Password',
    passwordPlaceholder: 'Enter your password',
    forgotPassword: 'Forgot password?',
    showPassword: 'Show password',
    hidePassword: 'Hide password',
    signingIn: 'Signing in',
    reasonMessages: {
      sessionExpired: {
        title: 'Your session ended',
        body: 'Sign in again to continue working in CampusCore.',
      },
      unauthorized: {
        title: 'Sign in required',
        body: 'Please sign in again before trying that action.',
      },
      signedOut: {
        title: 'Signed out',
        body: 'You have been signed out of the campus portal.',
      },
    },
    runtimeNotice: {
      infoTitle: 'Preview ready',
      infoBody: 'This preview is ready to review.',
      warningTitle: 'Sign-in is unavailable in this preview',
      warningBody:
        'This preview cannot reach campus services right now. Open the main portal or try again in a moment.',
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
    portals: {
      groupLabel: 'Choose your campus portal',
      student: {
        tab: 'Student',
        eyebrow: 'Student portal',
        title: 'Sign in to the student portal.',
        description:
          'Register for sections, read your timetable, and follow grades from the student desk.',
        heading: 'Student sign-in',
        subheading: 'Use your student campus account to open registration and class records.',
        officeSupport: 'Campus academic office',
        destination: 'A matching student account opens the student dashboard.',
        mismatch:
          'This account belongs to another campus portal. Open the lecturer or admin sign-in instead.',
        featureTitles: ['Register', 'Timetable', 'Grades'],
        featureDescriptions: [
          'Pick sections while the registration window is open.',
          'See class times and rooms for the current term.',
          'Follow published scores without leaving the student desk.',
        ],
      },
      lecturer: {
        tab: 'Lecturer',
        eyebrow: 'Faculty portal',
        title: 'Sign in to the faculty portal.',
        description:
          'Open the gradebook, teaching timetable, and section notices from the faculty desk.',
        heading: 'Faculty sign-in',
        subheading: 'Use your lecturer campus account to continue teaching work.',
        officeSupport: 'Faculty office',
        destination: 'A matching lecturer account opens the faculty dashboard.',
        mismatch:
          'This account belongs to another campus portal. Open the student or admin sign-in instead.',
        featureTitles: ['SE204', 'GRADE', 'SLOT'],
        featureDescriptions: [
          'Class lists stay next to the students you teach.',
          'Enter and review scores for the sections you teach.',
          'Check teaching hours without switching to another desk.',
        ],
      },
      admin: {
        tab: 'Admin',
        eyebrow: 'Operations portal',
        title: 'Sign in to campus operations.',
        description:
          'Manage people, the catalog, notices, and the public campus look from one operations desk.',
        heading: 'Operations sign-in',
        subheading: 'Use an administrator account to manage campus records.',
        officeSupport: 'Campus operations',
        destination: 'A matching administrator account opens the admin portal.',
        mismatch:
          'This account belongs to another campus portal. Open the student or lecturer sign-in instead.',
        opsMark: 'Ops',
        featureTitles: ['People records', 'Catalog', 'Campus look'],
        featureDescriptions: [
          'Review accounts and role assignments.',
          'Keep courses, sections, and rooms aligned.',
          'Publish notices and arrange how they appear.',
        ],
      },
    },
  },
  courseRegistration: {
    eyebrow: 'Student portal',
    title: 'Course registration',
    description: 'Browse open sections, check remaining seats, and confirm your course registration.',
    searchPlaceholder: 'Search by course code or name',
    semester: 'Semester',
    allSemesters: 'All semesters',
    seatsLeft: 'Seats left',
    full: 'Full',
    register: 'Register',
    registered: 'Registered',
    drop: 'Drop course',
    emptyTitle: 'No matching sections',
    emptyDescription: 'Try another semester or search term.',
    loadFailed: 'Course registration data could not be loaded.',
    success: 'Enrollment updated.',
    confirmRegister: 'Confirm course registration?',
    confirmDrop: 'Are you sure you want to drop this course?',
    sectionCount: '{count} sections',
    working: 'Working',
    roundUnavailable: 'No registration round is open right now.',
    exportUnavailable: 'A registration slip is not available yet.',
    enrolledRail: 'Registered this term',
    columns: {
      course: 'Course',
      section: 'Section',
      schedule: 'Schedule',
      action: 'Action',
    },
  },
  workspaceForbidden: {
    title: 'Access restricted',
    signedInDescription: 'This account does not have permission for this area.',
    signedOutDescription: 'Sign in to continue to this area.',
  },
  signup: {
    eyebrow: 'Create a campus account',
    title: 'Register for CampusCore.',
    description: 'Create a student account with your campus email to start course registration.',
    heading: 'Create your account',
    subheading: 'Use a campus email, a password of at least 8 characters, and your name.',
    firstNameLabel: 'First name',
    lastNameLabel: 'Last name',
    emailLabel: 'Email address',
    passwordLabel: 'Password',
    submit: 'Create account',
    submitting: 'Creating account',
    hasAccount: 'Already have an account?',
    signInLink: 'Sign in',
    needAccount: 'Need a student account?',
    errors: {
      fallback: 'We could not create the account right now.',
      conflict: 'That email is already registered. Sign in instead.',
      validation: 'Please check the details and try again.',
    },
  },
  forgotPassword: {
    eyebrow: 'Password recovery',
    title: 'Recover account access without guessing.',
    description:
      'Use your campus email to request a reset link. The response stays consistent whether the account exists or not.',
    featureTitles: ['Verified handoff', 'Clear next steps', 'Safer messaging'],
    featureDescriptions: [
      'Password recovery stays aligned with the same sign-in experience used across the portal.',
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
      'The reset link returns you to the same protected sign-in flow instead of a separate experience.',
      'Users see password guidance and validation before the form submits.',
      'An expired or invalid link shows a clear recovery step instead of a broken page.',
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
    eyebrow: 'Admin portal',
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
    mobileNavigation: 'Admin navigation on mobile',
  },
  admin: {
    title: 'Admin dashboard',
    overviewTitle: 'Campus overview at a glance',
    description:
      'Manage campus identities, academic records, announcements, and core thesis workflows from one consistent portal.',
    managementConsoleTitle: 'Admin tools',
    managementConsoleDescription:
      'Open the area that needs attention. Every tool uses the same navigation and clear confirmation steps.',
    menuItems: [
      ['Thesis management', 'Manage registration rounds, topics, groups, and progress.'],
      ['User management', 'Review campus accounts, statuses, and role assignments.'],
      ['Lecturers', 'Manage lecturer records and teaching assignments.'],
      ['Courses', 'Maintain the course catalogue, codes, and details.'],
      ['Sections', 'Watch capacity, teaching assignments, and classrooms.'],
      ['Enrollments', 'Review registration outcomes and next steps.'],
      ['Semesters', 'Control the academic timeline and current registration window.'],
      ['Departments', 'Manage departmental structure and faculty mappings.'],
      ['Classrooms', 'Track rooms, buildings, and capacity readiness.'],
      ['Announcements', 'Publish updates that flow out to the rest of the campus.'],
      ['Campus assistant knowledge', 'Review the public campus guidance available to the CampusCore assistant.'],
      ['Site appearance', 'Change the public campus look and the order of notices.'],
    ],
    stats: ['Students', 'Lecturers', 'Courses', 'Enrollments'],
    statDetails: [
      'People records available to the current administrator.',
      'Active lecturer accounts and teaching-facing identities.',
      'Courses available for section planning and registration.',
      'Registration records available across the academic views.',
    ],
    loading: 'Loading campus overview',
    unavailableTitle: 'Admin overview unavailable',
    unavailableDescription: 'Campus overview is not available right now.',
    appearance: {
      title: 'Site appearance',
      description:
        'Change the public campus look and the order of notices. Other open pages pick up the change within a few seconds.',
      eyebrow: 'Public campus chrome',
      live: 'Live on the public site',
      saving: 'Saving',
      saved: 'Published',
      saveFailed: 'The campus look could not be saved right now.',
      loadFailed: 'The campus look could not be loaded right now.',
      heroEyebrow: 'Home eyebrow',
      heroTitle: 'Home title',
      heroDescription: 'Home description',
      heroHint: 'Leave a field empty to keep the default campus wording.',
      localeEn: 'English copy',
      localeVi: 'Vietnamese copy',
      accent: 'Campus accent',
      accents: {
        'ute-yellow': 'UTE yellow',
        'campus-gold': 'Campus gold',
        'river-blue': 'River blue',
      },
      preview: 'Live preview',
      postsTitle: 'Notice order',
      postsDescription:
        'Move a notice up or down. Student and lecturer feeds follow this order as soon as it is saved.',
      postsEmpty: 'Publish a notice first, then arrange it here.',
      moveUp: 'Move up',
      moveDown: 'Move down',
      openAnnouncements: 'Open notices',
    },
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
      'Keep the current page, academic signals, and registration shortcuts visible without crowding the main portal.',
      currentViewLabel: 'Current view',
      signalsTitle: 'Signals',
      notificationLabel: 'Unread updates',
      localeLabel: 'Language',
      sessionSummary:
        'Your student area stays in the same sign-in while you move between registration, schedules, and records.',
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
      thesis: 'Thesis area',
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
      mobileNavigation: 'Campus navigation on mobile',
      bottomNav: {
        dashboard: 'Home',
        schedule: 'Schedule',
        courseRegistration: 'Register',
        grades: 'Grades',
        teachingSchedule: 'Schedule',
        gradeManagement: 'Grades',
        thesis: 'Thesis',
        menu: 'Menu',
      },
      skipToContent: 'Skip to portal content',
      sidebarNavigation: 'Student and lecturer portal navigation',
      preferences: 'Preferences',
    },
    pageDefaults: {
      description:
        'Navigate the current task without leaving the portal.',
      title: 'Campus portal',
      fallbackDescription:
        'Move through your role-based tools with a consistent sign-in.',
    },
    signOutPage: {
      eyebrow: 'Sign-in handoff',
      title: 'Signing you out',
      description:
        'We are closing your current sign-in and returning you to the login page.',
      progress: 'Finishing sign-out...',
    },
    routeDescriptions: {
      dashboard:
      'Registration, courses, schedules, and profile tasks stay in one student area.',
      profile:
        'Keep contact details and password changes together with your account.',
      register:
        'Browse classes and manage registration decisions for the current term.',
      enrollments:
        'Track the classes you are taking and their class details.',
      schedule:
        'Keep the weekly class view close while the rest of the portal stays reachable.',
      grades: 'Review published grades and current academic standing.',
      transcript: 'View cumulative academic history and semester outcomes.',
      announcements: 'Read campus-wide updates and shared notices.',
      notifications:
        'Review account alerts, academic deadlines, and workflow updates in one focused inbox.',
      lecturer:
        'Keep teaching tasks, grading queues, class context, and announcements in one lecturer area.',
      lecturerSchedule: 'Track assigned sections, rooms, and meeting windows.',
      lecturerGrades:
        'Review grading queues, filter by term, and move publish-ready sections forward.',
      lecturerAnnouncements:
        'Share updates with the students connected to your sections.',
      thesis:
        'Track thesis registration rounds, topics, groups, and progress.',
    },
    loading: 'Loading campus portal',
  },
  studentDashboard: {
    eyebrow: 'Student area',
    title: 'Welcome back, {name}',
    description:
      'The current term is {semester}. Move between registration, courses, schedules, and profile updates without leaving the student area.',
    currentTermFallback: 'No active term',
    currentDateLabel: 'Today',
    metrics: {
      coursesInScope: 'Courses in scope',
      confirmedEnrollments: 'Confirmed enrollments',
      pendingDecisions: 'Pending decisions',
      currentSemester: 'Current semester',
      details: [
        'Registration, class details, and current courses remain visible from the same student area.',
        'Confirmed classes stay close so you can move into schedules, grades, and transcript work without losing context.',
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
          'Confirmed courses stay visible here so you can check your classes before opening more details.',
        emptyTitle: 'No confirmed courses yet',
        emptyDescription:
          'Once enrollment is confirmed, your current courses will appear here.',
        sectionLabel: 'Section {section}',
      },
      referenceLinks: {
        title: 'Reference links',
        description:
        'Keep the supporting student views close without leaving the same sign-in.',
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
      'Update personal details, keep contact information current, and change your password without leaving the portal.',
    profileTitle: 'Account profile',
    profileDescription:
      'Keep the account record aligned with the information your campus teams rely on.',
    profileUpdated: 'Profile updated',
    profileSaveFailed: 'We could not save your profile changes.',
    passwordTitle: 'Password and sign-in safety',
    passwordDescription:
      'Use a strong password and expect to sign in again after a successful change.',
    passwordUpdated: 'Password updated',
    passwordUpdateFailed: 'We could not update your password.',
    whatChangesTitle: 'What changes here',
    whatChanges: [
      'Profile edits update the account view after a successful save.',
      'Password changes stay on this account page after you confirm.',
      'Sensitive account fields such as your email stay managed by campus staff, not an inline edit field.',
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
    eyebrow: 'Lecturer area',
    title: 'Welcome back, {name}',
    description:
      'Keep class work, grading queues, and teaching updates in one lecturer-focused area.',
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
      'Shared notices for the lecturer area will show up here once they are published.',
    quickLinks: [
      ['Teaching schedule', 'Check rooms, sections, and meeting times for the current term.'],
      ['Grade management', 'Finish grading queues and move publish-ready sections forward.'],
      ['Announcements', 'Review broadcast updates that affect your sections and teaching day.'],
    ],
    metrics: {
      labels: ['Sections', 'Students', 'Ready to publish', 'Fresh notices'],
      details: [
        'Assigned teaching sections stay visible so grading and scheduling decisions remain grounded in the same term context.',
        'Registration volume stays close to the lecturer area so class follow-up remains visible.',
        'Publish-ready grading work surfaces early so final review does not get lost behind the rest of the workflow.',
        'Broadcast teaching updates remain visible without pulling attention away from the grading queue.',
      ],
    },
    queueStatusReady: 'Ready to publish',
    queueStatusProgress: 'In progress',
    sectionPrefix: 'Class',
    studentsSuffix: 'students',
    gradedSuffix: 'graded',
    errors: {
      loadFailed: 'The lecturer dashboard could not load its academic data.',
      unavailableTitle: 'Lecturer dashboard unavailable',
      loading: 'Loading lecturer dashboard',
    },
  },
  lecturerGrades: {
    eyebrow: 'Lecturer area',
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
    eyebrow: 'Thesis area',
    title: 'Keep the whole thesis journey in one calm timeline.',
    description:
      'Registration rounds, topic choices, group membership, and recorded progress stay connected to your campus identity.',
    selectRound: 'Select registration round',
    noRound: 'No thesis registration round is available yet.',
    loading: 'Loading thesis area',
    loadFailed: 'Thesis data could not be loaded right now.',
    retry: 'Retry thesis area',
    roundStatus: 'Round status',
    registrationWindow: 'Registration window',
    topics: 'Published topics',
    groups: 'Thesis groups',
    topicsTitle: 'Find a direction that fits your group.',
    topicsDescription:
      'Published topics are grouped by department. Topic selection becomes a proposal your coordinator can review.',
    groupsTitle: 'Your group area',
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
    backToWorkspace: 'Back to thesis area',
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
      returnToWorkspace: 'Return to your area',
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
    label: 'Campus helpdesk',
    title: 'CampusCore assistant',
    description: 'Ask about registration, schedules, announcements, the academic catalog, policies, or your thesis journey.',
    open: 'Open CampusCore assistant',
    close: 'Close CampusCore assistant',
    placeholder: 'Ask about registration, schedules, announcements, or your thesis journey…',
    send: 'Send message',
    thinking: 'Checking the public CampusCore knowledge base…',
    empty: 'Start with a focused question about campus services or your thesis journey.',
    unavailable: 'The assistant is not available right now. Your academic records are unchanged.',
    answered: 'Answer based on reviewed guidance',
    noMatch: 'No matching guidance',
    degraded: 'Reviewed guidance temporarily unavailable',
    sources: 'Sources',
    domains: {
      THESIS: 'Thesis guidance',
      REGISTRATION: 'Registration',
      ACADEMIC_CATALOG: 'Academic catalog',
      ANNOUNCEMENT: 'Announcements',
      POLICY: 'Campus policy',
      GENERAL_FAQ: 'Campus FAQ',
    },
    you: 'You',
    history: 'Conversation history',
    backToChat: 'Back to chat',
    newConversation: 'New conversation',
    deleteConversation: 'Delete conversation',
    deleteConversationConfirm: 'Delete this conversation? Its messages will be permanently removed.',
    historyLoading: 'Loading your conversations…',
    historyUnavailable: 'Conversation history is temporarily unavailable.',
    historyEmpty: 'No saved conversations yet.',
    untitledConversation: 'Campus conversation',
    model: 'Source note',
    stop: 'Stop generating',
    retry: 'Retry',
    quotaExceeded: 'Daily assistant limit reached. Try again tomorrow or continue with the cited guidance.',
    cancelled: 'Generation stopped. You can retry when you are ready.',
    feedbackUp: 'Mark answer helpful',
    feedbackDown: 'Mark answer not helpful',
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
      openWorkspace: 'Mở cổng học vụ',
      returnHome: 'Quay về trang chủ',
      openDashboard: 'Mở bảng điều khiển',
      continueToWorkspace: 'Tiếp tục vào cổng học vụ',
      signInToWorkspace: 'Đăng nhập vào cổng học vụ',
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
    statuses: {
      OPEN: 'Đang mở',
      CLOSED: 'Đã đóng',
      ACTIVE: 'Đang hoạt động',
      INACTIVE: 'Tạm dừng',
      LOCKED: 'Đã khóa',
      SUSPENDED: 'Tạm dừng',
      DISABLED: 'Đã vô hiệu hóa',
      ENROLLED: 'Đã đăng ký',
      CONFIRMED: 'Đã xác nhận',
      PENDING: 'Đang chờ duyệt',
      DROPPED: 'Đã hủy',
      CANCELLED: 'Đã hủy',
      COMPLETED: 'Đã hoàn tất',
      PUBLISHED: 'Đã công bố',
      DRAFT: 'Bản nháp',
      ARCHIVED: 'Đã lưu trữ',
      APPROVED: 'Đã duyệt',
      REJECTED: 'Chưa được duyệt',
      IN_PROGRESS: 'Đang xử lý',
      NONE: 'Chưa có điểm',
      PARTIAL: 'Đã nhập một phần',
      ALL_GRADED: 'Đã nhập đủ điểm',
      NOT_GRADED: 'Chưa chấm điểm',
      APPEALED: 'Đang xem xét',
      PENDING_REVIEW: 'Đang chờ duyệt',
      UNKNOWN: 'Chưa có thông tin',
    },
    campusErrors: {
      network:
        'CampusCore chưa kết nối được cổng học vụ lúc này. Hãy thử lại sau một lát.',
      validation: 'Hãy kiểm tra lại thông tin rồi thử lại.',
      conflict:
        'Thay đổi này chưa thể hoàn tất. Hãy kiểm tra hồ sơ hiện tại rồi thử lại.',
      unauthorized: 'Hãy đăng nhập lại để tiếp tục.',
      forbidden: 'Bạn không có quyền thực hiện thao tác này.',
      notFound: 'Hồ sơ này không còn khả dụng.',
      server:
        'Hệ thống học vụ đang gặp sự cố. Hãy thử lại sau một lát.',
      unknown: 'Chưa thể hoàn tất thao tác này. Hãy thử lại.',
    },
  },
  meta: {
    defaults: {
      siteName: 'CampusCore',
      title: 'Cổng học vụ CampusCore',
      description:
        'CampusCore là cổng học vụ tập trung cho đăng ký, lịch học, điểm, thông báo và luận văn.',
      ogAlt: 'Tổng quan cổng học vụ CampusCore',
      twitterTitle: 'CampusCore',
      twitterDescription:
        'Cổng học vụ rõ ràng cho đăng ký, lịch học, điểm, thông báo và luận văn.',
    },
    home: {
      title: 'Cổng học vụ CampusCore',
      description:
        'CampusCore mang đến một cổng học vụ ổn định cho quản trị, giảng viên và sinh viên.',
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
      description: 'Tạo mật khẩu mới cho CampusCore và quay lại cổng học vụ.',
    },
    dashboard: {
      title: 'Cổng học vụ',
      description:
        'Các bảng điều khiển được bảo vệ cho sinh viên và giảng viên trong CampusCore.',
    },
    admin: {
      title: 'Cổng quản trị',
      description: 'Các khu vực quản trị được bảo vệ của CampusCore.',
    },
    socialImage: {
      eyebrow: 'CampusCore',
      title: 'Công việc học vụ rõ ràng từ đăng nhập đến tiến độ luận văn.',
      description:
        'Đăng ký học phần, giảng dạy, hồ sơ, thông báo và luận văn trong một cổng thống nhất.',
      badges: [
        'Cổng học vụ theo vai trò',
        'Phát hành đã xác minh',
        'Một cổng học vụ',
      ],
    },
  },
  home: {
    navSubtitle: 'Cổng học vụ CampusCore',
    eyebrow: 'Cổng học vụ theo vai trò',
    title: 'Học vụ thông suốt, từ đăng ký đến luận văn.',
    description:
      'Sinh viên, giảng viên và quản trị dùng chung một cổng CampusCore.',
    skipToContent: 'Bỏ qua đến nội dung',
    metricCards: [
      {
        title: 'Sẵn sàng cho buổi trình diễn',
        description:
          'Cổng học vụ bao phủ các tác vụ cốt lõi cho một buổi trình diễn rõ ràng.',
      },
      {
        title: 'Ưu tiên bảo mật',
        description:
          'Các lớp bảo vệ tài khoản và giao dịch vẫn được giữ vững mà không biến giao diện thành bảng chẩn đoán kỹ thuật.',
      },
      {
        title: 'Phân công học vụ rõ ràng',
        description:
          'Giao diện bám theo người phụ trách rõ ràng thay vì dồn mọi việc vào một nơi.',
      },
    ],
    snapshotEyebrow: 'Tổng quan học vụ',
    snapshotTitle: 'Một cách bình tĩnh hơn để giữ campus luôn chạy',
    snapshotChecks: [
      'Sinh viên, giảng viên và quản trị cùng đi vào một cổng học vụ ổn định',
      'Đăng ký học phần, điểm, thông báo và luận văn nối tiếp sau khi đăng nhập',
      'Web và điện thoại cùng đọc một bộ hồ sơ học vụ',
      'Dữ liệu học phần có thể dựng lại sạch cho buổi trình diễn',
    ],
    snapshotPrimaryAccessTitle: 'Lối vào chính',
    snapshotPrimaryAccessDescription:
      'Sinh viên, giảng viên và quản trị mỗi người vào bằng cổng đăng nhập riêng.',
    snapshotReleaseTitle: 'Phạm vi trình diễn môn học',
    snapshotReleaseDescription:
      'Một cổng học vụ và các ứng dụng chuyên biệt giúp bản trình diễn môn học dễ theo dõi.',
    capabilitiesEyebrow: 'Cổng này được xây để làm gì',
    capabilitiesTitle: 'Một trải nghiệm rõ ràng cho các tác vụ campus quan trọng',
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
          'Đăng ký, thời khóa biểu, điểm, bảng điểm và công việc lớp học phần dùng chung cùng hồ sơ campus.',
      },
      {
        title: 'Trạng thái học vụ rõ ràng',
        description:
          'Mỗi màn hình cho biết công việc đang tải, chưa có dữ liệu, bị chặn hoặc đã xong.',
      },
      {
        title: 'Hồ sơ người dùng',
        description:
          'Dữ liệu sinh viên và giảng viên được phân quyền rõ trong cùng hệ thống học vụ.',
      },
      {
        title: 'Kỷ luật phát hành',
        description:
          'Thiết lập và bàn giao luôn rõ ràng cho một buổi trình diễn môn học.',
      },
      {
        title: 'Cổng sẵn cho campus',
        description:
          'Một cổng chung cho sinh viên, giảng viên và quản trị với trạng thái rõ hơn, ít ngõ cụt hơn và điều hướng điềm tĩnh hơn.',
      },
    ],
    whyEyebrow: 'Vì sao CampusCore giữ một cổng học vụ',
    whyTitle: 'Giữ đồ án tập trung và dễ tái lập',
    whyDescription:
      'CampusCore giữ hồ sơ học vụ trong một cổng để đăng ký, phân quyền và bản trình diễn môn học dễ hiểu.',
    whyPoints: [
      {
        title: 'Một cổng học vụ',
        description:
          'Sinh viên, giảng viên và quản trị làm việc trên cùng cổng CampusCore.',
      },
      {
        title: 'Đúng khu vực học vụ',
        description:
          'Mỗi người vào đúng khu vực phù hợp với vai trò trên campus.',
      },
      {
        title: 'Hồ sơ học vụ đáng tin',
        description:
          'Môn học, thời khóa biểu và điểm số bám theo học kỳ đang diễn ra.',
      },
      {
        title: 'Kiểm chứng đơn giản',
        description:
          'Các bước kiểm tra web và điện thoại giúp theo dõi trải nghiệm campus.',
      },
      {
        title: 'Thiết lập môn học gọn',
        description:
          'Buổi trình diễn môn học chỉ cần cổng học vụ và hồ sơ campus.',
      },
      {
        title: 'Bàn giao đồ án rõ hơn',
        description:
          'Sinh viên có thể giải thích kiến trúc và demo các luồng học vụ từ đầu đến cuối.',
      },
    ],
    footerSubtitle: 'Cổng học vụ',
    footerDescription:
      'Nền tảng campus tập trung vào đăng nhập ổn định, phân công rõ ràng và công việc học vụ hằng ngày bớt rối hơn.',
    footerWorkspace: 'Cổng học vụ',
    footerDelivery: 'Triển khai',
    footerLinks: {
      workspace: ['Khu sinh viên', 'Luồng giảng viên', 'Công cụ quản trị'],
      delivery: ['Đăng nhập', 'Cổng học vụ', 'Quản trị'],
    },
    footerNav: {
      workspace: [
        { href: '/login?portal=student', label: 'Khu sinh viên' },
        { href: '/login?portal=lecturer', label: 'Luồng giảng viên' },
        { href: '/login?portal=admin', label: 'Công cụ quản trị' },
      ],
      delivery: [
        { href: '/login', label: 'Đăng nhập' },
        { href: '/dashboard', label: 'Cổng học vụ' },
        { href: '/admin', label: 'Quản trị' },
      ],
    },
    processKicker: 'Lộ trình học vụ',
    processSteps: ['Đăng ký', 'Thời khóa biểu', 'Điểm', 'Luận văn'],
    roleLanes: {
      student: {
        title: 'Sinh viên',
        rows: ['Đăng ký', 'Thời khóa biểu', 'Điểm'],
        action: 'Đăng nhập để đăng ký',
        href: '/login?portal=student',
      },
      lecturer: {
        title: 'Giảng viên',
        rows: ['Sổ điểm', 'Lịch giảng'],
        action: 'Mở đăng nhập giảng viên',
        href: '/login?portal=lecturer',
      },
      admin: {
        title: 'Quản trị',
        rows: ['Người dùng', 'Danh mục môn'],
        action: 'Mở đăng nhập quản trị',
        href: '/login?portal=admin',
      },
    },
    publicProof: [
      {
        title: 'Một cổng học vụ',
        description:
          'Sinh viên, giảng viên và quản trị dùng chung một cổng CampusCore.',
      },
      {
        title: 'Hồ sơ học vụ đáng tin',
        description:
          'Môn học, thời khóa biểu và điểm số bám theo học kỳ đang diễn ra.',
      },
      {
        title: 'Trạng thái học vụ rõ ràng',
        description:
          'Mỗi màn hình cho biết công việc đang tải, chưa có dữ liệu, bị chặn hoặc đã xong.',
      },
    ],
    identityTabs: ['Sinh viên', 'Giảng viên', 'Quản trị'],
    identityRows: [
      { code: 'SE101', label: 'Công nghệ phần mềm', meta: 'T2 07:00' },
      { code: 'MA201', label: 'Toán rời rạc', meta: 'T3 09:00' },
      { code: 'TH400', label: 'Seminar luận văn', meta: 'T4 13:00' },
    ],
    lecturerIdentityRows: [
      { code: 'SE101', label: 'Công nghệ phần mềm', meta: 'P. A2' },
      { code: 'SE204', label: 'Cơ sở dữ liệu', meta: 'P. B1' },
      { code: 'TH400', label: 'Seminar luận văn', meta: 'Hội trường 3' },
    ],
    adminIdentityRows: [
      { code: 'USR', label: 'Hồ sơ người dùng', meta: 'Mở' },
      { code: 'CAT', label: 'Danh mục môn', meta: 'Duyệt' },
      { code: 'ENR', label: 'Cửa sổ đăng ký', meta: 'Đang mở' },
    ],
    footerCopyright: 'Mọi quyền được bảo lưu.',
  },
  authShell: {
    desktopSubtitle: 'Cổng học vụ CampusCore',
    mobileSubtitle: 'Truy cập học vụ',
    lecturerSubtitle: 'Không gian giảng viên',
    adminSubtitle: 'Công cụ quản trị nhà trường',
  },
  login: {
    eyebrow: 'Truy cập an toàn',
    title: 'Đăng nhập vào cổng học vụ CampusCore.',
    description:
      'Đăng nhập bằng tài khoản nhà trường để tiếp tục đăng ký học phần, xem điểm và nhận thông báo.',
    featureTitles: ['Đúng nơi bắt đầu', 'Một lần đăng nhập', 'Sẵn sàng cho buổi học'],
    featureDescriptions: [
      'Sinh viên, giảng viên và quản trị đều vào đúng khu vực mình cần.',
      'Đăng nhập một lần rồi tiếp tục đăng ký, xem điểm và giảng dạy.',
      'Ngày học vụ bắt đầu từ một cổng rõ ràng, không thêm bước thừa.',
    ],
    sectionEyebrow: 'Truy cập tài khoản',
    heading: 'Chào mừng bạn quay lại',
    subheading: 'Đăng nhập bằng tài khoản nhà trường để tiếp tục.',
    emailLabel: 'Địa chỉ email',
    emailPlaceholder: 'you@university.edu',
    officeSupport: 'Phòng học vụ',
    passwordLabel: 'Mật khẩu',
    passwordPlaceholder: 'Nhập mật khẩu của bạn',
    forgotPassword: 'Quên mật khẩu?',
    showPassword: 'Hiện mật khẩu',
    hidePassword: 'Ẩn mật khẩu',
    signingIn: 'Đang đăng nhập',
    reasonMessages: {
      sessionExpired: {
        title: 'Lần đăng nhập của bạn đã kết thúc',
        body: 'Hãy đăng nhập lại để tiếp tục làm việc trong CampusCore.',
      },
      unauthorized: {
        title: 'Cần đăng nhập',
        body: 'Hãy đăng nhập lại trước khi thực hiện thao tác này.',
      },
      signedOut: {
        title: 'Đã đăng xuất',
        body: 'Bạn đã đăng xuất khỏi cổng học vụ.',
      },
    },
    runtimeNotice: {
      infoTitle: 'Bản xem trước đã sẵn sàng',
      infoBody: 'Bản xem trước này đã sẵn sàng để xem thử.',
      warningTitle: 'Đăng nhập chưa sẵn sàng trên bản xem trước này',
      warningBody:
        'Bản xem trước hiện chưa kết nối được tới dịch vụ nhà trường. Hãy mở cổng chính hoặc thử lại sau ít phút.',
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
    portals: {
      groupLabel: 'Chọn khu vực học vụ của bạn',
      student: {
        tab: 'Sinh viên',
        eyebrow: 'Cổng sinh viên',
        title: 'Đăng nhập vào khu sinh viên.',
        description:
          'Đăng ký học phần, xem thời khóa biểu và theo dõi điểm từ bàn làm việc sinh viên.',
        heading: 'Đăng nhập sinh viên',
        subheading: 'Dùng tài khoản sinh viên để mở đăng ký và hồ sơ lớp học.',
        officeSupport: 'Phòng học vụ',
        destination: 'Tài khoản sinh viên khớp sẽ mở khu sinh viên.',
        mismatch:
          'Tài khoản này thuộc cổng khác. Hãy mở đăng nhập giảng viên hoặc quản trị.',
        featureTitles: ['Đăng ký', 'Thời khóa biểu', 'Điểm'],
        featureDescriptions: [
          'Chọn học phần khi cửa sổ đăng ký đang mở.',
          'Xem giờ học và phòng cho học kỳ hiện tại.',
          'Theo dõi điểm đã công bố ngay tại bàn sinh viên.',
        ],
      },
      lecturer: {
        tab: 'Giảng viên',
        eyebrow: 'Cổng giảng viên',
        title: 'Đăng nhập vào khu giảng viên.',
        description:
          'Mở sổ điểm, lịch giảng và thông báo lớp từ bàn giảng viên.',
        heading: 'Đăng nhập giảng viên',
        subheading: 'Dùng tài khoản giảng viên để tiếp tục công việc giảng dạy.',
        officeSupport: 'Văn phòng khoa',
        destination: 'Tài khoản giảng viên khớp sẽ mở khu giảng viên.',
        mismatch:
          'Tài khoản này thuộc cổng khác. Hãy mở đăng nhập sinh viên hoặc quản trị.',
        featureTitles: ['SE204', 'GRADE', 'SLOT'],
        featureDescriptions: [
          'Danh sách lớp nằm cạnh sinh viên bạn phụ trách.',
          'Nhập và rà điểm cho các lớp bạn giảng.',
          'Xem giờ giảng mà không phải chuyển sang màn khác.',
        ],
      },
      admin: {
        tab: 'Quản trị',
        eyebrow: 'Cổng quản trị',
        title: 'Đăng nhập vào công cụ quản trị nhà trường.',
        description:
          'Quản lý người dùng, danh mục, thông báo và diện mạo cổng công khai từ một bàn quản trị.',
        heading: 'Đăng nhập quản trị',
        subheading: 'Dùng tài khoản quản trị để tiếp tục quản lý hồ sơ nhà trường.',
        officeSupport: 'Văn phòng quản trị nhà trường',
        destination: 'Tài khoản quản trị khớp sẽ mở cổng quản trị.',
        mismatch:
          'Tài khoản này thuộc cổng khác. Hãy mở đăng nhập sinh viên hoặc giảng viên.',
        opsMark: 'Quản trị',
        featureTitles: ['Hồ sơ người dùng', 'Danh mục môn học', 'Diện mạo cổng'],
        featureDescriptions: [
          'Rà tài khoản và phân quyền.',
          'Giữ môn, lớp và phòng học khớp nhau.',
          'Phát hành thông báo và sắp thứ tự hiển thị.',
        ],
      },
    },
  },
  courseRegistration: {
    eyebrow: 'Cổng sinh viên',
    title: 'Đăng ký học phần',
    description: 'Tra cứu lớp đang mở, kiểm tra số chỗ và xác nhận đăng ký học phần.',
    searchPlaceholder: 'Tìm theo mã hoặc tên môn học',
    semester: 'Học kỳ',
    allSemesters: 'Tất cả học kỳ',
    seatsLeft: 'Còn chỗ',
    full: 'Đã đầy',
    register: 'Đăng ký',
    registered: 'Đã đăng ký',
    drop: 'Hủy đăng ký',
    emptyTitle: 'Chưa có lớp học phần phù hợp',
    emptyDescription: 'Thử đổi học kỳ hoặc từ khóa tìm kiếm.',
    loadFailed: 'Không thể tải dữ liệu đăng ký học phần.',
    success: 'Đã cập nhật đăng ký học phần.',
    confirmRegister: 'Xác nhận đăng ký học phần này?',
    confirmDrop: 'Bạn có chắc muốn hủy học phần này không?',
    sectionCount: '{count} lớp học phần',
    working: 'Đang xử lý',
    roundUnavailable: 'Hiện chưa có đợt đăng ký đang mở.',
    exportUnavailable: 'Phiếu đăng ký chưa sẵn sàng.',
    enrolledRail: 'Đã đăng ký trong học kỳ',
    columns: {
      course: 'Học phần',
      section: 'Lớp học phần',
      schedule: 'Lịch học',
      action: 'Thao tác',
    },
  },
  workspaceForbidden: {
    title: 'Không có quyền truy cập',
    signedInDescription: 'Tài khoản hiện không được phép dùng khu vực này.',
    signedOutDescription: 'Hãy đăng nhập lại để tiếp tục vào khu vực này.',
  },
  signup: {
    eyebrow: 'Tạo tài khoản nhà trường',
    title: 'Đăng ký CampusCore.',
    description: 'Tạo tài khoản sinh viên bằng email nhà trường để bắt đầu đăng ký học phần.',
    heading: 'Tạo tài khoản',
    subheading: 'Dùng email nhà trường, mật khẩu tối thiểu 8 ký tự và họ tên của bạn.',
    firstNameLabel: 'Tên',
    lastNameLabel: 'Họ',
    emailLabel: 'Địa chỉ email',
    passwordLabel: 'Mật khẩu',
    submit: 'Tạo tài khoản',
    submitting: 'Đang tạo tài khoản',
    hasAccount: 'Đã có tài khoản?',
    signInLink: 'Đăng nhập',
    needAccount: 'Chưa có tài khoản sinh viên?',
    errors: {
      fallback: 'Hiện chưa thể tạo tài khoản.',
      conflict: 'Email này đã được đăng ký. Hãy đăng nhập.',
      validation: 'Hãy kiểm tra lại thông tin và thử lại.',
    },
  },
  forgotPassword: {
    eyebrow: 'Khôi phục mật khẩu',
    title: 'Lấy lại quyền truy cập mà không cần đoán mò.',
    description:
      'Dùng email nhà trường để yêu cầu liên kết đặt lại mật khẩu. Phản hồi sẽ giữ nhất quán dù tài khoản có tồn tại hay không.',
    featureTitles: ['Bàn giao đã xác minh', 'Bước kế tiếp rõ ràng', 'Thông điệp an toàn hơn'],
    featureDescriptions: [
      'Khôi phục mật khẩu vẫn đi cùng một trải nghiệm đăng nhập nhất quán trong toàn bộ cổng học vụ.',
      'Màn hình luôn giữ hướng dẫn khôi phục hiển thị thay vì đưa bạn vào ngõ cụt.',
      'Phản hồi được giữ mơ hồ có chủ đích để không xác nhận email có tồn tại hay không.',
    ],
    sectionEyebrow: 'Luồng khôi phục',
    heading: 'Quên mật khẩu',
    beforeSend: 'Nhập email của bạn và chúng tôi sẽ gửi hướng dẫn đặt lại mật khẩu.',
    afterSend: 'Bước tiếp theo đang ở trong hộp thư email của bạn.',
    emailLabel: 'Địa chỉ email',
    emailPlaceholder: 'you@university.edu',
    emailHint: 'Dùng địa chỉ được gắn với tài khoản nhà trường của bạn.',
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
      'Chọn mật khẩu mới cho tài khoản nhà trường của bạn. Sau khi xong, bạn sẽ đăng nhập lại bằng thông tin vừa cập nhật.',
    featureTitles: ['Một đường đi an toàn', 'Yêu cầu rõ ràng', 'Khôi phục nhất quán'],
    featureDescriptions: [
      'Liên kết đặt lại đưa bạn về cùng luồng đăng nhập được bảo vệ thay vì tách ra thành một trải nghiệm khác.',
      'Người dùng thấy yêu cầu mật khẩu và kiểm tra hợp lệ trước khi gửi biểu mẫu.',
      'Liên kết hết hạn hoặc không hợp lệ vẫn hiển thị bước khôi phục rõ ràng thay vì một trang lỗi.',
    ],
    invalidTitle: 'Liên kết đặt lại này không còn hợp lệ',
    invalidDescription:
      'Hãy yêu cầu một liên kết đặt lại mật khẩu mới và dùng email mới nhất để tiếp tục.',
    sectionEyebrow: 'Mật khẩu mới',
    heading: 'Đặt lại mật khẩu',
    subheading:
      'Hãy dùng mật khẩu bạn chưa dùng gần đây và giữ nó là duy nhất cho tài khoản nhà trường của bạn.',
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
    eyebrow: 'Cổng quản trị',
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
    backToDashboard: 'Quay lại tổng quan quản trị',
    mobileNavigation: 'Điều hướng quản trị trên điện thoại',
  },
  admin: {
    title: 'Tổng quan quản trị',
    overviewTitle: 'Tổng quan campus',
    description:
      'Quản lý tài khoản, hồ sơ học vụ, thông báo và luồng luận văn cốt lõi trong một cổng thống nhất.',
    managementConsoleTitle: 'Công cụ quản trị',
    managementConsoleDescription:
      'Mở khu vực cần xử lý. Mọi công cụ đều dùng cùng điều hướng và bước xác nhận rõ ràng.',
    menuItems: [
      ['Quản lý luận văn', 'Quản lý đợt đăng ký, đề tài, nhóm và tiến độ.'],
      ['Quản lý người dùng', 'Xem tài khoản nhà trường, trạng thái và phân quyền.'],
      ['Giảng viên', 'Quản lý hồ sơ giảng viên và phân công giảng dạy.'],
      ['Môn học', 'Duy trì danh mục, mã môn và thông tin môn học.'],
      ['Lớp học phần', 'Theo dõi sức chứa, người phụ trách và phòng học đi kèm.'],
      ['Đăng ký học', 'Kiểm tra kết quả đăng ký và các bước tiếp theo.'],
      ['Học kỳ', 'Kiểm soát dòng thời gian học vụ và cửa sổ đăng ký hiện tại.'],
      ['Bộ môn', 'Quản lý cấu trúc bộ môn và ánh xạ khoa.'],
      ['Phòng học', 'Theo dõi phòng, tòa nhà và mức sẵn sàng về sức chứa.'],
      ['Thông báo', 'Đăng cập nhật đến toàn trường.'],
      ['Kho tri thức trợ lý CampusCore', 'Quản lý nội dung campus công khai mà trợ lý CampusCore sử dụng.'],
      ['Diện mạo cổng', 'Đổi giao diện công khai và thứ tự thông báo.'],
    ],
    stats: ['Sinh viên', 'Giảng viên', 'Môn học', 'Đăng ký học'],
    statDetails: [
      'Hồ sơ người dùng có thể truy cập trong khu vực quản trị hiện tại.',
      'Tài khoản giảng viên đang hoạt động và danh tính phục vụ giảng dạy.',
      'Các môn học sẵn sàng cho việc mở lớp và đăng ký.',
      'Các hồ sơ đăng ký hiện có trong những màn học vụ.',
    ],
    loading: 'Đang tải tổng quan nhà trường',
    unavailableTitle: 'Tổng quan quản trị chưa sẵn sàng',
    unavailableDescription: 'Hiện chưa thể tải tổng quan nhà trường.',
    appearance: {
      title: 'Diện mạo cổng',
      description:
        'Đổi diện mạo cổng công khai và thứ tự thông báo. Các trang đang mở nhận thay đổi sau vài giây.',
      eyebrow: 'Giao diện cổng công khai',
      live: 'Đang hiện trên cổng công khai',
      saving: 'Đang lưu',
      saved: 'Đã phát hành',
      saveFailed: 'Hiện chưa thể lưu giao diện cổng.',
      loadFailed: 'Hiện chưa thể tải giao diện cổng.',
      heroEyebrow: 'Dòng dẫn trang chủ',
      heroTitle: 'Tiêu đề trang chủ',
      heroDescription: 'Mô tả trang chủ',
      heroHint: 'Để trống một trường nếu muốn giữ lời mặc định của cổng.',
      localeEn: 'Bản tiếng Anh',
      localeVi: 'Bản tiếng Việt',
      accent: 'Màu nhấn của cổng',
      accents: {
        'ute-yellow': 'Vàng UTE',
        'campus-gold': 'Vàng campus',
        'river-blue': 'Xanh sông',
      },
      preview: 'Xem trước trực tiếp',
      postsTitle: 'Thứ tự thông báo',
      postsDescription:
        'Đưa thông báo lên hoặc xuống. Bảng tin sinh viên và giảng viên theo thứ tự này ngay khi được lưu.',
      postsEmpty: 'Hãy phát hành một thông báo trước, rồi sắp xếp tại đây.',
      moveUp: 'Đưa lên',
      moveDown: 'Đưa xuống',
      openAnnouncements: 'Mở thông báo',
    },
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
        'Giữ trang hiện tại, tín hiệu học vụ và lối tắt đăng ký ở gần mà không làm chật cổng chính.',
      currentViewLabel: 'Màn hiện tại',
      signalsTitle: 'Tín hiệu nhanh',
      notificationLabel: 'Cập nhật chưa đọc',
      localeLabel: 'Ngôn ngữ',
      sessionSummary:
        'Khu sinh viên giữ cùng một lần đăng nhập khi bạn chuyển giữa đăng ký, lịch học và hồ sơ học tập.',
      quickActionsTitle: 'Lối tắt nhanh',
      collapse: 'Thu gọn cột ngữ cảnh sinh viên',
      expand: 'Mở rộng cột ngữ cảnh sinh viên',
      closeDrawer: 'Đóng cột ngữ cảnh sinh viên',
      quickLinks: {
        registration: {
          title: 'Kế hoạch đăng ký',
          description:
            'Xem lớp đang mở, danh sách chờ và cửa sổ đăng ký hiện tại.',
        },
        schedule: {
          title: 'Lịch học trong tuần',
          description:
            'Giữ lịch học kỳ này ở gần khi bạn so sánh các lớp.',
        },
        announcements: {
            title: 'Cập nhật từ nhà trường',
          description:
            'Mở các thông báo chung ảnh hưởng đến lớp học và hoạt động sinh viên.',
        },
      },
    },
    menu: {
      dashboard: 'Tổng quan',
      courseRegistration: 'Đăng ký học phần',
      myCourses: 'Môn học của tôi',
      schedule: 'Thời khóa biểu',
      grades: 'Điểm số',
      transcript: 'Bảng điểm',
      announcements: 'Thông báo',
      notifications: 'Trung tâm thông báo',
      thesis: 'Khu luận văn',
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
        'Hiện chưa có cảnh báo chưa đọc. Thông báo vẫn là kênh chính cho các cập nhật dùng chung.',
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
      openSidebar: 'Mở thanh điều hướng',
      closeSidebar: 'Đóng thanh điều hướng',
      collapseSidebar: 'Thu gọn thanh điều hướng',
      expandSidebar: 'Mở rộng thanh điều hướng',
      closeOverlay: 'Đóng lớp phủ điều hướng',
      openStudentRail: 'Mở cột ngữ cảnh sinh viên',
      closeStudentRailOverlay: 'Đóng lớp phủ cột ngữ cảnh sinh viên',
      toggleNotifications: 'Bật tắt bảng thông báo',
      toggleProfile: 'Bật tắt menu hồ sơ',
      mobileNavigation: 'Điều hướng cổng học vụ trên điện thoại',
      bottomNav: {
        dashboard: 'Trang chủ',
        schedule: 'TKB',
        courseRegistration: 'Đăng ký',
        grades: 'Điểm',
        teachingSchedule: 'TKB',
        gradeManagement: 'Điểm',
        thesis: 'Đề tài',
        menu: 'Menu',
      },
      skipToContent: 'Chuyển đến nội dung cổng thông tin',
      sidebarNavigation: 'Điều hướng cổng sinh viên và giảng viên',
      preferences: 'Tùy chọn',
    },
    pageDefaults: {
      description:
        'Đi giữa các bước hiện tại mà không rời khỏi cổng học vụ.',
      title: 'Cổng học vụ',
      fallbackDescription:
        'Di chuyển trong khu vực theo vai trò hiện tại với một lần đăng nhập thống nhất.',
    },
    signOutPage: {
      eyebrow: 'Kết thúc đăng nhập',
      title: 'Đang đăng xuất',
      description:
        'Hệ thống đang kết thúc lần đăng nhập hiện tại và đưa bạn trở lại màn hình đăng nhập.',
      progress: 'Đang hoàn tất đăng xuất...',
    },
    routeDescriptions: {
      dashboard:
        'Giữ đăng ký, môn học, lịch học và tác vụ hồ sơ trong cùng một khu sinh viên.',
      profile:
        'Giữ thông tin liên hệ và đổi mật khẩu cùng một chỗ trong tài khoản của bạn.',
      register:
        'Xem các lớp học phần và quản lý đăng ký cho học kỳ hiện tại.',
      enrollments:
        'Theo dõi các môn đang học và thông tin lớp học phần tương ứng.',
      schedule:
        'Giữ góc nhìn thời khóa biểu theo tuần trong tầm tay khi phần còn lại của cổng vẫn sẵn sàng.',
      grades: 'Xem điểm đã công bố và trạng thái học tập hiện tại.',
      transcript: 'Xem lịch sử học tập tích lũy và kết quả theo học kỳ.',
      announcements: 'Đọc các cập nhật dùng chung của nhà trường.',
      notifications:
        'Xem cảnh báo tài khoản, hạn học vụ và cập nhật quy trình trong một hộp thư tập trung.',
      lecturer:
        'Giữ tác vụ giảng dạy, hàng chờ chấm điểm, thông tin lớp và thông báo trong cùng một khu giảng viên.',
      lecturerSchedule: 'Theo dõi lớp được giao, phòng học và khung giờ lên lớp.',
      lecturerGrades:
        'Xem hàng chờ chấm điểm, lọc theo học kỳ và đẩy các lớp học phần sẵn sàng sang bước công bố.',
      lecturerAnnouncements:
        'Chia sẻ cập nhật với sinh viên trong các lớp của bạn.',
      thesis:
        'Theo dõi đợt đăng ký đề tài, nhóm sinh viên và tiến độ luận văn.',
    },
    loading: 'Đang tải cổng học vụ',
  },
  studentDashboard: {
    eyebrow: 'Khu sinh viên',
    title: 'Chào mừng quay lại, {name}',
    description:
      'Học kỳ hiện tại là {semester}. Di chuyển giữa đăng ký, môn học, lịch học và cập nhật hồ sơ mà không rời khỏi khu sinh viên.',
    currentTermFallback: 'Chưa có học kỳ hoạt động',
    currentDateLabel: 'Hôm nay',
    metrics: {
      coursesInScope: 'Môn học trong phạm vi',
      confirmedEnrollments: 'Đăng ký đã xác nhận',
      pendingDecisions: 'Mục chờ xử lý',
      currentSemester: 'Học kỳ hiện tại',
      details: [
        'Đăng ký, thông tin lớp và môn học hiện tại luôn ở cùng một khu sinh viên.',
        'Các lớp đã xác nhận luôn ở gần để bạn chuyển qua lịch học, điểm số và bảng điểm mà không mất thông tin.',
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
          'Giữ các màn hỗ trợ trong tầm tay mà vẫn ở trong cùng một lần đăng nhập an toàn.',
      },
      currentStatus: {
        title: 'Trạng thái hiện tại',
        description:
          'Một góc nhìn nhanh về ngữ cảnh học vụ đang dùng và các việc còn cần chú ý.',
        semesterSelectionTitle: 'Lựa chọn học kỳ',
        semesterSelectionActive:
          'Bảng điều khiển đang dùng {semester} làm học kỳ hiện tại.',
        semesterSelectionEmpty: 'Chưa có học kỳ ưu tiên nào đang hoạt động.',
        enrollmentHealthTitle: 'Tình trạng đăng ký',
        enrollmentHealthPending:
          'Vẫn còn {count} mục đăng ký cần được xử lý.',
        enrollmentHealthClear:
          'Không có vấn đề đăng ký nào đang chặn góc nhìn hiện tại.',
      },
    },
    quickActions: [
      ['Đăng ký học phần', 'Xem các lớp đang mở và đưa ra quyết định đăng ký.'],
      ['Mở thời khóa biểu', 'Kiểm tra lịch học của tuần này.'],
      ['Xem điểm số', 'Xem kết quả đã công bố và tình trạng học tập.'],
    ],
    portalLinks: [
      ['Môn học của tôi', 'Đăng ký hiện tại, thông tin lớp và trạng thái.'],
      ['Bảng điểm', 'Lịch sử theo học kỳ và kết quả học tập tích lũy.'],
      ['Thông báo', 'Cập nhật chung từ trường và các nhóm học phần.'],
    ],
    errors: {
      loadFailed: 'Hiện chưa thể tải dữ liệu khu sinh viên của bạn.',
      unavailableTitle: 'Khu sinh viên chưa sẵn sàng',
      loading: 'Đang tải khu sinh viên',
    },
  },
  profile: {
    eyebrow: 'Cài đặt tài khoản',
    title: 'Cài đặt hồ sơ',
    description:
      'Cập nhật thông tin cá nhân, giữ dữ liệu liên hệ mới nhất và đổi mật khẩu mà không rời khỏi cổng học vụ.',
    profileTitle: 'Hồ sơ tài khoản',
    profileDescription:
      'Giữ hồ sơ tài khoản khớp với thông tin mà nhà trường đang sử dụng.',
    profileUpdated: 'Đã cập nhật hồ sơ',
    profileSaveFailed: 'Hiện chưa thể lưu thay đổi hồ sơ.',
    passwordTitle: 'Mật khẩu và an toàn đăng nhập',
    passwordDescription:
      'Dùng mật khẩu mạnh và chờ đăng nhập lại sau khi đổi thành công.',
    passwordUpdated: 'Đã cập nhật mật khẩu',
    passwordUpdateFailed: 'Hiện chưa thể cập nhật mật khẩu.',
    whatChangesTitle: 'Những gì thay đổi ở đây',
    whatChanges: [
      'Chỉnh sửa hồ sơ sẽ cập nhật lại thông tin tài khoản sau khi lưu thành công.',
      'Đổi mật khẩu vẫn nằm trên trang tài khoản này sau khi bạn xác nhận.',
      'Các trường nhạy cảm như email do cán bộ nhà trường quản lý, không cho sửa trực tiếp trong biểu mẫu.',
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
      managedHint: 'Email do nhà trường quản lý.',
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
    eyebrow: 'Khu giảng viên',
    title: 'Chào mừng quay lại, {name}',
    description:
      'Giữ công việc lớp học, hàng chờ chấm điểm và cập nhật giảng dạy trong cùng một khu giảng viên.',
    quickActionsTitle: 'Tác vụ nhanh',
    quickActionsDescription:
      'Mở các công cụ giảng viên thường dẫn đến tác vụ tiếp theo trong ngày.',
    gradingQueueTitle: 'Hàng chờ chấm điểm',
    gradingQueueDescription:
      'Các lớp gần bước rà soát cuối luôn ở đây để việc chấm điểm vẫn là ưu tiên chính.',
    gradingQueueEmptyTitle: 'Chưa có phân công chấm điểm',
    gradingQueueEmptyDescription:
      'Các lớp có trách nhiệm chấm điểm sẽ xuất hiện tại đây khi được mở.',
    sectionsInScopeTitle: 'Các lớp đang phụ trách',
    sectionsInScopeDescription:
      'Các lớp được giao luôn hiển thị cùng sức chứa và bộ môn trước khi bạn đi sâu vào lịch hoặc chấm điểm.',
    sectionsInScopeEmptyTitle: 'Chưa có phân công giảng dạy',
    sectionsInScopeEmptyDescription:
      'Các lớp được giao sẽ xuất hiện tại đây ngay khi học kỳ hiện tại được thiết lập.',
    announcementsTitle: 'Thông báo mới nhất',
    announcementsDescription:
      'Các cập nhật ảnh hưởng đến giảng dạy được đưa lên đây mà không làm bạn rời khỏi khối lượng công việc hiện tại.',
    announcementsEmptyTitle: 'Chưa có thông báo mới',
    announcementsEmptyDescription:
      'Các thông báo chung cho khu giảng viên sẽ xuất hiện ở đây sau khi được đăng.',
    quickLinks: [
      ['Lịch giảng dạy', 'Kiểm tra phòng học, lớp học phần và thời gian dạy của học kỳ hiện tại.'],
      ['Quản lý điểm', 'Xử lý hàng chờ chấm điểm và đưa các lớp học phần đã sẵn sàng sang bước công bố.'],
      ['Thông báo', 'Xem các cập nhật chung ảnh hưởng đến lớp học phần và ngày giảng dạy của bạn.'],
    ],
    metrics: {
      labels: ['Lớp học phần', 'Sinh viên', 'Sẵn sàng công bố', 'Thông báo mới'],
      details: [
        'Các lớp học phần giảng dạy luôn hiển thị để quyết định về lịch và chấm điểm vẫn bám theo đúng học kỳ.',
        'Khối lượng đăng ký luôn ở gần khu giảng viên để việc theo dõi lớp học phần không bị mất.',
        'Công việc chấm điểm sẵn sàng công bố nổi lên sớm để không bị chìm giữa các việc khác.',
        'Các cập nhật chung ảnh hưởng đến giảng dạy vẫn hiện ra mà không kéo sự chú ý khỏi hàng chờ chấm điểm.',
      ],
    },
    queueStatusReady: 'Sẵn sàng công bố',
    queueStatusProgress: 'Đang xử lý',
    sectionPrefix: 'Lớp học phần',
    studentsSuffix: 'sinh viên',
    gradedSuffix: 'đã chấm',
    errors: {
      loadFailed: 'Hiện chưa thể tải dữ liệu học thuật của khu giảng viên.',
      unavailableTitle: 'Khu giảng viên chưa sẵn sàng',
      loading: 'Đang tải khu giảng viên',
    },
  },
  lecturerGrades: {
    eyebrow: 'Khu giảng viên',
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
      'Các lớp có trách nhiệm chấm điểm sẽ xuất hiện tại đây khi phân công giảng dạy đã sẵn sàng.',
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
    eyebrow: 'Khu luận văn',
    title: 'Giữ toàn bộ hành trình đề tài trong một tiến trình rõ ràng.',
    description:
      'Đợt đăng ký, lựa chọn đề tài, thành viên nhóm và tiến độ đã ghi nhận được nối với cùng tài khoản nhà trường.',
    selectRound: 'Chọn đợt đăng ký đề tài',
    noRound: 'Chưa có đợt đăng ký đề tài nào.',
    loading: 'Đang tải khu luận văn',
    loadFailed: 'Hiện chưa thể tải dữ liệu luận văn.',
    retry: 'Thử tải lại khu luận văn',
    roundStatus: 'Trạng thái đợt',
    registrationWindow: 'Cửa sổ đăng ký',
    topics: 'Đề tài đã công bố',
    groups: 'Nhóm đề tài',
    topicsTitle: 'Tìm hướng đề tài phù hợp với nhóm.',
    topicsDescription:
      'Các đề tài đã công bố được gom theo bộ môn. Lựa chọn của nhóm sẽ trở thành đề xuất để điều phối viên xét duyệt.',
    groupsTitle: 'Khu nhóm của bạn',
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
    backToWorkspace: 'Quay lại khu luận văn',
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
      returnToWorkspace: 'Quay lại khu vực của bạn',
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
    label: 'Trợ lý học vụ CampusCore',
    title: 'Trợ lý CampusCore',
    description: 'Hỏi về đăng ký học phần, lịch học, thông báo, học liệu, chính sách hoặc hành trình luận văn.',
    open: 'Mở trợ lý CampusCore',
    close: 'Đóng trợ lý CampusCore',
    placeholder: 'Hỏi về đăng ký, lịch học, thông báo hoặc hành trình luận văn…',
    send: 'Gửi tin nhắn',
    thinking: 'Đang kiểm tra kho kiến thức CampusCore công khai…',
    empty: 'Bắt đầu bằng một câu hỏi cụ thể về học vụ hoặc hành trình luận văn.',
    unavailable: 'Trợ lý hiện chưa sẵn sàng. Hồ sơ học vụ của bạn không bị thay đổi.',
    answered: 'Trả lời dựa trên hướng dẫn đã duyệt',
    noMatch: 'Chưa có hướng dẫn phù hợp',
    degraded: 'Hướng dẫn đã duyệt tạm thời chưa sẵn sàng',
    sources: 'Nguồn tham khảo',
    domains: {
      THESIS: 'Hướng dẫn luận văn',
      REGISTRATION: 'Đăng ký học phần',
      ACADEMIC_CATALOG: 'Danh mục học vụ',
      ANNOUNCEMENT: 'Thông báo',
      POLICY: 'Chính sách CampusCore',
      GENERAL_FAQ: 'Câu hỏi thường gặp',
    },
    you: 'Bạn',
    history: 'Lịch sử hội thoại',
    backToChat: 'Quay lại chat',
    newConversation: 'Hội thoại mới',
    deleteConversation: 'Xóa hội thoại',
    deleteConversationConfirm: 'Xóa hội thoại này? Tin nhắn sẽ bị xóa vĩnh viễn.',
    historyLoading: 'Đang tải hội thoại của bạn…',
    historyUnavailable: 'Lịch sử hội thoại tạm thời chưa sẵn sàng.',
    historyEmpty: 'Chưa có hội thoại nào được lưu.',
    untitledConversation: 'Hội thoại CampusCore',
    model: 'Ghi chú nguồn',
    stop: 'Dừng tạo câu trả lời',
    retry: 'Thử lại',
    quotaExceeded: 'Bạn đã chạm giới hạn trợ lý trong ngày. Hãy thử lại vào ngày mai hoặc tiếp tục với hướng dẫn có trích dẫn.',
    cancelled: 'Đã dừng tạo câu trả lời. Bạn có thể thử lại khi sẵn sàng.',
    feedbackUp: 'Đánh dấu câu trả lời hữu ích',
    feedbackDown: 'Đánh dấu câu trả lời chưa hữu ích',
    offline: 'Không có kết nối mạng. Hãy kiểm tra mạng rồi thử lại.',
    sessionExpired: 'Lần đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
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
