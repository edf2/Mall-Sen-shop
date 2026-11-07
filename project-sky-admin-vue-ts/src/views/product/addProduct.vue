<template>
  <div :key="vueRest"
       class="addBrand-container"
  >
    <div :key="restKey"
         class="container"
    >
      <el-form ref="ruleForm"
               :model="ruleForm"
               :rules="rules"
               :inline="true"
               label-width="180px"
               class="demo-ruleForm"
      >
        <div>
          <el-form-item label="商品名称:"
                        prop="name"
          >
            <el-input v-model="ruleForm.name"
                      placeholder="请填写商品名称"
                      maxlength="20"
            />
          </el-form-item>
          <el-form-item label="商品分类:"
                        prop="categoryId"
          >
            <el-select v-model="ruleForm.categoryId"
                       placeholder="请选择商品分类"
            >
              <el-option v-for="(item, index) in categoryList"
                         :key="index"
                         :label="item.name"
                         :value="item.id"
              />
            </el-select>
          </el-form-item>
        </div>
        <div>
          <el-form-item label="商品价格:"
                        prop="price"
          >
            <el-input v-model="ruleForm.price"
                      placeholder="请设置商品价格"
            />
          </el-form-item>
        </div>
        <div>
          <el-form-item label="商品库存:"
                        prop="stock"
          >
            <el-input-number v-model="ruleForm.stock"
                             :min="0"
                             :max="999999"
                             label="库存数量"
            />
          </el-form-item>
          <el-form-item label="库存预警阈值:"
                        prop="lowStockWarning"
          >
            <el-input-number v-model="ruleForm.lowStockWarning"
                             :min="0"
                             :max="999999"
                             label="库存预警阈值"
            />
          </el-form-item>
        </div>

        <div>
          <el-form-item label="商品图片:"
                        prop="image"
          >
            <image-upload :prop-image-url="imageUrl"
                          @imageChange="imageChange"
            >
              图片大小不超过2M<br>仅能上传 PNG JPEG JPG类型图片<br>建议上传200*200或300*300尺寸的图片
            </image-upload>
          </el-form-item>
        </div>
        <div class="address">
          <el-form-item label="商品描述:"
                        prop="region"
          >
            <el-input v-model="ruleForm.description"
                      type="textarea"
                      :rows="3"
                      maxlength="200"
                      placeholder="商品描述，最长200字"
            />
          </el-form-item>
        </div>
        <div class="subBox address">
          <el-button @click="() => $router.back()">
            取消
          </el-button>
          <el-button type="primary"
                     :class="{ continue: actionType === 'add' }"
                     @click="submitForm('ruleForm')"
          >
            保存
          </el-button>
          <el-button v-if="actionType === 'add'"
                     type="primary"
                     @click="submitForm('ruleForm', 'goAnd')"
          >
            保存并继续添加
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import HeadLable from '@/components/HeadLable/index.vue'
import ImageUpload from '@/components/ImgUpload/index.vue'
import {
  queryProductById,
  addProduct,
  editProduct,
  getCategoryList
} from '@/api/product'
@Component({
  name: 'addShop',
  components: {
    HeadLable,
    ImageUpload
  }
})
export default class extends Vue {
  private restKey: number = 0
  private imageUrl: string = ''
  private actionType: string = ''
  private categoryList: string[] = []
  private vueRest = '1'
  private ruleForm = {
    name: '',
    id: '',
    price: '',
    code: '',
    image: '',
    description: '',
    status: true,
    categoryId: '',
    stock: 0,
    lowStockWarning: 10
  }

  get rules() {
    return {
      name: [
        {
          required: true,
          validator: (rule: any, value: string, callback: Function) => {
            if (!value) {
              callback(new Error('请输入商品名称'))
            } else {
              const reg = /^([A-Za-z0-9\u4e00-\u9fa5]){2,20}$/
              if (!reg.test(value)) {
                callback(new Error('商品名称输入不符，请输入2-20个字符'))
              } else {
                callback()
              }
            }
          },
          trigger: 'blur'
        }
      ],
      categoryId: [
        { required: true, message: '请选择商品分类', trigger: 'change' }
      ],
      image: {
        required: true,
        message: '商品图片不能为空'
      },
      price: [
        {
          required: true,
          // 'message': '请填写商品价格',
          validator: (rules: any, value: string, callback: Function) => {
            const reg = /^([1-9]\d{0,5}|0)(\.\d{1,2})?$/
            if (!reg.test(value) || Number(value) <= 0) {
              callback(
                new Error(
                  '商品价格格式有误，请输入大于零且最多保留两位小数的金额'
                )
              )
            } else {
              callback()
            }
          },
          trigger: 'blur'
        }
      ],
      stock: [
        {
          required: true,
          validator: (rules: any, value: number, callback: Function) => {
            if (value === undefined || value === null || value < 0) {
              callback(new Error('请填写商品库存'))
            } else {
              callback()
            }
          },
          trigger: 'blur'
        }
      ]
    }
  }

  created() {
    this.loadCategoryList()
    this.actionType = this.$route.query.id ? 'edit' : 'add'
    if (this.$route.query.id) {
      this.init()
    }
  }

  mounted() {}

  private async init() {
    queryProductById(this.$route.query.id).then(res => {
      if (res && res.data && res.data.code === 1) {
        this.ruleForm = { ...res.data.data }
        this.ruleForm.price = String(res.data.data.price)
        this.ruleForm.status = res.data.data.status === '1'
        this.imageUrl = res.data.data.image
      } else {
        this.$message.error(res.data.msg)
      }
    })
  }

  // 获取商品分类
  private loadCategoryList() {
    getCategoryList({}).then(res => {
      if (res.data.code === 1) {
        this.categoryList = res && res.data && res.data.data
      } else {
        this.$message.error(res.data.msg)
      }
    })
  }

  private submitForm(formName: any, st: any) {
    (this.$refs[formName] as any).validate((valid: any) => {
      if (valid) {
        // 兜底：确保 ruleForm.image 与 ImgUpload 组件显示的 imageUrl 完全一致
        // （防止 init 时 imageUrl 已赋值但 imageChange 还没触发 emit 的情况）
        if (!this.ruleForm.image && this.imageUrl) {
          this.ruleForm.image = this.imageUrl
        }
        // 严格空值校验：null / undefined / 空字符串 / 字面量"null"都不允许通过
        const img = (this.ruleForm.image || '').toString().trim()
        if (!img || img === 'null' || img === 'undefined') {
          this.$message.error('商品图片不能为空，请先上传图片')
          return
        }
        let params: any = { ...this.ruleForm }
        params.image = this.ruleForm.image  // 显式赋值，避免 spread 被意外覆盖
        params.status =
          this.actionType === 'add' ? 0 : this.ruleForm.status ? 1 : 0
        // params.price *= 100
        params.categoryId = this.ruleForm.categoryId
        if (this.actionType === 'add') {
          delete params.id
          addProduct(params)
            .then(res => {
              if (res.data.code === 1) {
                this.$message.success('商品添加成功！')
                if (!st) {
                  this.$router.push({ path: '/product' })
                } else {
                  this.imageUrl = ''
                  this.ruleForm = {
                    name: '',
                    id: '',
                    price: '',
                    code: '',
                    image: '',
                    description: '',
                    status: true,
                    categoryId: '',
                    stock: 0,
                    lowStockWarning: 10
                  }
                  this.restKey++
                }
              } else {
                this.$message.error(res.data.desc || res.data.msg)
              }
            })
            .catch(err => {
              this.$message.error('请求出错了：' + err.message)
            })
        } else {
          delete params.createTime
          delete params.updateTime
          editProduct(params)
            .then(res => {
              if (res && res.data && res.data.code === 1) {
                this.$router.push({ path: '/product' })
                this.$message.success('商品修改成功！')
              } else {
                this.$message.error(res.data.desc || res.data.msg)
              }
            })
            .catch(err => {
              this.$message.error('请求出错了：' + err.message)
            })
        }
      } else {
        return false
      }
    })
  }

  imageChange(value: any) {
    this.ruleForm.image = value
  }
}
</script>
<style lang="scss" scoped>
.addBrand-container {
  .el-form--inline .el-form-item__content {
    width: 293px;
  }

  .el-input {
    width: 350px;
  }

  .address {
    .el-form-item__content {
      width: 777px !important;
    }
  }
}
</style>
<style lang="scss" scoped>
.addBrand {
  &-container {
    margin: 30px;

    .container {
      position: relative;
      z-index: 1;
      background: #fff;
      padding: 30px;
      border-radius: 4px;
      min-height: 500px;

      .subBox {
        padding-top: 30px;
        text-align: center;
        border-top: solid 1px $gray-5;
      }
      .upload-item {
        .el-form-item__error {
          top: 90%;
        }
      }
    }
  }
}

</style>
