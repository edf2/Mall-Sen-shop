import Cookies from 'js-cookie'
import {
  setUserInfo,
  getUserInfo,
  removeUserInfo,
  setToken,
  getToken
} from '@/utils/cookies'

describe('Utils:cookies', () => {
  afterEach(() => {
    Cookies.remove('userInfo')
    Cookies.remove('token')
  })

  it('userInfo should round-trip through JSON serialization', () => {
    const info = {
      id: 1,
      name: 'zhangsan',
      token: 'jwt-token',
      stores: [{ storeId: 1 }]
    }
    setUserInfo(info)
    expect(getUserInfo()).toEqual(info)
  })

  it('getUserInfo returns null when the key is missing', () => {
    expect(getUserInfo()).toBeNull()
  })

  it('getUserInfo tolerates corrupted cookie value', () => {
    Cookies.set('userInfo', '[object Object]')
    expect(getUserInfo()).toBeNull()
  })

  it('token round-trips as a plain string', () => {
    setToken('abc123')
    expect(getToken()).toBe('abc123')
  })

  it('removeUserInfo clears the stored user info', () => {
    setUserInfo({ name: 'lisi' })
    removeUserInfo()
    expect(getUserInfo()).toBeNull()
  })
})
