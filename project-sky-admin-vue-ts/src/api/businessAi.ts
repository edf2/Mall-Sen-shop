import request from '@/utils/request'

export const chatWithAI = (data: any) =>
  request({
    url: '/aigc/chat',
    method: 'post',
    data
  })

export const getSessionList = (role: string, limit = 50) =>
  request({
    url: '/aigc/sessions',
    method: 'get',
    params: { role, limit }
  })

export const getSessionMessages = (sessionId: string) =>
  request({
    url: `/aigc/sessions/${sessionId}/messages`,
    method: 'get'
  })
