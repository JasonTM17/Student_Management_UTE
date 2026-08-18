import type { CSSProperties } from 'react'
import './App.css'

type Student = {
  name: string
  id: string
  grade: string
  attendance: string
  gpa: string
  status: 'Excellent' | 'Watch' | 'Support'
}

const metrics = [
  { label: 'Active students', value: '2,846', change: '+12.4%', tone: 'blue' },
  { label: 'Attendance today', value: '94.8%', change: '+3.1%', tone: 'green' },
  { label: 'Average grade', value: '86.2', change: '+1.8 pts', tone: 'violet' },
  { label: 'Pending fees', value: '$18.4k', change: '-8.6%', tone: 'amber' },
]

const students: Student[] = [
  {
    name: 'Maya Tran',
    id: 'ST-2048',
    grade: '10A Robotics',
    attendance: '98%',
    gpa: '3.92',
    status: 'Excellent',
  },
  {
    name: 'Leo Nguyen',
    id: 'ST-1932',
    grade: '9B Science',
    attendance: '91%',
    gpa: '3.44',
    status: 'Watch',
  },
  {
    name: 'Ava Le',
    id: 'ST-1815',
    grade: '11C Arts',
    attendance: '96%',
    gpa: '3.78',
    status: 'Excellent',
  },
  {
    name: 'Minh Pham',
    id: 'ST-1764',
    grade: '8A Math',
    attendance: '84%',
    gpa: '2.96',
    status: 'Support',
  },
]

const events = [
  { time: '09:30', title: 'Parent meeting', detail: 'Grade 10 counseling room' },
  { time: '11:00', title: 'Chemistry lab audit', detail: 'Safety checklist due' },
  { time: '14:15', title: 'Scholarship review', detail: '12 candidates shortlisted' },
]

const navigation = ['Overview', 'Students', 'Attendance', 'Grades', 'Courses', 'Fees']

function App() {
  return (
    <main className="app-shell">
      <aside className="sidebar" aria-label="Main navigation">
        <div className="brand">
          <span className="brand-mark">S</span>
          <div>
            <strong>SmartSchool</strong>
            <span>Management Portal</span>
          </div>
        </div>

        <nav className="nav-list">
          {navigation.map((item) => (
            <a className={item === 'Overview' ? 'active' : ''} href={`#${item.toLowerCase()}`} key={item}>
              <span aria-hidden="true" />
              {item}
            </a>
          ))}
        </nav>

        <section className="advisor-card" aria-label="AI advisor summary">
          <p>AI Advisor</p>
          <strong>23 students need attention this week.</strong>
          <button type="button">View insights</button>
        </section>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">Academic year 2026</p>
            <h1>Smart Student Management Portal</h1>
          </div>
          <label className="search-box">
            <span>Search</span>
            <input placeholder="Find student, class, invoice..." type="search" />
          </label>
          <button className="primary-action" type="button">
            Add student
          </button>
        </header>

        <section className="hero-panel">
          <div>
            <p className="eyebrow">Live campus pulse</p>
            <h2>Every student signal in one calm command center.</h2>
            <p>
              Track attendance, academic risk, tuition status, and class health with a dashboard
              built for quick decisions instead of spreadsheet hunting.
            </p>
          </div>
          <div className="hero-score" aria-label="Campus health score">
            <span>Campus health</span>
            <strong>92%</strong>
            <small>Stable, +4.2% this month</small>
          </div>
        </section>

        <section className="metric-grid" aria-label="School metrics">
          {metrics.map((metric) => (
            <article className={`metric-card ${metric.tone}`} key={metric.label}>
              <span>{metric.label}</span>
              <strong>{metric.value}</strong>
              <small>{metric.change} vs last month</small>
            </article>
          ))}
        </section>

        <section className="content-grid">
          <article className="panel performance-panel">
            <div className="panel-header">
              <div>
                <p className="eyebrow">Performance</p>
                <h2>Class health trend</h2>
              </div>
              <span className="pill">Last 7 days</span>
            </div>
            <div className="chart" aria-label="Class performance bar chart">
              {[68, 84, 76, 91, 88, 95, 82].map((height, index) => (
                <span key={height + index} style={{ '--bar-height': `${height}%` } as CSSProperties}>
                  <small>{['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][index]}</small>
                </span>
              ))}
            </div>
          </article>

          <article className="panel events-panel">
            <div className="panel-header">
              <div>
                <p className="eyebrow">Today</p>
                <h2>Schedule</h2>
              </div>
            </div>
            <div className="event-list">
              {events.map((event) => (
                <div className="event-item" key={`${event.time}-${event.title}`}>
                  <time>{event.time}</time>
                  <div>
                    <strong>{event.title}</strong>
                    <span>{event.detail}</span>
                  </div>
                </div>
              ))}
            </div>
          </article>
        </section>

        <section className="panel student-panel" id="students">
          <div className="panel-header">
            <div>
              <p className="eyebrow">Student intelligence</p>
              <h2>Priority roster</h2>
            </div>
            <button className="ghost-button" type="button">
              Export report
            </button>
          </div>
          <div className="student-table" role="table" aria-label="Priority student roster">
            <div className="table-row table-head" role="row">
              <span role="columnheader">Student</span>
              <span role="columnheader">Class</span>
              <span role="columnheader">Attendance</span>
              <span role="columnheader">GPA</span>
              <span role="columnheader">Status</span>
            </div>
            {students.map((student) => (
              <div className="table-row" role="row" key={student.id}>
                <span role="cell">
                  <strong>{student.name}</strong>
                  <small>{student.id}</small>
                </span>
                <span role="cell">{student.grade}</span>
                <span role="cell">{student.attendance}</span>
                <span role="cell">{student.gpa}</span>
                <span role="cell">
                  <mark className={`status ${student.status.toLowerCase()}`}>{student.status}</mark>
                </span>
              </div>
            ))}
          </div>
        </section>
      </section>
    </main>
  )
}

export default App
