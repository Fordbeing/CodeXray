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
    createdAt: data.created_at
  }
}

/**
 * 获取用户公开仓库列表（通过后端代理）
 */
export async function getUserRepos(username, perPage = 10) {
  const data = await request.get(`/github/users/${username}/repos`, {
    params: { sort: 'updated', per_page: perPage }
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
    fork: r.fork
  }))
}

export async function getUserStarred(username, perPage = 10) {
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
    fork: r.fork
  }))
}
