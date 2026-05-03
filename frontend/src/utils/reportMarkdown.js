/**
 * 将分析报告 JSON 转换为可读的 Markdown 文本。
 * 用于对比页面和导出功能。
 */
export function reportToMarkdown(report, scoreLabels) {
  if (!report || typeof report === 'string') return ''
  const labels = scoreLabels || {
    codeQuality: '代码质量',
    structure: '项目结构',
    documentation: '文档',
    testing: '测试',
    dependencies: '依赖管理',
    security: '安全性',
    performance: '性能',
    maintainability: '可维护性'
  }

  let md = ''
  if (report.summary) md += `**项目概述**: ${report.summary}\n\n`
  if (report.primaryLanguage) md += `**主要语言**: ${report.primaryLanguage}\n\n`
  if (report.techStack?.length) md += `**技术栈**: ${report.techStack.join(', ')}\n\n`
  if (report.architecture) md += `**架构**: ${report.architecture}\n\n`

  if (report.score != null) {
    md += `**综合评分**: ${report.score}/100\n\n`
  }

  if (report.scoreDetails) {
    md += `| 维度 | 评分 |\n|------|------|\n`
    for (const [k, v] of Object.entries(report.scoreDetails)) {
      md += `| ${labels[k] || k} | ${v} |\n`
    }
    md += '\n'
  }

  if (report.modules?.length) {
    md += `**模块结构**\n\n| 模块 | 职责 |\n|------|------|\n`
    report.modules.forEach(m => { md += `| ${m.name} | ${m.description} |\n` })
    md += '\n'
  }

  if (report.strengths?.length) {
    md += `**优点**\n`
    report.strengths.forEach(s => { md += `- ${s}\n` })
    md += '\n'
  }

  if (report.improvements?.length) {
    md += `**改进建议**\n`
    report.improvements.forEach(s => { md += `- ${s}\n` })
    md += '\n'
  }

  if (report.securityRisks?.length) {
    md += `**安全风险**\n`
    report.securityRisks.forEach(s => { md += `- ${s}\n` })
    md += '\n'
  }

  if (report.performanceNotes?.length) {
    md += `**性能注意事项**\n`
    report.performanceNotes.forEach(s => { md += `- ${s}\n` })
    md += '\n'
  }

  if (report.keyDependencies?.length) {
    md += `**关键依赖**: ${report.keyDependencies.join(', ')}\n\n`
  }

  if (report.verdict) md += `**总结**: ${report.verdict}\n`

  return md
}

/**
 * 解析 report JSON 字符串，失败返回 null。
 */
export function parseReport(reportStr) {
  if (!reportStr) return null
  try {
    return JSON.parse(reportStr)
  } catch {
    return null
  }
}
