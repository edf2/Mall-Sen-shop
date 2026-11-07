import request from '@/utils/request'

export const getPurchaseOrderPage = (params: any) =>
  request({
    url: `/purchase-order/page`,
    method: 'get',
    params
  })

export const getPurchaseOrderDetail = (id: number) =>
  request({
    url: `/purchase-order/${id}`,
    method: 'get'
  })

export const createPurchaseOrder = (data: any) =>
  request({
    url: `/purchase-order`,
    method: 'post',
    data
  })

export const updatePurchaseOrderStatus = (id: number, data: any) =>
  request({
    url: `/purchase-order/${id}/status`,
    method: 'put',
    data
  })

export const deletePurchaseOrder = (id: number) =>
  request({
    url: `/purchase-order/${id}`,
    method: 'delete'
  })
