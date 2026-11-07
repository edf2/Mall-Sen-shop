import Cookies from 'js-cookie'

// App
const sidebarStatusKey = 'sidebar_status'
export const getSidebarStatus = () => Cookies.get(sidebarStatusKey)
export const setSidebarStatus = (sidebarStatus: string) => Cookies.set(sidebarStatusKey, sidebarStatus)

// User
const storeId = 'storeId'
export const getStoreId = () => Cookies.get(storeId)
export const setStoreId = (id: string) => Cookies.set(storeId, id)
export const removeStoreId = () => Cookies.remove(storeId)

// User
const tokenKey = 'token'
export const getToken = () => Cookies.get(tokenKey)
export const setToken = (token: string) => Cookies.set(tokenKey, token)
export const removeToken = () => Cookies.remove(tokenKey)

// userInfo

const userInfoKey = 'userInfo'
export const getUserInfo = (): any => {
  const raw = Cookies.get(userInfoKey)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw)
  } catch (e) {
    return null
  }
}
export const setUserInfo = (useInfor: Object) => Cookies.set(userInfoKey, JSON.stringify(useInfor))
export const removeUserInfo = () => Cookies.remove(userInfoKey)

// printinfo

const printKey = 'print'
export const getPrint = (): any => {
  const raw = Cookies.get(printKey)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw)
  } catch (e) {
    return null
  }
}
export const setPrint = (useInfor: Object) => Cookies.set(printKey, JSON.stringify(useInfor))
export const removePrint = () => Cookies.remove(printKey)

// 获取消息
const newData = 'new'
export const getNewData = (): any => {
  const raw = Cookies.get(newData)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw)
  } catch (e) {
    return null
  }
}
export const setNewData = (val: Object) => Cookies.set(newData, JSON.stringify(val))
