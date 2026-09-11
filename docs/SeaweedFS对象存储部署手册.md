# SeaweedFS 对象存储生产部署手册 (Debian 13 + Systemd + Nginx)

> [!IMPORTANT]
> **适用对象**：系统运维工程师、DevOps、后端开发人员  
> **设计目标**：为本系统提供高性能、高可靠、无商业化弹窗与限制的 S3 兼容对象存储服务，支撑处方影印件、工序调配拍照凭证、手持机验药照片以及应用发版 APK 的持久化安全存储。

---

## 1. 架构与安全模型

```mermaid
graph TD
    Client[外部客户端 / Web-Admin / 微信小程序] -->|HTTPS 443 (SSL加密)| Nginx[Nginx 反向代理]
    Client -.->|HTTP 80 访问| Redirect[301 自动跳转 HTTPS]
    Redirect --> Nginx
    
    subgraph Host[服务器内部 Localhost (安全隔离)]
        Nginx -->|S3 API 代理| S3[SeaweedFS S3 接口 :8333]
        Nginx -->|Web 管理界面 代理 (带密码)| Filer[Filer 文件管理器 :8888]
        S3 --> Master[Master 主控 :9333]
        S3 --> Volume[Volume 存储卷 :8085]
        Filer --> Master
        Filer --> Volume
        Volume --> Disk[(本地硬盘 /data/seaweedfs)]
    end
```

* **绝对安全隔离**：SeaweedFS 所有底层服务（Master、Volume、Filer、S3）全量绑定在本地回环地址 `127.0.0.1`，云服务器安全组仅对外开放 `80` 与 `443`。
* **原生 S3 兼容**：后端基于 `@aws-sdk/client-s3` 生成预签名直传（Presigned Put/Post）与带签名的私有下载链接（Presigned Get），桶权限保持私有即可正常工作。

---

## 2. 基础依赖与程序安装

更新 Debian 13 基础环境并安装必要工具：

```bash
sudo apt-get update -y
sudo apt-get install -y wget curl tar nginx apache2-utils certbot python3-certbot-nginx
```

### 安装 SeaweedFS 二进制程序
```bash
# 1. 下载官方最新的 64 位 Linux 预编译二进制包
wget https://github.com/seaweedfs/seaweedfs/releases/latest/download/linux_amd64.tar.gz

# 2. 解压并移动至系统路径
tar -zxvf linux_amd64.tar.gz
sudo mv weed /usr/local/bin/
rm -f linux_amd64.tar.gz

# 3. 验证安装结果
weed version
```

---

## 3. 存储路径与 S3 密钥配置

1. **创建工作与存储目录**：
   ```bash
   sudo mkdir -p /data/seaweedfs
   sudo mkdir -p /etc/seaweedfs
   ```

2. **配置 S3 访问密钥 (`/etc/seaweedfs/s3.json`)**：
   ```bash
   sudo vim /etc/seaweedfs/s3.json
   ```
   写入以下身份鉴权配置（**请务必将 `your_access_key` 和 `your_secret_key` 替换为您的高强度秘钥**）：
   ```json
   {
     "identities": [
       {
         "name": "tcm_admin",
         "credentials": [
           {
             "accessKey": "your_access_key",
             "secretKey": "your_secret_key"
           }
         ],
         "actions": [
           "Read",
           "Write",
           "List",
           "Tagging",
           "Admin"
         ]
       }
     ]
   }
   ```

---

## 4. Systemd 服务配置与开机自启

创建服务描述文件 `/etc/systemd/system/seaweedfs.service`：

```bash
sudo vim /etc/systemd/system/seaweedfs.service
```

写入如下内容：
```ini
[Unit]
Description=SeaweedFS Server
Documentation=https://github.com/seaweedfs/seaweedfs
Wants=network-online.target
After=network-online.target

[Service]
Type=simple
# 核心启动命令：指定数据目录、绑定 127.0.0.1、避开 8888 端口冲突、开启 S3 并加载鉴权文件
ExecStart=/usr/local/bin/weed server \
  -dir=/data/seaweedfs \
  -ip=127.0.0.1 \
  -ip.bind=127.0.0.1 \
  -volume.port=8085 \
  -s3 \
  -s3.config=/etc/seaweedfs/s3.json

Restart=always
RestartSec=5
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
```

启动并设置开机自启：
```bash
sudo systemctl daemon-reload
sudo systemctl start seaweedfs
sudo systemctl enable seaweedfs

# 查看运行状态 (确认呈现绿色的 active running)
sudo systemctl status seaweedfs
```

---

## 5. Nginx 反向代理配置

1. **为 Filer Web 文件管理器创建 Basic Auth 登录密码（避免未授权访问）**：
   ```bash
   sudo htpasswd -c /etc/nginx/.seaweedfs_htpasswd admin
   # 输入两次高强度密码
   ```

2. **创建 Nginx 站点配置 (`/etc/nginx/conf.d/seaweedfs.conf`)**：
   ```bash
   sudo vim /etc/nginx/conf.d/seaweedfs.conf
   ```
   写入以下基础 80 端口配置（**请先将 `s3.yourdomain.com` 与 `pan.yourdomain.com` 替换为真实解析到服务器的域名**）：

   ```nginx
   # 1. S3 API 服务代理
   server {
       listen 80;
       server_name s3.yourdomain.com;

       # S3 专用调优：允许无限制大文件上传，关闭缓存实现流式直传
       client_max_body_size 0;
       proxy_request_buffering off;
       proxy_buffering off;

       location / {
           proxy_pass http://127.0.0.1:8333;
           proxy_set_header Host $http_host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
           proxy_http_version 1.1;
       }
   }

   # 2. Filer Web 网页文件管理器 (管理员使用)
   server {
       listen 80;
       server_name pan.yourdomain.com;

       location / {
           auth_basic "SeaweedFS Private Storage";
           auth_basic_user_file /etc/nginx/.seaweedfs_htpasswd;

           proxy_pass http://127.0.0.1:8888;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
       }
   }
   ```

3. **测试并重新加载 Nginx**：
   ```bash
   sudo nginx -t && sudo systemctl reload nginx
   ```

---

## 6. HTTPS SSL 证书配置 (Let's Encrypt 自动续签)

确认域名 DNS 解析已生效后，执行一键证书申请：
```bash
sudo certbot --nginx -d s3.yourdomain.com -d pan.yourdomain.com
```
*根据交互提示输入邮箱，并在 Redirect 选项中选择自动重定向（将所有 HTTP 请求转为 HTTPS）。*

测试证书自动静默续签：
```bash
sudo certbot renew --dry-run
```

---

## 7. Web-Admin 后台系统配置对接

登录本系统 **Web 管理端 (PC)** ➔ **系统设置** ➔ **对象存储配置 (OSS/S3)**，对应填入以下参数：

| 配置项 | 推荐填写值 | 说明 |
| :--- | :--- | :--- |
| **存储类型** | `S3 兼容存储` | 本项目已内置完整的 S3 直传 SDK 适配 |
| **Endpoint（服务地址）** | `https://s3.yourdomain.com` | 指向 Nginx 反代的 S3 HTTPS 域名 |
| **Bucket（存储桶名）** | `tcm` | 系统首次上传会自动创建或在 Filer 新建文件夹 |
| **Access Key** | 步骤 3 中配置的 `accessKey` | 访问密钥 |
| **Secret Key** | 步骤 3 中配置的 `secretKey` | 密钥密码 |
| **Region（区域）** | `us-east-1` | 默认填此项即可 |
| **CDN 加速域名** | 留空（或填 `https://s3.yourdomain.com`） | 私有桶留空由后端生成预签名下载链接 |

---

## 8. 日常维护与一键平滑升级

### 常用状态检查
```bash
journalctl -u seaweedfs.service -f # 查看实时运行日志
sudo systemctl restart seaweedfs   # 重启 SeaweedFS
```

### 一键平滑升级脚本
执行以下命令即可安全升级版本（配置与持久化数据完全不受影响）：
```bash
sudo systemctl stop seaweedfs
wget -q https://github.com/seaweedfs/seaweedfs/releases/latest/download/linux_amd64.tar.gz -O /tmp/weed.tar.gz
tar -zxvf /tmp/weed.tar.gz -C /tmp/
sudo mv /tmp/weed /usr/local/bin/weed
rm -f /tmp/weed.tar.gz
sudo systemctl start seaweedfs
weed version
```
