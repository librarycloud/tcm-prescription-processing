# 药房助手 iOS 管理员端

基于 **SwiftUI** 打造的原生 iOS 手持工作台。专为连锁中药房的一线药师、调剂员与库房盘点人员设计，提供移动扫码、加工流水线打卡、库存盘点与取药核销功能。最低支持 `iOS 18.0+`；工程使用 Swift 5 语言模式并启用并发相关编译检查。

---

## 核心技术特性

- **声明式现代架构**：
  - 100% 采用 SwiftUI 原生构建，响应式状态绑定（`@StateObject`, `@ObservedObject`）。
  - 基于 `NavigationStack` 与全局单例 `Router`，彻底解耦页面导航与弹窗控制，支持深层嵌套子视图随意呼出全屏相机扫码器与页面重定向。
- **离线智能视觉 OCR 与条码识别**：
  - 基于 `AVFoundation` 相机采集、PaddleOCR ONNX 模型与本地 ONNX Runtime 完成离线识别；当前 OCR 推理使用 CPU 执行提供程序。
  - **视觉容错纠正**：智能识别中药条码标签混淆字符（`O` 自动纠正为 `0`，`l/I` 自动纠正为 `1` 等）。
  - **屏幕中心就近算法**：解析视觉文字候选包围盒（Bounding Box），计算多候选项到视口中心的距离，优先命中取景框正中央的目标 SKU。
  - **离线处理**：OCR 图像识别在设备本地完成，无需上传图像到后端进行推理。
- **多线程与极致性能**：
  - **二维码后台异步绘制**：使用独立 `Task.detached` 后台线程调度 `CoreImage` 引擎合成高清位图，彻底消除页面 Navigation 转场动画的撕裂与丢帧。
  - **布局抗坍塌机制**：针对全屏加载状态注入自适应约束，杜绝轻量占位导致的容器横向缩水。
- **全局优雅交互**：
  - **全屏空白点击收键盘**：通过挂载于顶层 `UIWindow` 的事件拦截代理（带有输入控件穿透保护），在任何页面点击空白处均可丝滑收起软键盘，同时绝不阻断列表点击或按钮操作。
  - **动态字体热重载**：内置 `ThemeManager` 字体比例引擎，全 App 460+ 处字体大小毫秒级无感知切换（无需重启 App）。
- **门店权限智能降级**：
  - 自适应员工账号权限。当非全局管理员无权请求全量门店列表时，自动安全忽略 403 错误并隐藏门店切换器，保障当前门店库存与业务无阻畅行。

---

## 工程结构

```text
ios-app/TCMAdmin/
├── TCMAdminApp.swift         # App 入口点与主题配置
├── Assets.xcassets/          # 图标与静态媒体资源
├── Models/
│   └── AppModels.swift       # 核心业务数据模型 (Codable)
├── Navigation/
│   └── AppRoute.swift        # 全局路由栈与单例调度中心
├── Network/
│   ├── ApiClient.swift       # Fastify RESTful API 客户端 (URLSession Async/Await)
│   └── SessionManager.swift  # 钥匙串持久化与用户凭据管理
├── Utils/
│   ├── Color+Theme.swift     # 动态色彩系统与十六进制转换
│   ├── ThemeManager.swift    # 主题色、暗色模式、动态字号控制器
│   ├── HapticManager.swift   # 触觉振动反馈
│   └── UpdateManager.swift   # 版本更新检测
└── Views/
    ├── MainShellView.swift   # 主界面 5 标签底部导航栏
    ├── Auth/                 # 登录与服务器配置
    ├── Inventory/            # 药品库存查询与批次详情
    ├── Herbs/                # 智能斗谱布局与药斗落位
    ├── Processing/           # 处方加工排产与煎药流水线打卡
    ├── Packages/             # 包裹生成、查询与快速核销
    ├── Stocktaking/          # 商品初盘复盘与盲盘录入
    ├── Transfers/            # 跨门店库存借调与归还
    ├── Differences/          # 库存差异台账与变动记录
    ├── E6Imports/            # 诊所处方同步与排产审核
    ├── Scanner/              # 工业级扫码器与视觉 OCR 组件
    ├── Profile/              # 个人中心、系统设置与关于
    └── Components/           # 全局通用 UI 控件库
```

---


## 响应式布局与 iPad 分屏适配 (Split View Architecture)

为了在 iPhone 和 iPad 上提供最佳的原生操作体验，本项目针对 iPad 等大屏设备（`horizontalSizeClass == .regular`）对核心业务列表页进行了深度的**左右分屏架构**适配。

- **动态 Master-Detail (主从) 视图**：
  在 iPad 宽屏模式下，左侧保留全量功能列表与搜索栏，右侧使用独立的 `VStack`/`HStack` 容器动态挂载详情模块（例如 `PackageDetailView`、`TransferDetailView`）。
- **极速状态刷新**：
  由于 SwiftUI 对右侧容器可能存在的重用缓存，分屏架构中针对所有子级 DetailView 注入了 `.id(detailId)` 令牌，强制在每次切换列表项时彻底销毁并重建右侧视图状态，实现了零卡顿、零假死的页面无缝秒切。
- **与 iPhone 端解耦隔离**：
  在小屏设备或 iPad 侧滑小窗（`horizontalSizeClass == .compact`）下，系统自动降级为原生遮罩 (`ZStack`) 或标准推入 (`NavigationStack`) 模式，且完美保留了独享的 `.allowsHitTesting(selectedProduct == nil)` 等移动端防误触机制。

目前已完成原生适配的核心模块包括：
- 库存查询 (`InventoryView`)
- 门店调拨 (`TransfersView`)
- 包裹核销 (`PackagesView`)
- 加工排产 (`ProcessingView`)
- 处方流转 (`PrescriptionsView`)
- 盘点复核 (`StocktakingView`)
- E6 进销存导入 (`E6ImportsView`)
- 斗谱落位 (`HerbsView`)

---

## 本地开发与构建指南

### 环境要求

- macOS 14.0 (Sonoma) 或更高版本
- Xcode 16.0+（支持 iOS 18 SDK）
- iOS 真机或模拟器（系统版本 iOS 18.0+）

### 运行调试步骤

1. 打开 Xcode，选择 **File -> Open...**，定位并选中 `ios-app/TCMAdmin` 所在目录或工程文件。
2. 配置签名证书（Signing & Capabilities）：
   - 选择自己的 Apple 开发者账号或个人免费团队证书。
3. 连接 iOS 设备（推荐真机，以完整体验摄像头扫码与本地 OCR 实时分析）。
4. 点击 **Product -> Run**（快捷键 `Cmd + R`）启动编译并部署。

### 后端服务连接配置

#### 1. 手动配置方式
- **未登录状态**：首次启动应用后，在登录页右上角点击 **「服务器设置」**（`server.rack` 图标）填入 Base URL。
- **已登录状态**：进入 **「我的」➔「设置」➔「系统与数据」➔「API 服务器地址」** 即可直接修改。
- **配置参考**：
  - **Mac 模拟器调试**：填入 `http://127.0.0.1:3000` 或 `http://localhost:3000`（可点击弹窗预设按钮）。
  - **局域网/内网调试**：可使用内网 IP 或主机名上的 HTTP 服务（例如 `http://192.168.1.100:3000`）；iOS 首次访问局域网时可能会询问本地网络权限。
  - **生产环境**：使用 HTTPS API 域名（例如 `https://api.tcm.example.com`）。应用仅对本地网络地址开放 ATS HTTP 例外。

#### 2. 专属链接一键导入（Deep Link）
可将配置链接发给员工，点击链接即可**自动唤起 App、完成配置并持久化保存**：

| 链接格式 | 示例 | 适用场景 |
| --- | --- | --- |
| **标准 HTTP 网页中转（推荐）** | `http://管理后台域名/app-config?server=https%3A%2F%2Fapi.tcm.yourdomain.com` | **微信聊天/微信内扫码必备**（防拦截） |
| **原生 Deep Link** | `tcmadmin://config?server=https://api.tcm.yourdomain.com` | 系统浏览器 / 备忘录 / 短信下发 |
| **局域网真机联调** | `tcmadmin://config?server=http://192.168.1.100:3000` | 药店内部 Wi-Fi 联调 |
| **超简短格式** | `tcm://config?server=http://192.168.1.100:3000` | 便于快速输入 |

> 💡 参数名兼容 `server`、`url`、`baseURL` 与 `api`（不区分大小写）；亦支持简写格式如 `tcmadmin://192.168.1.100:3000`。

#### 3. 解决微信内拦截（标准 HTTP 落地页机制）
微信内置浏览器默认会拦截自定义协议（如 `tcmadmin://`）。因此管理后台生成的配置二维码与快捷链接采用标准网页跳转：
- **访问路径**：`http://管理后台域名/app-config?server=...`
- **使用体验**：员工在**微信聊天窗口中直接点击链接**，或**在微信内扫描配置二维码**，会先打开一个精美的中转落地页，页面自动或由用户点击「**点击打开 App**」按钮，即可在微信中顺畅唤起并自动配置药房助手 App。

#### 4. 二维码扫码导入
将上述任意导入链接（HTTP 落地页链接或原生 Deep Link）生成为标准二维码：
1. **系统相机扫码**：员工打开 iPhone **系统相机**对准二维码，画面自动弹出 **“在「药房助手」中打开”** 黄色胶囊，轻点即可完成配置。
2. **微信扫码**：在微信中直接扫码，进入中转落地页后点击「点击打开 App」即可完成配置。

---

## GitHub Actions 自动化编译 Release (.ipa) 与发版

项目已配置好全自动编译、打包、签名与发布的 GitHub Actions 工作流（`.github/workflows/ios-release.yml`）。流水线可将版本信息同步至 App Release Hub；iOS 客户端的「检查新版本」会按当前 App Store storefront 查询对应区域的 App Store，不会从 Release Hub 获取或安装更新。

### 1. 准备仓库 Secrets（仅首次需配置）

进入 GitHub 仓库：**Settings ➔ Secrets and variables ➔ Actions**，点击 **New repository secret** 添加以下配置：

| Secret 变量名 | 必填 | 说明与获取方式 |
| --- | --- | --- |
| `IOS_BUILD_CERTIFICATE_BASE64` | 是 | 苹果发布证书（`.p12`）的 Base64 编码。终端生成命令：`base64 -i distribution.p12 \| pbcopy` |
| `IOS_P12_PASSWORD` | 是 | 导出 `.p12` 时设置的证书安全密码 |
| `IOS_PROVISION_PROFILE_BASE64` | 是 | 苹果描述文件（`.mobileprovision`）的 Base64 编码。终端生成命令：`base64 -i App.mobileprovision \| pbcopy` |
| `RELEASE_HUB_URL` | 否 | App Release Hub 根域名（例如 `https://release.example.com`，也可在 Actions 变量中设置，与 Android 通用） |
| `RELEASE_HUB_API_KEY` | 否 | Release Hub 服务端鉴权 API Key（与 Android 端通用） |
| `RELEASE_HUB_IOS_APP_ID` | 否 | iOS 专属 App ID（避免与 Android 的 `RELEASE_HUB_APP_ID` 冲突，默认 `tcm-admin-ios`） |

### 2. 触发方式

#### 方式 A：打 Git Tag 自动触发（推荐）
```bash
# 推送形如 ios-v1.0.0 的版本标签
git tag ios-v1.0.0
git push origin ios-v1.0.0
```

#### 方式 B：GitHub 网页手动点击触发
1. 访问 GitHub 仓库 ➔ **Actions** 标签页。
2. 左侧选择 **iOS Release IPA** 工作流。
3. 点击右侧 **Run workflow** 下拉框：
   - 填写版本号（如 `1.0.0`）。
   - 选择导出方式（`ad-hoc` 用于企业内测分发，`app-store` 用于上传 TestFlight / App Store）。
4. 点击绿色 **Run workflow** 开始自动化构建。

### 3. 构建产物与自动化闭环

构建完成后，流水线将自动执行以下动作：
1. **生成 IPA**：输出标准化 `tcm-admin-release.ipa`。
2. **生成元数据**：生成 `app-version.ios.json`，内含 sha256 校验和、文件大小与自动截取的 Git Changelog。
3. **发布 GitHub Release**：自动创建或更新 Release 并上传 IPA 与版本元数据。
4. **同步 App Release Hub 元数据**：配置了 Release Hub 凭据时，流水线会发起 `POST /admin/apps/{appId}/sync`。此同步用于 Release Hub 侧的版本管理；iOS 客户端检查更新仍以用户当前 storefront 对应的 App Store 上架版本为准。
