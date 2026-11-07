const path = require('path')
const name = 'Vue Typescript Admin'
const IS_PROD = ['production', 'development'].includes(process.env.NODE_ENV)

module.exports = {
  'publicPath': process.env.NODE_ENV === 'production' ? './' : '/', // TODO: Remember to change this to fit your need
  'lintOnSave': process.env.NODE_ENV === 'development',
  'pwa': {
    'name': name
  },
  'pluginOptions': {
    'style-resources-loader': {
      'preProcessor': 'scss',
      'patterns': [
        path.resolve(__dirname, 'src/styles/_variables.scss'),
        path.resolve(__dirname, 'src/styles/_mixins.scss')
      ]
    }
  },
  // 开启代理
  devServer: {
    host: '0.0.0.0',
    public: '0.0.0.0:8888', // 本地的ip:端口号
    port: 8888,
    open: true,
    disableHostCheck: true,
    hot: true, // 自动保存
    overlay: {
      warnings: false,
      errors: true
    },
    proxy: {
      // AI 对话统一走 Java 后端（/admin/aigc/**），由 Java 转发 Python Agent 并强制 admin 角色鉴权
      '/api': {
        target: process.env.VUE_APP_URL,
        ws: false,
        secure: false,
        changeOrigin: true,
        pathRewrite: {
          '^/api': ''
        },
        proxyTimeout: 120000,
        timeout: 120000
      },
      // 本地文件上传后的静态图片访问代理（映射到 Spring Boot 的 /upload/** 静态资源）
      // 例：<img src="/upload/xxx.png"> → http://后端:8080/upload/xxx.png → 读取本地磁盘文件
      '/upload': {
        target: 'http://localhost:8080',
        ws: false,
        secure: false,
        changeOrigin: true,
        proxyTimeout: 60000,
        timeout: 60000
      }
    }
  },
  chainWebpack: (config) => {
    config.resolve.symlinks(true) // 修复热更新失效
  },
  configureWebpack: {
    devtool: 'source-map'
  },

  css: {
    // 是否使用css分离插件 ExtractTextPlugin
    extract: IS_PROD,
    // 开启 CSS source maps?
    sourceMap: false,
    // css预设器配置项
    loaderOptions: {
    },
    // 启用 CSS modules for all css / pre-processor files.
    modules: false
  }
}
