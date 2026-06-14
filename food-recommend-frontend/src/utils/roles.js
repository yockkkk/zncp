/**
 * 角色常量
 */
export const ROLES = {
  WAITER: 'WAITER',
  OWNER: 'OWNER'
}

/** 角色中文名映射 */
export const ROLE_LABELS = {
  WAITER: '服务员',
  OWNER: '管理员'
}

/** 根据角色获取默认首页路径 */
export function getDefaultRoute(role) {
  return role === ROLES.OWNER ? '/owner/dashboard' : '/waiter/recommend'
}
