<template>
  <div class="container">
    <h2 class="homeTitle homeTitleBtn">
      库存预警
      <ul class="conTab">
        <li
          v-for="(item, index) in tabList"
          :key="index"
          :class="activeIndex === index ? 'active' : ''"
          @click="handleTab(index)"
        >
          <el-badge
            class="item"
            :class="item.count >= 10 ? 'badgeW' : ''"
            :value="item.count > 99 ? '99+' : item.count"
            :hidden="item.count === 0"
          >
            {{ item.label }}
          </el-badge>
        </li>
      </ul>
    </h2>
    <div>
      <el-table
        v-if="tableData.length > 0"
        :data="tableData"
        stripe
        class="tableBox"
        style="width: 100%"
      >
        <el-table-column prop="name" label="商品名称" min-width="140" />
        <el-table-column prop="categoryName" label="分类" min-width="80" />
        <el-table-column prop="stock" label="当前库存" min-width="80">
          <template slot-scope="scope">
            <span :class="scope.row.stock === 0 ? 'stock-zero' : 'stock-low'">{{ scope.row.stock }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="lowStockWarning" label="预警阈值" min-width="80" />
        <el-table-column prop="status" label="状态" min-width="60">
          <template slot-scope="scope">
            <span :class="scope.row.status === 1 ? 'status-on' : 'status-off'">{{ scope.row.status === 1 ? '上架' : '下架' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="100">
          <template slot-scope="scope">
            <el-button type="primary" size="mini" @click="handleAddStock(scope.row)">
              增加库存
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无预警" :image-size="80" />
    </div>

    <el-dialog title="增加库存" :visible.sync="dialogVisible" width="400px" :append-to-body="true" @close="handleClose">
      <el-form :model="addForm" label-width="100px">
        <el-form-item label="商品名称">
          <span>{{ addForm.productName }}</span>
        </el-form-item>
        <el-form-item label="当前库存">
          <span>{{ addForm.currentStock }}</span>
        </el-form-item>
        <el-form-item label="增加数量">
          <el-input-number v-model="addForm.quantity" :min="1" :max="999999" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleConfirm">确定</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { Component, Vue, Prop } from 'vue-property-decorator'
import { inventoryInbound } from '@/api/index'

@Component({
  name: 'InventoryWarning'
})
export default class extends Vue {
  @Prop({ type: Array, default: () => [] }) private zeroStockList!: any[]
  @Prop({ type: Array, default: () => [] }) private lowStockList!: any[]
  @Prop({ type: Number, default: 0 }) private zeroStockCount!: number
  @Prop({ type: Number, default: 0 }) private lowStockCount!: number

  private activeIndex = 0
  private dialogVisible = false
  private submitting = false
  private addForm: any = {
    productId: null,
    productName: '',
    currentStock: 0,
    quantity: 1
  }

  get tabList() {
    return [
      { label: '零库存商品', count: this.zeroStockCount },
      { label: '低库存商品', count: this.lowStockCount }
    ]
  }

  get tableData() {
    return this.activeIndex === 0 ? this.zeroStockList : this.lowStockList
  }

  private handleTab(index: number) {
    this.activeIndex = index
  }

  private handleAddStock(row: any) {
    this.addForm.productId = row.id
    this.addForm.productName = row.name
    this.addForm.currentStock = row.stock
    this.addForm.quantity = 1
    this.dialogVisible = true
  }

  private handleClose() {
    this.addForm.productId = null
    this.addForm.productName = ''
    this.addForm.currentStock = 0
    this.addForm.quantity = 1
  }

  private async handleConfirm() {
    this.submitting = true
    try {
      await inventoryInbound({
        productId: this.addForm.productId,
        quantity: this.addForm.quantity
      })
      this.$message.success('入库成功')
      this.dialogVisible = false
      this.$emit('refresh')
    } catch {
      this.$message.error('入库失败')
    } finally {
      this.submitting = false
    }
  }
}
</script>

<style scoped>
.stock-zero {
  color: #fd3333;
  font-weight: bold;
}
.stock-low {
  color: #e6a23c;
  font-weight: bold;
}
.status-on {
  color: #67c23a;
}
.status-off {
  color: #909399;
}
</style>
