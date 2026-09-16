# 药房助手 iOS 管理员端

基于 **SwiftUI** 与 **Apple Vision Framework** 打造的原生 iOS 手持工作台。专为连锁中药房的一线药师、调剂员与库房盘点人员设计，与 Android 端功能 1:1 像素级对齐，提供极致丝滑的移动扫码、加工流水线打卡、库存盘点与取药核销体验。最低支持 `iOS 15.0+`（全面适配 iOS 17 与 Swift 6 严格并发模型）。

---

## 核心技术特性

- **声明式现代架构**：
  - 100% 采用 SwiftUI 原生构建，响应式状态绑定（`@StateObject`, `@ObservedObject`）。
  - 基于 `NavigationStack` 与全局单例 `Router`，彻底解耦页面导航与弹窗控制，支持深层嵌套子视图随意呼出全屏相机扫码器与页面重定向。
- **离线智能视觉 OCR 与条码识别**：
  - 基于原生 `AVFoundation` + 苹果 `Vision` 框架（`VNRecognizeTextRequest`）毫秒级低功耗离线解析。
  - **视觉容错纠正**：智能识别中药条码标签混淆字符（`O` 自动纠正为 `0`，`l/I` 自动纠正为 `1` 等）。
  - **屏幕中心就近算法**：解析视觉文字候选包围盒（Bounding Box），计算多候选项到视口中心的距离，优先命中取景框正中央的目标 SKU。
  - **离线安全**：全部 OCR 在神经引擎（Neural Engine）本地运行，无需后端联网计算。
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

## 本地开发与构建指南

### 环境要求

- macOS 14.0 (Sonoma) 或更高版本
- Xcode 15.0+ 或 16.0+（支持 iOS 17 SDK / Swift 5.9+ / Swift 6 并发模式）
- iOS 真机或模拟器（系统版本 iOS 15.0+）

### 运行调试步骤

1. 打开 Xcode，选择 **File -> Open...**，定位并选中 `ios-app/TCMAdmin` 所在目录或工程文件。
2. 配置签名证书（Signing & Capabilities）：
   - 选择自己的 Apple 开发者账号或个人免费团队证书。
3. 连接 iOS 设备（推荐真机，以完整体验摄像头扫码与 Vision OCR 实时分析）。
4. 点击 **Product -> Run**（快捷键 `Cmd + R`）启动编译并部署。

### 后端服务连接配置

首次启动应用后，在登录页右上角点击 **「服务器设置」**：
- **Mac 模拟器调试**：填入 `http://127.0.0.1:3000` 或 `http://localhost:3000`。
- **局域网真机调试**：填入运行 Fastify 后端电脑的局域网 IP（例如 `http://192.168.1.100:3000`）。
- **生产环境**：填入线上正式 API 域名（例如 `https://api.tcm.example.com`）。
