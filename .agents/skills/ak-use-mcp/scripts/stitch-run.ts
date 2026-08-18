import { MCPClientManager } from './mcp-client.js'

async function main() {
  const manager = new MCPClientManager()
  await manager.loadConfig(process.env.MCP_CONFIG_PATH)
  await manager.connectToServer('stitch')

  const action = process.argv[2]

  if (action === 'create_project') {
    const result = await manager.callTool('stitch', 'create_project', {
      title: 'Smart Student Management Portal'
    })
    console.log(JSON.stringify(result, null, 2))
  } else if (action === 'generate') {
    const projectId = process.argv[3]
    const result = await manager.callTool('stitch', 'generate_screen_from_text', {
      projectId,
      prompt:
        'Smart Student Management Portal dashboard for school administrators and teachers. Premium modern SaaS interface, desktop layout, sidebar navigation with menu items: Overview, Students, Attendance, Grades, Courses, Fees. Top bar with search and add student button. Hero panel showing campus health score. KPI cards for: Total Students, Attendance Today, Average Grade, Pending Fees. Class performance bar chart. Today schedule with events. Priority student table with columns: Student, Class, Attendance, GPA, Status. Clean accessible typography, glassmorphism accents, blue indigo emerald palette.',
      deviceType: 'DESKTOP'
    })
    console.log(JSON.stringify(result, null, 2))
  } else if (action === 'list_screens') {
    const projectId = process.argv[3]
    const result = await manager.callTool('stitch', 'list_screens', { projectId })
    console.log(JSON.stringify(result, null, 2))
  } else if (action === 'get_screen') {
    const projectId = process.argv[3]
    const screenId = process.argv[4]
    const result = await manager.callTool('stitch', 'get_screen', { projectId, screenId })
    console.log(JSON.stringify(result, null, 2))
  } else if (action === 'download') {
    const projectId = process.argv[3]
    const outputDir = process.argv[4]
    const result = await manager.callTool('stitch', 'download_assets', { projectId, outputDir })
    console.log(JSON.stringify(result, null, 2))
  }

  await manager.cleanup()
}

main().catch(err => { console.error(err); process.exit(1) })
