<template>
  <div class="dashboard-container home">
    <!-- 营业数据 -->
    <Overview :overview-data="overviewData" />
    <!-- end -->
    <!-- 订单管理 -->
    <Orderview :orderview-data="orderviewData" />
    <!-- end -->
    <div class="homeMain">
      <!-- 商品总览 -->
      <CuisineStatistics :dishes-data="dishesData" />
      <!-- end -->
    </div>
    <!-- 库存预警 -->
    <InventoryWarning
      :zero-stock-list="inventoryData.zeroStockList"
      :low-stock-list="inventoryData.lowStockList"
      :zero-stock-count="inventoryData.zeroStockCount"
      :low-stock-count="inventoryData.lowStockCount"
      @refresh="getInventoryWarningData"
    />
    <!-- end -->
    <!-- 订单信息 -->
    <OrderList
      :order-statics="orderStatics"
      @getOrderListBy3Status="getOrderListBy3Status"
    />
    <!-- end -->
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import {
  getBusinessData,
  getDataOverView, // 营业数据
  getOrderData, // 订单管理今日订单
  getOverviewProducts, // 商品总览
  getInventoryOverview // 库存预警
} from '@/api/index'
import { getOrderListBy } from '@/api/order'
// 组件
// 营业数据
import Overview from './components/overview.vue'
// 订单管理
import Orderview from './components/orderview.vue'
// 商品总览
import CuisineStatistics from './components/cuisineStatistics.vue'
// 库存预警
import InventoryWarning from './components/inventoryWarning.vue'
// 订单列表
import OrderList from './components/orderList.vue'
@Component({
  name: 'Dashboard',
  components: {
    Overview,
    Orderview,
    CuisineStatistics,
    InventoryWarning,
    OrderList
  }
})
export default class extends Vue {
  private todayData = {} as any
  private overviewData = {}
  private orderviewData = {} as any
  private flag = 2
  private tateData = []
  private dishesData = {} as any
  private inventoryData: any = {
    zeroStockList: [],
    lowStockList: [],
    zeroStockCount: 0,
    lowStockCount: 0
  }
  private orderListData = []
  private counts = 0
  private page: number = 1
  private pageSize: number = 10
  private status = 2
  private orderStatics = {} as any
  created() {
    this.init()
  }
  init() {
    this.$nextTick(() => {
      this.getBusinessData()
      this.getOrderStatisticsData()
      this.getOverStatisticsData()
      this.getInventoryWarningData()
    })
  }
  // 获取营业数据
  async getBusinessData() {
    const data = await getBusinessData()
    this.overviewData = data.data.data
  }
  // 获取今日订单
  async getOrderStatisticsData() {
    const data = await getOrderData()
    this.orderviewData = data.data.data
  }
  // 获取商品总览数据
  async getOverStatisticsData() {
    const data = await getOverviewProducts()
    this.dishesData = data.data.data
  }
  // 获取库存预警数据
  async getInventoryWarningData() {
    const data = await getInventoryOverview()
    this.inventoryData = data.data.data
  }
  // 获取待审核，待配送，配送中数量
  getOrderListBy3Status() {
    getOrderListBy({})
      .then((res) => {
        if (res.data.code === 1) {
          this.orderStatics = res.data.data
        } else {
          this.$message.error(res.data.msg)
        }
      })
      .catch((err) => {
        this.$message.error('请求出错了：' + err.message)
      })
  }
}
</script>

<style lang="scss">
</style>
