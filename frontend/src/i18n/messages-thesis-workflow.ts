/**
 * Thesis-area copy (workflow stepper + regulation handbook).
 *
 * These components previously rendered Vietnamese literals directly, so the
 * English portal showed Vietnamese regulation text. Copy lives here rather than
 * in messages.ts to keep that file reviewable; the `vi` object is typed against
 * the widened `en` shape so a missing or extra key is a compile error.
 *
 * Text may contain `**bold**` markers. The guide renders them as inline
 * emphasis instead of splitting each sentence into several keys.
 */

type Widen<T> = T extends string
  ? string
  : T extends readonly (infer U)[]
    ? readonly Widen<U>[]
    : T extends object
      ? { readonly [K in keyof T]: Widen<T[K]> }
      : T;

export const thesisWorkflowEn = {
  stepper: {
    badge: 'Standard 5-stage process',
    authorityNote: '(Per the final-year project regulation — Faculty of IT, HCMUTE)',
    title: 'Project & graduation thesis workflow',
    collapse: 'Collapse guidance',
    expand: 'Show stage details',
    stageProgress: {
      done: 'Done',
      current: 'Current',
      upcoming: 'Upcoming',
    },
    stageHeading: 'Stage {step}: {title}',
    ownerLabel: 'Responsible:',
    basisLabel: 'Basis',
    regulationNote: 'Regulation note:',
    fallbackBeforeRegistration: 'Before the student registration window opens',
    fallbackBeforeDefence: 'Before the defence',
    fallbackPerRoundPlan: 'Per the round schedule',
    fallbackPerAssignment: 'Per the assignment schedule',
    reportDeadline: 'Report deadline: {date}',
    reviewerScoreDeadline: 'Reviewer score deadline to the Faculty: {date}',
    reportDate: 'Report date: {date}',
    stages: [
      {
        title: 'Lecturer proposes a topic',
        shortDesc: 'Department lecturer submits proposals',
        role: 'Department lecturers (1-2 supervisors)',
        rule: 'Articles R1, R2, R3',
        objective:
          'Lecturers in each department define the objectives, technology requirements and group quota for every topic.',
        actions: [
          'Each topic belongs to one department and has 1 to 2 supervising lecturers (primary and co-supervisor).',
          'The lecturer drafts a summary of the content, the technology used and the maximum number of groups (1-20 groups).',
          'The topic is stored as a draft or submitted to the Faculty council for approval.',
        ],
        deadlineNote: 'The topic submission deadline is set strictly by the Faculty schedule.',
      },
      {
        title: 'Review & publication',
        shortDesc: 'Faculty reviews & publishes',
        role: 'Head of Department & Faculty of IT',
        rule: 'Articles R2, R3',
        objective:
          'The academic council and the Head of Department assess the scientific merit, overlap and feasibility of each topic before publication.',
        actions: [
          'The Head of Department reviews the knowledge load and the feasibility for undergraduate students.',
          'The Faculty issues the official topic list, published openly on the academic portal.',
          'Students can browse the full topic list by specialised department.',
        ],
        deadlineNote: 'Once published, the topic list is ready for students to register in Stage 2.',
      },
      {
        title: 'Student group registers',
        shortDesc: 'Form a group of up to 3 students & pick a topic',
        role: 'Students & supervisors',
        rule: 'Articles R2, R4',
        objective:
          'Students form a research group and register exactly one topic from the published list.',
        actions: [
          'Each group has at most 3 students and exactly one leader as its representative.',
          'Each student may join only one group for the entire registration round.',
          'Each group registers exactly one topic; the supervisor approves it or rejects it with a reason.',
        ],
        deadlineNote: 'Groups may only be created and registered inside the Stage 2 window.',
      },
      {
        title: 'Work & report submission',
        shortDesc: 'Research & submit the report',
        role: 'Group leader (only the leader submits)',
        rule: 'Articles R4, R5',
        objective:
          'The group conducts the research, writes the thesis report, builds the product and submits the acceptance documents.',
        actions: [
          'The group works regularly under the academic guidance of its supervising lecturer.',
          'Article R5: ONLY THE GROUP LEADER may submit or update the thesis report (PDF/Google Drive/OneDrive).',
          'The reviewer lecturer reads the report and submits the review score to the Faculty.',
        ],
        deadlineNote: 'Per the round schedule',
      },
      {
        title: 'Defence & final score',
        shortDesc: 'A council of 3-5 lecturers marks the defence',
        role: 'Council of 3-5 lecturers & the chair',
        rule: 'Articles R6, R7, R8, R9',
        objective:
          'Students present before the thesis evaluation council. The council marks the work, averages the scores and publishes the result.',
        actions: [
          'The council has 3 to 5 lecturers (1 chair, 1 secretary and the members).',
          'Article R8: a supervising lecturer MUST NOT MARK a topic they supervise, so the assessment stays objective.',
          'Article R7: the final score is the arithmetic mean of the valid component scores from council members.',
          'Article R9: the chair finalises the score and the Faculty publishes the score, the classification and the comments on the system.',
        ],
        deadlineNote: "The result is recorded in the student's official graduation record.",
      },
    ],
  },
  guide: {
    badge: 'HCMUTE academic regulation',
    faculty: 'Faculty of Information Technology',
    title: 'Handbook: project & graduation thesis regulation (Provisions R1 — R13)',
    collapse: 'Collapse handbook',
    open: 'Open the 9 regulation articles',
    indexTitle: 'Article index',
    footer: 'Administrative and academic standards applied automatically across the CampusUTE system.',
    rules: [
      {
        number: 'Article R1',
        title: 'Classification of the 4 registration rounds & mandatory milestones',
        badge: 'Rounds & planning',
        lead:
          'The Dean approves and issues the topic registration round for the **4 official types** of the Faculty of Information Technology:',
        definitions: [
          {
            label: '1. Graduation thesis (KLTN):',
            text: 'The highest graduation form. It requires full configuration: the lecturer topic-submission window, the student registration window, the **reviewer score deadline to the Faculty**, and the **council defence date**.',
          },
          {
            label: '2. Specialised essay (TLCN):',
            text: 'A course that prepares students for graduation. It must define the **reviewer score deadline to the Faculty**.',
          },
          {
            label: '3. Scientific research (NCKH):',
            text: 'An in-depth applied research topic for students and lecturers.',
          },
          {
            label: '4. Course project (MON_HOC):',
            text: 'A practical project that integrates the specialised courses of the programme.',
          },
        ],
      },
      {
        number: 'Article R2',
        title: 'Two independent stages',
        badge: 'Implementation sequence',
        lead: 'Every topic round must follow **2 separate stages** with independent time windows:',
        bullets: [
          '**Stage 1 (Building & publishing topics):** department lecturers submit topic proposals; the departmental academic council assesses scientific merit and overlap, then publishes the official topic list.',
          "**Stage 2 (Student group registers a topic):** a student group registers one specific topic from the published list. Registration is only possible inside this stage's window.",
        ],
      },
      {
        number: 'Article R3',
        title: 'Topics managed per department & 1–2 supervising lecturers',
        badge: 'Specialisation & supervisors',
        lead:
          'Topics are managed strictly per department under the Faculty of IT (Computer Science, Data Engineering, Software Engineering, Information Systems, Computer Networks & Information Security):',
        bullets: [
          'Each topic belongs to one specific department, with a clear outline and expected outcomes.',
          'Supervised by **at least 1 and at most 2 lecturers** (supervisor 1 and supervisor 2).',
          'The supervising lecturer is responsible for monitoring the progress and the scientific quality of the topic.',
        ],
      },
      {
        number: 'Article R4',
        title: 'Student group rules & approval',
        badge: 'Group structure',
        bullets: [
          'Work is done in groups, **at most 3 students**, with **1 group leader** as the representative.',
          'Each student **may join only one group** for the whole round; nobody may be listed in two groups at once.',
          'Each group **registers exactly one topic** from the published list.',
          'After the student submits the request, the supervising lecturer reviews it and **approves** it or **rejects it with a reason**. Once approved, the group formally starts the topic.',
        ],
      },
      {
        number: 'Article R5',
        title: 'Report submission: only the group leader submits',
        badge: 'Submission responsibility',
        callout:
          '**Article R5 principle:** submitting the project report (PDF file, Google Drive or OneDrive link, presentation slides, source code) is done on the system **ONLY BY THE GROUP LEADER** on behalf of the group.',
        tail:
          "Group members may access and view the submitted report, the submission time and the leader's notes.",
      },
      {
        number: 'Article R6',
        title: 'Review council & member structure',
        badge: 'Defence council',
        bullets: [
          'The Faculty forms thesis evaluation councils; **each council has 3 to 5 lecturers**.',
          'The council structure is mandatory: **1 chair**, **1 secretary** and the **members**.',
          'The chair leads the defence session and consolidates every member\'s assessment and score into the final result.',
        ],
      },
      {
        number: 'Article R7',
        title: 'Score formula: arithmetic mean of the component scores',
        badge: 'Scoring formula',
        callout:
          '**Formula:** the final topic score = the **arithmetic mean** of the valid component scores given by the council members (10-point scale, rounded to 2 decimal places).',
        tail:
          'The score is then converted to a letter grade (A, B+, B, C+, C, D+, D, F) and to the official graduation classification on the credit scale of Ho Chi Minh City University of Technology and Engineering.',
      },
      {
        number: 'Article R8',
        title: 'Conflict-of-interest exclusion: supervisors may not mark their own topics',
        badge: 'Fairness & transparency',
        callout:
          '**Invariant rule:** a lecturer may mark many different topics on a council, but **MUST NEVER MARK** a topic that they supervise.',
        tail:
          "The system detects this automatically and locks the supervisor's score entry for that topic, guaranteeing objectivity, fairness and compliance with higher-education quality assurance standards.",
      },
      {
        number: 'Article R9',
        title: 'Public publication & lookup of results',
        badge: 'Score lookup',
        lead: 'After the review session ends and the council chair finalises the scores:',
        bullets: [
          'The evaluation results and scores are **published openly** on the academic portal.',
          "Students can look up the 10-point score, the letter grade, the graduation classification and the council's feedback in detail.",
        ],
      },
    ],
  },
  page: {
    classification: {
      EXCELLENT: 'Excellent',
      GOOD: 'Very good',
      FAIR: 'Good',
      UPPER_AVERAGE: 'Fairly good',
      AVERAGE: 'Average',
      BELOW_AVERAGE: 'Below average',
      PASS: 'Pass',
      RETAKE: 'Fail (re-defence required)',
    },
    groupLeaderFallback: 'Group leader',
    rejectedTopicFallback: 'Please contact your supervisor or choose another suitable topic.',
    defenceScoreRange: 'The defence score must be a number between 0.0 and 10.0.',
    councilScoresPending:
      'Every valid council member must submit a score before the result is finalised.',
    topicAlreadyFinalised: 'This topic score was already finalised.',
    thesisReportFallback: 'Graduation report',
    projectDocumentsFallback: 'Project documents',
    councilNameFallback: 'Faculty of IT council',
    scoreFinalised: 'Defence score finalised: {score} points.',
    supervisorLabel: 'Supervising lecturer (Article R3):',
    topicApprovedNotice:
      '**Topic officially approved by the supervisor (Article R4):** the group is cleared to start the research. The group leader prepares to submit the thesis report and slides under Article R5.',
    topicPendingNotice:
      "**Awaiting supervisor review (Article R4):** the topic request is pending the supervising lecturer's approval. The group leader can manage members while registration is open.",
    topicRejectedLabel: 'Topic rejected:',
    articleR5Suffix: '(Article R5)',
    reportRightsNotice:
      '**Article R5:** the right to submit or update the thesis report belongs to the **group leader** ({leader}). Members can view the submitted acceptance documents.',
    reportLinkHint:
      'Accepts Google Drive links (Anyone with the link), OneDrive, a GitHub repository or a direct PDF file.',
    assignedTopicsCount: '{count} assigned topics',
    thesisDocumentLabel: 'Thesis document:',
    tabTopicsAndSupervisors: '1. Topics & supervising groups (supervisors)',
    groupsCount: '{count} groups',
    tabCouncil: '2. Defence council (Articles R6 — R8)',
    councilsCount: '{count} councils',
    thesisReportLabel: 'Thesis report:',
    reportNotSubmitted: 'Thesis report not submitted yet',
    workspaceTitle: 'Student workbench & research group (Articles R4, R5, R9)',
    workspaceSubtitle:
      'Faculty of IT regulation: at most 3 students per group (Article R4), only the group leader submits the thesis report (Article R5), and scores are looked up transparently once the council finalises them (Article R9).',
    defenceCompleted: 'Defence completed',
    articlesR7R9: 'Articles R7 & R9',
    resultsHeading: 'Thesis evaluation result & score',
    publishedByCouncil: 'Officially published by the Faculty of IT council',
    defenceScoreTenScale: 'Defence score (10-point scale)',
    arithmeticMeanR7: 'Arithmetic mean (R7)',
    letterGradeLabel: 'Letter grade',
    gpaConversion: 'GPA conversion: {gpa}',
    graduationClassification: 'Graduation classification',
    creditScaleNote: 'On the credit scale',
    evaluationCouncil: 'Evaluation council',
    universityName: 'Ho Chi Minh City University of Technology and Engineering',
    yourRoleLabel: 'Your role:',
    roleGroupLeaderReport: 'Group leader (submits the report — R5)',
    roleMember: 'Member',
    roleGroupLeader: 'Group leader',
    roleAdmin: 'Administrator',
    roleLecturer: 'Lecturer',
    workspaceAreaLabel: 'Area:',
    facultySuffix: '(Faculty of IT - HCMUTE)',
  },
} as const;

export const thesisWorkflowVi: Widen<typeof thesisWorkflowEn> = {
  stepper: {
    badge: 'Quy trình chuẩn 5 giai đoạn',
    authorityNote: '(Theo Quy chế đồ án cuối kỳ — Khoa CNTT, HCMUTE)',
    title: 'Tiến trình thực hiện đề tài & khóa luận tốt nghiệp',
    collapse: 'Thu gọn hướng dẫn',
    expand: 'Xem chi tiết giai đoạn',
    stageProgress: {
      done: 'Đã xong',
      current: 'Hiện tại',
      upcoming: 'Sắp tới',
    },
    stageHeading: 'Giai đoạn {step}: {title}',
    ownerLabel: 'Chủ thể thực hiện:',
    basisLabel: 'Căn cứ',
    regulationNote: 'Lưu ý quy chế:',
    fallbackBeforeRegistration: 'Trước ngày mở đăng ký SV',
    fallbackBeforeDefence: 'Trước ngày phản biện',
    fallbackPerRoundPlan: 'Theo kế hoạch đợt',
    fallbackPerAssignment: 'Theo lịch phân công',
    reportDeadline: 'Hạn nộp báo cáo trước: {date}',
    reviewerScoreDeadline: 'Hạn chót GVPB nộp điểm về Khoa: {date}',
    reportDate: 'Ngày báo cáo: {date}',
    stages: [
      {
        title: 'GV đề xuất đề tài',
        shortDesc: 'GV bộ môn gửi đề xuất',
        role: 'Giảng viên Bộ môn (1-2 GVHD)',
        rule: 'Điều R1, R2, R3',
        objective:
          'Giảng viên thuộc các Bộ môn xây dựng mục tiêu, yêu cầu công nghệ và chỉ tiêu số lượng nhóm cho từng đề tài.',
        actions: [
          'Mỗi đề tài thuộc 1 Bộ môn cụ thể và có từ 1 đến 2 Giảng viên hướng dẫn (GVHD chính & GVHD phối hợp).',
          'Giảng viên soạn thảo tóm tắt nội dung, công nghệ sử dụng, và số nhóm tối đa (1-20 nhóm).',
          'Đề tài được lưu dạng bản nháp (Draft) hoặc gửi lên Hội đồng Khoa phê duyệt.',
        ],
        deadlineNote: 'Hạn cuối nộp đề tài được quy định nghiêm ngặt theo thời gian biểu của Khoa.',
      },
      {
        title: 'Thẩm định & công bố',
        shortDesc: 'Khoa xét duyệt & công bố',
        role: 'Trưởng Bộ môn & Khoa CNTT',
        rule: 'Điều R2, R3',
        objective:
          'Hội đồng Khoa học và Trưởng Bộ môn thẩm định tính khoa học, độ trùng lặp và tính khả thi của đề tài trước khi công bố.',
        actions: [
          'Trưởng bộ môn rà soát khối lượng kiến thức và tính khả thi đối với sinh viên đại học.',
          'Khoa ban hành danh mục đề tài chính thức được phê duyệt công khai trên cổng học vụ.',
          'Sinh viên có thể tra cứu toàn bộ danh mục đề tài theo từng bộ môn chuyên ngành.',
        ],
        deadlineNote: 'Sau khi công bố, danh mục đề tài sẽ sẵn sàng cho sinh viên đăng ký ở Giai đoạn 2.',
      },
      {
        title: 'Nhóm SV đăng ký',
        shortDesc: 'Lập nhóm ≤3 SV & chọn đề tài',
        role: 'Sinh viên & GVHD',
        rule: 'Điều R2, R4',
        objective:
          'Sinh viên thành lập nhóm nghiên cứu và đăng ký đúng 1 đề tài trong danh mục đã công bố.',
        actions: [
          'Mỗi nhóm tối đa 3 sinh viên, có đúng 1 Nhóm trưởng (Leader) đại diện.',
          'Mỗi sinh viên chỉ được tham gia duy nhất 1 nhóm trong toàn bộ đợt đăng ký.',
          'Mỗi nhóm chỉ đăng ký đúng 1 đề tài; Giảng viên hướng dẫn sẽ xét duyệt (Approve) hoặc từ chối (Reject kèm lý do).',
        ],
        deadlineNote: 'Chỉ được phép tạo nhóm và đăng ký trong khung giờ quy định của Giai đoạn 2.',
      },
      {
        title: 'Thực hiện & nộp báo cáo',
        shortDesc: 'Nghiên cứu & nộp báo cáo',
        role: 'Nhóm trưởng (Chỉ trưởng nhóm nộp)',
        rule: 'Điều R4, R5',
        objective:
          'Nhóm sinh viên tiến hành nghiên cứu, viết báo cáo luận văn, xây dựng sản phẩm và nộp tài liệu nghiệm thu.',
        actions: [
          'Nhóm sinh viên làm việc thường xuyên dưới sự chỉ dẫn khoa học của Giảng viên hướng dẫn.',
          'Quy chế Điều R5: CHỈ NHÓM TRƯỞNG mới có quyền nộp hoặc cập nhật báo cáo luận văn (PDF/Google Drive/OneDrive).',
          'Giảng viên phản biện (GVPB) đọc báo cáo và chấm điểm phản biện nộp về Khoa.',
        ],
        deadlineNote: 'Theo kế hoạch đợt',
      },
      {
        title: 'Bảo vệ & chốt điểm',
        shortDesc: 'Hội đồng 3-5 GV chấm bảo vệ',
        role: 'Hội đồng 3-5 GV & Chủ tịch',
        rule: 'Điều R6, R7, R8, R9',
        objective:
          'Sinh viên báo cáo trước Hội đồng đánh giá luận văn. Hội đồng chấm điểm, tổng hợp điểm trung bình và công bố kết quả.',
        actions: [
          'Hội đồng gồm 3 đến 5 giảng viên (1 Chủ tịch, 1 Thư ký, các Ủy viên).',
          'Quy tắc Điều R8: Giảng viên hướng dẫn KHÔNG ĐƯỢC CHẤM đề tài do chính mình hướng dẫn để đảm bảo tính khách quan.',
          'Quy tắc Điều R7: Điểm cuối cùng là TRUNG BÌNH CỘNG các điểm thành phần hợp lệ của thành viên hội đồng.',
          'Quy tắc Điều R9: Chủ tịch chốt điểm và Khoa công bố điểm số, xếp loại và nhận xét công khai trên hệ thống.',
        ],
        deadlineNote: 'Kết quả được lưu trữ chính thức vào hồ sơ tốt nghiệp đại học của sinh viên.',
      },
    ],
  },
  guide: {
    badge: 'Quy Chế Đào Tạo HCMUTE',
    faculty: 'Khoa Công nghệ Thông tin',
    title: 'Cẩm nang quy chế đề tài & khóa luận tốt nghiệp (Quy định R1 — R13)',
    collapse: 'Thu gọn cẩm nang',
    open: 'Mở xem 9 điều quy chế',
    indexTitle: 'Danh Mục Điều Khoản',
    footer: 'Quy chuẩn hành chính & học thuật được áp dụng tự động trong toàn bộ hệ thống CampusUTE.',
    rules: [
      {
        number: 'Điều R1',
        title: 'Phân loại 4 đợt đăng ký & các mốc thời gian bắt buộc',
        badge: 'Đợt & Kế hoạch',
        lead:
          'Trưởng khoa phê duyệt và ban hành đợt đăng ký đề tài cho **4 loại hình** chính thức của Khoa Công nghệ Thông tin:',
        definitions: [
          {
            label: '1. Khóa luận tốt nghiệp (KLTN):',
            text: 'Hình thức tốt nghiệp cao nhất. Bắt buộc cấu hình đầy đủ: Thời gian GV nộp đề tài, thời gian SV đăng ký, **hạn chót GVPB nộp điểm về Khoa**, và **ngày báo cáo Hội đồng đánh giá**.',
          },
          {
            label: '2. Tiểu luận chuyên ngành (TLCN):',
            text: 'Học phần chuẩn bị tốt nghiệp. Bắt buộc có **hạn chót GVPB nộp điểm về Khoa**.',
          },
          {
            label: '3. Nghiên cứu khoa học (NCKH):',
            text: 'Đề tài nghiên cứu ứng dụng chuyên sâu dành cho sinh viên và giảng viên.',
          },
          {
            label: '4. Đồ án môn học (MON_HOC):',
            text: 'Đồ án thực hành tích hợp các học phần chuyên môn trong chương trình đào tạo.',
          },
        ],
      },
      {
        number: 'Điều R2',
        title: 'Quy trình 2 giai đoạn độc lập',
        badge: 'Trình tự triển khai',
        lead: 'Mỗi đợt đề tài bắt buộc tuân thủ **2 giai đoạn riêng biệt** với khung thời gian độc lập:',
        bullets: [
          '**Giai đoạn 1 (Xây dựng & Công bố đề tài):** Giảng viên bộ môn nộp đề xuất đề tài; Hội đồng khoa học bộ môn thẩm định tính khoa học, độ trùng lặp và công bố danh sách đề tài chính thức.',
          '**Giai đoạn 2 (Nhóm SV đăng ký đề tài):** Nhóm sinh viên đăng ký 1 đề tài cụ thể trong danh sách đã công bố. Đăng ký chỉ được thực hiện trong thời gian quy định của giai đoạn này.',
        ],
      },
      {
        number: 'Điều R3',
        title: 'Quản lý đề tài theo từng bộ môn & 1–2 giảng viên hướng dẫn',
        badge: 'Chuyên môn & GVHD',
        lead:
          'Đề tài được quản lý chặt chẽ theo từng Bộ môn trực thuộc Khoa CNTT (Khoa học máy tính, Kỹ thuật dữ liệu, Kỹ thuật phần mềm, Hệ thống thông tin, Mạng máy tính & An ninh thông tin):',
        bullets: [
          'Mỗi đề tài thuộc 1 bộ môn cụ thể, có đề cương và yêu cầu kết quả rõ ràng.',
          'Được hướng dẫn bởi **ít nhất 1 và tối đa 2 Giảng viên** (GVHD 1 và GVHD 2).',
          'Giảng viên hướng dẫn chịu trách nhiệm đôn đốc tiến độ và chất lượng khoa học của đề tài.',
        ],
      },
      {
        number: 'Điều R4',
        title: 'Quy định nhóm sinh viên thực hiện & phê duyệt',
        badge: 'Cơ cấu nhóm',
        bullets: [
          'Thực hiện theo nhóm, **tối đa 3 sinh viên**, có **1 nhóm trưởng (Leader)** đại diện.',
          'Mỗi sinh viên **chỉ tham gia duy nhất 1 nhóm** trong toàn bộ đợt; không được đứng tên 2 nhóm cùng lúc.',
          'Mỗi nhóm **chỉ đăng ký duy nhất 1 đề tài** từ danh sách đã công bố.',
          'Sau khi sinh viên nộp nguyện vọng, Giảng viên hướng dẫn sẽ xem xét và **phê duyệt (Approve)** hoặc **từ chối (Reject kèm lý do)**. Khi được duyệt, nhóm chính thức thực hiện đề tài.',
        ],
      },
      {
        number: 'Điều R5',
        title: 'Quy chế nộp báo cáo: chỉ nhóm trưởng nộp báo cáo',
        badge: 'Trách nhiệm nộp',
        callout:
          '**Nguyên tắc Điều R5:** Việc nộp báo cáo đề tài (tập tin PDF, liên kết Google Drive, OneDrive, slide báo cáo, mã nguồn) **CHỈ DO NHÓM TRƯỞNG** đại diện nhóm thực hiện trên hệ thống.',
        tail:
          'Các thành viên trong nhóm có quyền truy cập để xem báo cáo đã nộp, thời gian nộp và ghi chú của nhóm trưởng.',
      },
      {
        number: 'Điều R6',
        title: 'Hội đồng phản biện & cơ cấu thành viên',
        badge: 'Hội đồng bảo vệ',
        bullets: [
          'Khoa thành lập các Hội đồng đánh giá luận văn, **mỗi hội đồng gồm từ 3 đến 5 Giảng viên**.',
          'Cơ cấu hội đồng bắt buộc gồm: **1 Chủ tịch**, **1 Thư ký**, và các **Ủy viên**.',
          'Chủ tịch hội đồng chủ trì phiên bảo vệ, tổng hợp đánh giá và điểm số của tất cả các thành viên để ra kết quả cuối cùng.',
        ],
      },
      {
        number: 'Điều R7',
        title: 'Công thức điểm số: trung bình cộng điểm thành phần',
        badge: 'Công thức tính điểm',
        callout:
          '**Công thức tính:** Điểm cuối cùng của đề tài = **Trung bình cộng số học** của các điểm thành phần hợp lệ do các thành viên Hội đồng chấm (thang điểm 10, làm tròn đến 2 chữ số thập phân).',
        tail:
          'Điểm số sau đó được quy đổi sang điểm chữ (A, B+, B, C+, C, D+, D, F) và xếp loại tốt nghiệp chính thức theo thang điểm tín chỉ của Trường ĐH Công nghệ Kỹ thuật TP.HCM.',
      },
      {
        number: 'Điều R8',
        title: 'Quy tắc loại trừ xung đột lợi ích: GVHD không được chấm đề tài của mình',
        badge: 'Công tâm & Minh bạch',
        callout:
          '**Quy tắc bất biến:** Giảng viên được tham gia chấm nhiều đề tài khác nhau trong hội đồng, nhưng **TUYỆT ĐỐI KHÔNG ĐƯỢC CHẤM** đề tài mà mình đang làm Giảng viên hướng dẫn (GVHD).',
        tail:
          'Hệ thống tự động phát hiện và khóa chức năng nhập điểm của GVHD đối với đề tài đó, đảm bảo 100% tính khách quan, công bằng và tuân thủ chuẩn kiểm định chất lượng giáo dục đại học.',
      },
      {
        number: 'Điều R9',
        title: 'Công bố & tra cứu kết quả công khai',
        badge: 'Tra cứu điểm',
        lead: 'Sau khi phiên phản biện kết thúc và Chủ tịch hội đồng chốt điểm:',
        bullets: [
          'Kết quả đánh giá và điểm số được **công bố công khai** trên cổng học vụ.',
          'Sinh viên tra cứu chi tiết điểm số thang 10, điểm chữ, xếp loại tốt nghiệp và các nhận xét đóng góp từ Hội đồng.',
        ],
      },
    ],
  },
  page: {
    classification: {
      EXCELLENT: 'Xuất sắc',
      GOOD: 'Giỏi',
      FAIR: 'Khá',
      UPPER_AVERAGE: 'Trung bình khá',
      AVERAGE: 'Trung bình',
      BELOW_AVERAGE: 'Trung bình yếu',
      PASS: 'Đạt',
      RETAKE: 'Không đạt (Bảo vệ lại)',
    },
    groupLeaderFallback: 'Nhóm trưởng',
    rejectedTopicFallback: 'Vui lòng liên hệ GVHD hoặc chọn đề tài khác phù hợp.',
    defenceScoreRange: 'Điểm bảo vệ phải là số từ 0.0 đến 10.0',
    councilScoresPending: 'Cần tất cả thành viên hội đồng hợp lệ chấm điểm trước khi chốt.',
    topicAlreadyFinalised: 'Điểm đề tài này đã được chốt trước đó.',
    thesisReportFallback: 'Báo cáo tốt nghiệp',
    projectDocumentsFallback: 'Tài liệu đồ án',
    councilNameFallback: 'Hội đồng Khoa CNTT',
    scoreFinalised: 'Đã chốt điểm thành công: {score} điểm.',
    supervisorLabel: 'Giảng viên hướng dẫn (Điều R3):',
    topicApprovedNotice:
      '**Đề tài đã được GVHD phê duyệt chính thức (Điều R4):** Nhóm đủ điều kiện triển khai nghiên cứu. Nhóm trưởng chuẩn bị nộp báo cáo luận văn và slide theo Điều R5.',
    topicPendingNotice:
      '**Đang chờ GVHD xét duyệt (Điều R4):** Nguyện vọng đề tài đang chờ Giảng viên hướng dẫn duyệt. Nhóm trưởng có thể quản lý thành viên trong thời gian mở đăng ký.',
    topicRejectedLabel: 'Đề tài bị từ chối:',
    articleR5Suffix: '(Điều R5)',
    reportRightsNotice:
      '**Quy chế Điều R5:** Quyền nộp hoặc cập nhật báo cáo luận văn thuộc về **Nhóm trưởng** ({leader}). Các thành viên xem tài liệu nghiệm thu đã nộp.',
    reportLinkHint:
      'Chấp nhận liên kết Google Drive (chế độ Anyone with the link), OneDrive, GitHub repo hoặc file PDF trực tiếp.',
    assignedTopicsCount: '{count} đề tài phân công',
    thesisDocumentLabel: 'Tài liệu luận văn:',
    tabTopicsAndSupervisors: '1. Đề tài & nhóm hướng dẫn (GVHD)',
    groupsCount: '{count} nhóm',
    tabCouncil: '2. Hội đồng chấm bảo vệ (Điều R6 — R8)',
    councilsCount: '{count} hội đồng',
    thesisReportLabel: 'Báo cáo luận văn:',
    reportNotSubmitted: 'Chưa nộp báo cáo luận văn',
    workspaceTitle: 'Bàn Làm Việc Sinh Viên & Nhóm Nghiên Cứu (Điều R4, R5, R9)',
    workspaceSubtitle:
      'Tuân thủ Quy chế Khoa CNTT: Tối đa 3 SV/nhóm (Điều R4), chỉ Nhóm trưởng nộp báo cáo luận văn (Điều R5), và tra cứu điểm số minh bạch sau khi Hội đồng chốt điểm (Điều R9).',
    defenceCompleted: 'Đã Hoàn Thành Bảo Vệ',
    articlesR7R9: 'Quy chế Điều R7 & Điều R9',
    resultsHeading: 'Kết quả đánh giá & điểm số khóa luận tốt nghiệp',
    publishedByCouncil: 'Công bố chính thức bởi Hội đồng Khoa CNTT',
    defenceScoreTenScale: 'Điểm Bảo Vệ (Thang 10)',
    arithmeticMeanR7: 'Trung bình cộng R7',
    letterGradeLabel: 'Điểm Chữ',
    gpaConversion: 'Quy đổi GPA: {gpa}',
    graduationClassification: 'Xếp loại tốt nghiệp',
    creditScaleNote: 'Theo thang điểm tín chỉ',
    evaluationCouncil: 'Hội đồng đánh giá',
    universityName: 'Trường ĐH Công nghệ Kỹ thuật TP.HCM',
    yourRoleLabel: 'Vai trò của bạn:',
    roleGroupLeaderReport: 'Nhóm trưởng (Đại diện nộp báo cáo - R5)',
    roleMember: 'Thành viên',
    roleGroupLeader: 'Nhóm trưởng',
    roleAdmin: 'Quản trị viên',
    roleLecturer: 'Giảng viên',
    workspaceAreaLabel: 'Khu vực:',
    facultySuffix: '(Khoa CNTT - HCMUTE)',
  },
};
