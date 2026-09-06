# 药房助手 Android 管理员端

基于 Kotlin + Jetpack Compose 打造的原生 Android 手持工作台。该端专为门店药师、仓库人员和盘点员工设计，将 PC 端的复杂操作精简为适合移动扫码及现场作业的高效链路。最低支持 `Android 12 (API 31)`。

---

## 核心特性

- **现代架构**：100% Jetpack Compose 声明式 UI，MVVM 架构，Kotlin Coroutines + Flow。
- **离线智能视觉**：内置 Google ML Kit 离线引擎，实现毫秒级相机帧零拷贝解析。专供复杂的药房环境：
  - **连续帧防抖**：连续两帧识别一致后自动提取并回填结果。
  - **规则引擎剥离**：自动过滤 11 位手机号、13 位 UPC 条码、订单号等无效干扰项，精准抓取 `SKU`/`5KU` 标签前缀关联的 9 位目标数字码。
  - **无需联网**：不依赖后端 OCR，无隐私泄漏风险，断网也可完成初盘扫描。
- **柔性作业流**：加工完成时支持弹窗选择「立即生成待取包裹」或「暂缓生成包裹」（满足汤剂静置澄清沉淀、膏方凝胶冷却后装袋的实际生产周期需求）。
- **角色权限自适应**：基于服务端 RBAC 动态适配，普通店员账号可直接进行跨店调拨全流程打卡（发起/调入/出入库），而在 E6 处方池与库存差异模块自动切为纯只读安全模式。
- **在线热更新**：「关于」界面支持一键检查版本，下方自动按行纯文本罗列自 Git 历史自动提取的更新说明，并适配各品牌 Android 12+ 独立 FileProvider 安全安装。
- **业务闭环**：完整接入了 Fastify 后端的概览指标、包裹收发、E6 药材库检索、库存差异台账、药店库存初盘复盘以及跨门店借调。

---

## 本地开发构建

环境依赖：`JDK 21`、`Android SDK 36`、`Gradle 8.10`

### 构建 Debug APK

在项目根目录执行：

```bash
gradle --no-daemon assembleDebug
```

产物路径：`app/build/outputs/apk/debug/app-debug.apk`。

### 后端地址配置与登录

Debug 构建默认请求 `http://10.0.2.2:3000`（即 Android 模拟器访问宿主机的环回地址）。
真机调试或正式上线时，可动态注入真实接口地址：

```bash
gradle assembleRelease -PAPI_BASE_URL=https://api.tcm.example.com
```

- **登录流转**：调用后端 `POST /auth/login` 成功后，JWT 会持久化至本地 DataStore/SharedPreferences。
- **安全策略**：Debug 变体允许明文 HTTP 流量方便本地联调，Release 变体会强制关闭 Cleartext Traffic，要求所有网络请求必须使用 HTTPS。

---

## 自动化流水线 (CI/CD)

仓库已集成 GitHub Actions，代码变更时可自动完成构建与封包。

### 1. 自动构建 Debug
工作流文件 `.github/workflows/android-debug.yml` 监听推送，自动生成 Debug APK 并挂载为 GitHub Artifacts，无需任何签名证书即可下载安装测试。

### 2. 自动打包 Release 签名
若需流水线自动产出可上架/可分发的 Release APK，需在 GitHub 仓库中配置以下 Secrets：

- `ANDROID_KEYSTORE_BASE64`：你的 keystore 文件的 Base64 字符串
- `ANDROID_KEYSTORE_PASSWORD`：仓库密码
- `ANDROID_KEY_ALIAS`：密钥别名
- `ANDROID_KEY_PASSWORD`：密钥密码

#### 如何生成与配置 Keystore（首次执行）：

1. 生成密钥对：
   ```bash
   keytool -genkeypair -v \
     -keystore release.keystore \
     -alias tcm-admin \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. 转换为 Base64 填入 GitHub Secrets：
   - **Linux / macOS**:
     ```bash
     base64 -w 0 release.keystore
     ```
   - **Windows PowerShell**:
     ```powershell
     [Convert]::ToBase64String([IO.File]::ReadAllBytes('.\release.keystore'))
     ```

工作流会在云端将 Base64 还原为临时 `.keystore` 文件进行签名，随后立即安全擦除，绝对不会将证书本身提交至代码仓库。

---

## 代码目录结构

```text
app/src/main/java/com/tcm/admin/
├── MainActivity.kt        # 根容器，管理登录拦截壳与全局 Compose Navigation 路由
├── ApiClient.kt           # Ktor / Retrofit 等网络层统一封装
├── ScannerActivity.kt     # 独立的扫码容器与 ML Kit 硬件分析器层
└── ui/
    ├── AppModels.kt       # 领域模型 (包裹、盘点单、药材等 Data Class)
    ├── AppComponents.kt   # 全局可复用 Compose 原子组件（状态球、扫码输入框）
    └── screens/           # 页面级 Compose 视图，按功能模块分包
```
