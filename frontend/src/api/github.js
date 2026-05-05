import request from './request'

/**
 * 获取 GitHub 用户公开信息（通过后端代理）
 */
export async function getUserProfile(username) {
  const data = await request.get(`/github/users/${username}`)
  return {
    login: data.login,
    name: data.name,
    avatar: data.avatar_url,
    bio: data.bio,
    publicRepos: data.public_repos,
    followers: data.followers,
    following: data.following,
    location: data.location,
    blog: data.blog,
    company: data.company,
    twitter: data.twitter_username,
    email: data.email,
    createdAt: data.created_at,
    updatedAt: data.updated_at
  }
}

/**
 * 获取用户公开仓库列表
 */
export async function getUserRepos(username, perPage = 100) {
  const data = await request.get(`/github/users/${username}/repos`, {
    params: { sort: 'pushed', per_page: perPage }
  })
  return data.map(r => ({
    name: r.name,
    fullName: r.full_name,
    url: r.html_url,
    description: r.description,
    language: r.language,
    stars: r.stargazers_count,
    forks: r.forks_count,
    issues: r.open_issues_count,
    size: r.size,
    updatedAt: r.updated_at,
    createdAt: r.created_at,
    pushedAt: r.pushed_at,
    fork: r.fork,
    topics: r.topics || [],
    license: r.license ? r.license.spdx_id : null,
    homepage: r.homepage
  }))
}

/**
 * 获取用户收藏的仓库
 */
export async function getUserStarred(username, perPage = 30) {
  const data = await request.get(`/github/users/${username}/starred`, {
    params: { per_page: perPage }
  })
  return data.map(r => ({
    name: r.name,
    fullName: r.full_name,
    url: r.html_url,
    description: r.description,
    language: r.language,
    stars: r.stargazers_count,
    forks: r.forks_count,
    updatedAt: r.updated_at,
    fork: r.fork,
    topics: r.topics || []
  }))
}

/**
 * 获取用户近期动态事件
 */
export async function getUserEvents(username, perPage = 100) {
  const data = await request.get(`/github/users/${username}/events`, {
    params: { per_page: perPage }
  })
  return data.map(e => ({
    id: e.id,
    type: e.type,
    repo: e.repo ? e.repo.name : '',
    repoUrl: e.repo ? `https://github.com/${e.repo.name}` : '',
    createdAt: e.created_at,
    payload: e.payload || {},
    isPublic: e.public
  }))
}

/**
 * 获取用户所属组织
 */
export async function getUserOrgs(username) {
  const data = await request.get(`/github/users/${username}/orgs`)
  return data.map(o => ({
    login: o.login,
    avatar: o.avatar_url,
    url: o.url,
    description: o.description
  }))
}

/**
 * 获取聚合的仓库统计数据
 */
export async function getUserRepoStats(username) {
  return request.get(`/github/users/${username}/stats`)
}

/** 刷新 GitHub 缓存 */
export async function refreshGithubCache(username) {
  return request.post(`/github/users/${username}/refresh`)
}
