# 中药取药助手 Android 管理员端

Kotlin + Jetpack Compose 的管理员端原生 Android App，最低支持 Android 12（API 31）。当前版本已接入现有 Fastify 后端的管理员登录和概览统计接口，列表接口也已提供统一客户端封装；部分页面保留演示数据用于离线验证布局。

## 本地构建

需要 JDK 21、Android SDK 36 和 Gradle 8.10。构建 Debug APK：

```bash
gradle --no-daemon assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

## GitHub Actions

仓库中的 `.github/workflows/android-debug.yml` 会在 `android-app` 发生变更时自动构建并上传 Debug APK，无需配置正式签名证书。

## 后端地址与登录

Debug 包默认请求 `http://10.0.2.2:3000`，这是 Android 模拟器访问宿主机 localhost 的地址。真机调试或 Release 构建时，可通过 Gradle 参数或环境变量指定地址：

```bash
gradle assembleRelease -PAPI_BASE_URL=https://api.tcm.example.com
```

GitHub Actions 使用仓库 Secret `API_BASE_URL`；未配置时 Release 默认使用 `https://api.tcm.example.com`。登录使用后端 `POST /auth/login`，JWT 会在本次进程内用于后续 `/admin/*` 请求。

Debug 允许 HTTP 仅用于本地开发，Release 会强制关闭明文流量，因此正式 API 必须使用 HTTPS。

## Release 签名

正式签名需要在 GitHub Secrets 配置：

- `ANDROID_KEYSTORE_BASE64`：keystore 文件的 Base64 内容
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Release workflow 会将 Base64 临时还原为 `android-app/release.keystore`，构建签名 APK 后删除该文件；keystore 不会进入 Git。

首次生成 keystore（在本地安全目录执行）：

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias tcm-admin \
  -keyalg RSA -keysize 2048 -validity 10000
```

将文件转为 Base64 后填入 GitHub Secret：

```bash
base64 -w 0 release.keystore
```

Windows PowerShell 可使用：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('.\\release.keystore'))
```

本地签名构建时，把 keystore 放在 `android-app/release.keystore`，并设置四个环境变量；也可以通过 `-PANDROID_KEYSTORE_PASSWORD`、`-PANDROID_KEY_ALIAS` 和 `-PANDROID_KEY_PASSWORD` 传入。

已封装的真实接口包括：

- `GET /admin/stats`
- `GET /admin/packages`
- `GET /admin/e6-pharmacy/products`
- `GET /admin/product-differences/stats`
- `GET /admin/product-differences/logs`
- `GET /admin/yd-goods-check`
- `GET /admin/store-transfers`
