<template>
  <div class="dashboard-container">
    <div class="container">
      <div class="tableBar">
        <el-radio-group v-model="statusTab" @change="tabChange">
          <el-radio-button label="">
            全部
          </el-radio-button>
          <el-radio-button label="PENDING">
            待审核
          </el-radio-button>
          <el-radio-button label="APPROVED">
            已通过
          </el-radio-button>
          <el-radio-button label="RECEIVED">
            已收货
          </el-radio-button>
          <el-radio-button label="CANCELLED">
            已取消
          </el-radio-button>
        </el-radio-group>
        <div class="tableLab">
          <el-button type="primary" @click="openCreateDialog">
            + 新建采购单
          </el-button>
        </div>
      </div>

      <el-table v-if="tableData.length" :data="tableData" stripe class="tableBox">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="orderNumber" label="采购单号" width="180" />
        <el-table-column label="状态" width="100">
          <template slot-scope="scope">
            <el-tag :type="statusTag(scope.row.status)" size="small">
              {{ statusLabel(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="总金额" width="120">
          <template slot-scope="scope">
            ￥{{ scope.row.totalAmount.toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="notes" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="250" align="center">
          <template slot-scope="scope">
            <el-button type="text" size="small" class="blueBug" @click="viewDetail(scope.row.id)">
              查看
            </el-button>
            <el-button v-if="scope.row.status === 'PENDING'" type="text" size="small" class="blueBug" @click="handleStatus(scope.row.id, 'APPROVED')">
              通过
            </el-button>
            <el-button v-if="scope.row.status === 'APPROVED'" type="text" size="small" class="blueBug" @click="handleStatus(scope.row.id, 'RECEIVED')">
              收货
            </el-button>
            <el-button v-if="scope.row.status === 'PENDING' || scope.row.status === 'APPROVED'" type="text" size="small" class="delBut" @click="handleStatus(scope.row.id, 'CANCELLED')">
              取消
            </el-button>
            <el-button v-if="scope.row.status === 'RECEIVED' || scope.row.status === 'CANCELLED'" type="text" size="small" class="delBut" @click="handleDelete(scope.row.id)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <Empty v-else />

      <el-pagination v-if="counts > 10" class="pageList" :page-sizes="[10, 20, 30, 40]" :page-size="pageSize"
                     layout="total, sizes, prev, pager, next, jumper" :total="counts" @size-change="handleSizeChange"
                     @current-change="handleCurrentChange"
      />
    </div>

    <!-- 创建采购单对话框 -->
    <el-dialog title="新建采购单" :visible.sync="createDialogVisible" width="600px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="商品明细">
          <div v-for="(item, index) in newItems" :key="index" style="display:flex;gap:8px;margin-bottom:8px;">
            <el-select
              v-model="item.productId"
              filterable
              remote
              reserve-keyword
              placeholder="搜索商品名称"
              :remote-method="(q) => searchProduct(q, item)"
              :loading="productLoading"
              style="flex:1"
              @change="(val) => onProductChange(item, val)"
            >
              <el-option
                v-for="p in filteredProducts"
                :key="p.id"
                :label="p.name"
                :value="p.id"
              />
            </el-select>
            <el-input-number v-model="item.quantity" :min="1" style="width:140px" />
            <el-input-number v-model="item.unitPrice" :min="0" :precision="2" style="width:140px" />
            <el-button type="danger" icon="el-icon-delete" circle size="mini" @click="removeItem(index)" />
          </div>
          <el-button type="primary" icon="el-icon-plus" size="mini" @click="addItem">
            添加商品
          </el-button>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="newNotes" type="textarea" :rows="3" placeholder="备注信息（可选）" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确认创建</el-button>
      </span>
    </el-dialog>

    <!-- 采购单详情对话框 -->
    <el-dialog title="采购单详情" :visible.sync="detailDialogVisible" width="700px">
      <div v-if="detailData">
        <div style="display:flex;justify-content:space-between;margin-bottom:16px;">
          <span style="font-size:16px;font-weight:600;">采购单 #{{ detailData.id }}</span>
          <el-tag :type="statusTag(detailData.status)" size="medium">
            {{ statusLabel(detailData.status) }}
          </el-tag>
        </div>
        <div style="font-size:13px;color:#999;margin-bottom:16px;">
          单号：{{ detailData.orderNumber }} &nbsp;&nbsp; 创建时间：{{ detailData.createTime }}
        </div>
        <el-table :data="detailData.items || []" border style="width:100%;margin-bottom:16px;">
          <el-table-column prop="productName" label="商品" min-width="140" />
          <el-table-column prop="quantity" label="数量" width="80" align="right" />
          <el-table-column label="单价" width="100" align="right">
            <template slot-scope="s">
              ￥{{ s.row.unitPrice.toFixed(2) }}
            </template>
          </el-table-column>
          <el-table-column label="小计" width="100" align="right">
            <template slot-scope="s">
              ￥{{ s.row.subtotal.toFixed(2) }}
            </template>
          </el-table-column>
        </el-table>
        <div v-if="detailData.notes" style="padding:8px 12px;background:#fafafa;border-radius:4px;font-size:13px;color:#666;">
          <strong>备注：</strong>{{ detailData.notes }}
        </div>
      </div>
      <span slot="footer">
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button v-if="detailData && detailData.status === 'PENDING'" type="success" @click="detailAction('APPROVED')">通过</el-button>
        <el-button v-if="detailData && detailData.status === 'APPROVED'" type="primary" @click="detailAction('RECEIVED')">收货</el-button>
        <el-button v-if="detailData && (detailData.status === 'PENDING' || detailData.status === 'APPROVED')" type="warning" @click="detailAction('CANCELLED')">取消</el-button>
        <el-button v-if="detailData && (detailData.status === 'RECEIVED' || detailData.status === 'CANCELLED')" type="danger" @click="detailDelete">删除</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import {
  getPurchaseOrderPage,
  getPurchaseOrderDetail,
  createPurchaseOrder,
  updatePurchaseOrderStatus,
  deletePurchaseOrder
} from '@/api/purchaseOrder'
import { getProductPage } from '@/api/product'
import HeadLable from '@/components/HeadLable/index.vue'
import Empty from '@/components/Empty/index.vue'

@Component({
  name: 'PurchaseOrder',
  components: { HeadLable, Empty }
})
export default class extends Vue {
  private statusTab = ''
  private counts = 0
  private page = 1
  private pageSize = 10
  private tableData: any[] = []

  // 创建弹窗
  private createDialogVisible = false
  private newItems: any[] = []
  private newNotes = ''

  // 详情弹窗
  private detailDialogVisible = false
  private detailData: any = null
  private detailId: number | null = null

  // 商品列表
  private productList: any[] = []
  private filteredProducts: any[] = []
  private productLoading = false

  created() {
    this.init()
    this.loadProducts()
  }

  private statusTag(status: string) {
    const map: any = { PENDING: 'warning', APPROVED: 'success', RECEIVED: 'primary', CANCELLED: 'info' }
    return map[status] || ''
  }

  private statusLabel(status: string) {
    const map: any = { PENDING: '待审核', APPROVED: '已通过', RECEIVED: '已收货', CANCELLED: '已取消' }
    return map[status] || status
  }

  private tabChange() {
    this.page = 1
    this.init()
  }

  private async loadProducts() {
    try {
      const res = await getProductPage({ page: 1, pageSize: 1000 })
      if (res.data.code === 1) {
        this.productList = res.data.data.records || []
        this.filteredProducts = this.productList
      }
    } catch { /* ignore */ }
  }

  private searchProduct(query: string, item: any) {
    if (query !== '') {
      this.productLoading = true
      setTimeout(() => {
        this.filteredProducts = this.productList.filter(p =>
          p.name.toLowerCase().includes(query.toLowerCase())
        )
        this.productLoading = false
      }, 200)
    } else {
      this.filteredProducts = this.productList
    }
  }

  private async init() {
    try {
      const res = await getPurchaseOrderPage({
        page: this.page,
        pageSize: this.pageSize,
        status: this.statusTab || undefined
      })
      if (res.data.code === 1) {
        this.tableData = res.data.data.records || []
        this.counts = Number(res.data.data.total)
      }
    } catch (err) {
      this.$message.error('请求出错了：' + (err as any).message)
    }
  }

  // ── 创建采购单 ──
  private openCreateDialog() {
    this.newItems = [{ productId: null, productName: '', quantity: 1, unitPrice: 0 }]
    this.newNotes = ''
    this.createDialogVisible = true
  }

  private addItem() {
    this.newItems.push({ productId: null, productName: '', quantity: 1, unitPrice: 0 })
  }

  private onProductChange(item: any, productId: number) {
    const p = this.productList.find((p: any) => p.id === productId)
    if (p) {
      item.productId = p.id
      item.productName = p.name
      item.unitPrice = p.price || 0
    }
  }

  private removeItem(index: number) {
    this.newItems.splice(index, 1)
  }

  private async submitCreate() {
    const items = this.newItems.filter(i => i.productId && i.quantity > 0)
    if (!items.length) {
      this.$message.error('请至少添加一个有效商品')
      return
    }
    try {
      const res = await createPurchaseOrder({ items, notes: this.newNotes })
      if (res.data.code === 1) {
        this.$message.success('采购单创建成功')
        this.createDialogVisible = false
        this.init()
      } else {
        this.$message.error(res.data.msg || '创建失败')
      }
    } catch (err) {
      this.$message.error('创建失败：' + (err as any).message)
    }
  }

  // ── 状态操作 ──
  private async handleStatus(id: number, status: string) {
    const label = this.statusLabel(status)
    try {
      await this.$confirm(`确认${label}该采购单？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      const res = await updatePurchaseOrderStatus(id, { status })
      if (res.data.code === 1) {
        this.$message.success(`已${label}`)
        this.init()
      } else {
        this.$message.error(res.data.msg || '操作失败')
      }
    } catch { /* cancelled */ }
  }

  // ── 删除 ──
  private async handleDelete(id: number) {
    try {
      await this.$confirm('确认删除该采购单？', '确定删除', {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      })
      const res = await deletePurchaseOrder(id)
      if (res.data.code === 1) {
        this.$message.success('删除成功')
        this.init()
      } else {
        this.$message.error(res.data.msg || '删除失败')
      }
    } catch { /* cancelled */ }
  }

  // ── 详情 ──
  private async viewDetail(id: number) {
    try {
      const res = await getPurchaseOrderDetail(id)
      if (res.data.code === 1) {
        this.detailData = res.data.data
        this.detailId = id
        this.detailDialogVisible = true
      } else {
        this.$message.error(res.data.msg || '查询失败')
      }
    } catch (err) {
      this.$message.error('查询失败：' + (err as any).message)
    }
  }

  private async detailAction(status: string) {
    if (!this.detailId) return
    await this.handleStatus(this.detailId, status)
    if (this.detailDialogVisible) {
      this.detailDialogVisible = false
    }
  }

  private async detailDelete() {
    if (!this.detailId) return
    await this.handleDelete(this.detailId)
    if (this.detailDialogVisible) {
      this.detailDialogVisible = false
    }
  }

  // ── 分页 ──
  private handleSizeChange(val: number) {
    this.pageSize = val
    this.init()
  }

  private handleCurrentChange(val: number) {
    this.page = val
    this.init()
  }
}
</script>

<style lang="scss" scoped>
.dashboard {
  &-container {
    margin: 30px;
    .container {
      background: #fff;
      position: relative;
      z-index: 1;
      padding: 30px 28px;
      border-radius: 4px;
      .tableBar {
        margin-bottom: 20px;
        .tableLab {
          display: inline-block;
          float: right;
        }
      }
      .tableBox {
        width: 100%;
        border: 1px solid $gray-5;
        border-bottom: 0;
      }
      .pageList {
        text-align: center;
        margin-top: 30px;
      }
    }
  }
}
</style>
