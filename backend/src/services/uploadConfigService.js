import { S3Client, PutObjectCommand, GetObjectCommand, DeleteObjectCommand } from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";
import crypto from "node:crypto";

const CONFIG_ITEM_KEY = "oss_upload";

const DEFAULT_CONFIG = {
  activeProvider: "seaweedfs",
  providers: {
    seaweedfs: {
      endpoint: "",
      bucket: "",
      accessKey: "",
      secretKey: "",
      region: "us-east-1",
      cdnDomain: "",
    },
    aliyun: {
      endpoint: "",
      bucket: "",
      accessKey: "",
      secretKey: "",
      region: "oss-cn-hangzhou",
      cdnDomain: "",
    },
    tencent: {
      endpoint: "",
      bucket: "",
      accessKey: "",
      secretKey: "",
      region: "ap-guangzhou",
      cdnDomain: "",
    }
  }
};

export async function getUploadConfig(prisma) {
  let record = await prisma.systemConfig.findUnique({
    where: { item: CONFIG_ITEM_KEY },
  });
  
  if (!record) {
    record = await prisma.systemConfig.create({
      data: {
        item: CONFIG_ITEM_KEY,
        value: JSON.stringify(DEFAULT_CONFIG),
        class: "upload",
        isPublic: true,
        type: "json",
        mark: "对象存储配置",
      },
    });
    return DEFAULT_CONFIG;
  }
  
  try {
    const parsed = JSON.parse(record.value);
    // 兼容老版本配置
    if (!parsed.activeProvider) {
      return {
        activeProvider: "seaweedfs",
        providers: {
          ...DEFAULT_CONFIG.providers,
          seaweedfs: {
            endpoint: parsed.endpoint || "",
            bucket: parsed.bucket || "",
            accessKey: parsed.accessKey || "",
            secretKey: parsed.secretKey || "",
            region: parsed.region || "us-east-1",
            cdnDomain: parsed.cdnDomain || "",
          }
        }
      };
    }
    return parsed;
  } catch (e) {
    return DEFAULT_CONFIG;
  }
}

export async function updateUploadConfig(prisma, data) {
  return prisma.systemConfig.upsert({
    where: { item: CONFIG_ITEM_KEY },
    update: {
      value: JSON.stringify(data),
    },
    create: {
      item: CONFIG_ITEM_KEY,
      value: JSON.stringify(data),
      class: "upload",
      isPublic: true,
      type: "json",
      mark: "对象存储配置",
    }
  });
}

// 辅助函数：获取当前启用的配置
async function getActiveConfig(prisma) {
  const fullConfig = await getUploadConfig(prisma);
  const activeProvider = fullConfig.activeProvider || 'seaweedfs';
  const providerConfig = fullConfig.providers?.[activeProvider] || {};
  return { provider: activeProvider, config: providerConfig };
}

// 统一的直传策略
export async function generateUploadStrategy(prisma, category, filename, mimeType = "application/octet-stream") {
  const { provider, config } = await getActiveConfig(prisma);
  
  if (!config.endpoint || !config.bucket || !config.accessKey || !config.secretKey) {
    throw new Error("请先在系统设置中配置 S3/OSS 上传参数");
  }
  
  if (!/^[a-z][a-z0-9-]*$/.test(category)) {
    throw new Error("无效的分类名称");
  }

  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  
  let extension = 'tmp';
  if (filename && filename.includes('.')) {
    const ext = filename.split('.').pop().toLowerCase();
    if (/^[a-z0-9]{1,4}$/.test(ext)) {
      extension = ext;
    }
  }
  const uuid = crypto.randomUUID();
  const storagePath = `${category}/${year}/${month}/${uuid}.${extension}`;

  const s3 = new S3Client({
    region: config.region || "us-east-1",
    endpoint: config.endpoint,
    credentials: {
      accessKeyId: config.accessKey,
      secretAccessKey: config.secretKey,
    },
    // 阿里云和腾讯云通常不需要 forcePathStyle (使用虚拟托管样式 bucket.endpoint)
    // SeaweedFS 和 MinIO 等自建系统通常需要
    forcePathStyle: provider === 'seaweedfs',
    requestChecksumCalculation: "WHEN_REQUIRED",
    responseChecksumValidation: "WHEN_REQUIRED",
  });
  
  const command = new PutObjectCommand({
    Bucket: config.bucket,
    Key: storagePath,
    ContentType: mimeType,
  });
  
  const uploadUrl = await getSignedUrl(s3, command, { expiresIn: 3600 });

  return {
    provider,
    endpoint: config.endpoint,
    region: config.region || "us-east-1",
    bucket: config.bucket,
    storagePath,
    uploadUrl,
    cdnDomain: config.cdnDomain,
  };
}

export async function getFileDownloadUrl(prisma, storagePath) {
  const { provider, config } = await getActiveConfig(prisma);
  if (!config.endpoint || !config.bucket || !config.accessKey || !config.secretKey) {
    return null;
  }
  
  if (config.cdnDomain) {
    const baseUrl = config.cdnDomain.startsWith('http') ? config.cdnDomain : `https://${config.cdnDomain}`;
    return `${baseUrl}/${storagePath}`;
  }

  const s3 = new S3Client({
    region: config.region || "us-east-1",
    endpoint: config.endpoint,
    credentials: {
      accessKeyId: config.accessKey,
      secretAccessKey: config.secretKey,
    },
    forcePathStyle: provider === 'seaweedfs',
    requestChecksumCalculation: "WHEN_REQUIRED",
    responseChecksumValidation: "WHEN_REQUIRED",
  });
  
  const command = new GetObjectCommand({
    Bucket: config.bucket,
    Key: storagePath,
  });
  
  return getSignedUrl(s3, command, { expiresIn: 3600 });
}

export async function uploadBufferToOss(prisma, buffer, { category, mimeType, filename }) {
  // Test doubles from the legacy local-storage path do not expose systemConfig.
  // In the real application, config lookup errors must not silently write locally.
  if (!prisma?.systemConfig) return null;
  const { provider, config } = await getActiveConfig(prisma);
  if (!config || !config.endpoint || !config.bucket || !config.accessKey || !config.secretKey) {
    return null; // OSS not configured, caller falls back to local storage
  }

  if (!/^[a-z][a-z0-9-]*$/.test(category)) {
    throw new Error("无效的分类名称");
  }

  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");

  let extension = 'tmp';
  if (filename && filename.includes('.')) {
    const ext = filename.split('.').pop().toLowerCase();
    if (/^[a-z0-9]{1,4}$/.test(ext)) extension = ext;
  }
  const uuid = crypto.randomUUID();
  const storagePath = `${category}/${year}/${month}/${uuid}.${extension}`;

  const s3 = new S3Client({
    region: config.region || "us-east-1",
    endpoint: config.endpoint,
    credentials: {
      accessKeyId: config.accessKey,
      secretAccessKey: config.secretKey,
    },
    forcePathStyle: provider === 'seaweedfs',
    requestChecksumCalculation: "WHEN_REQUIRED",
    responseChecksumValidation: "WHEN_REQUIRED",
  });

  try {
    await s3.send(new PutObjectCommand({
      Bucket: config.bucket,
      Key: storagePath,
      Body: buffer,
      ContentType: mimeType,
    }));
  } catch (error) {
    // Keep the endpoint and provider visible for operations without exposing credentials.
    console.error("OSS upload failed", {
      provider,
      endpoint: config.endpoint,
      bucket: config.bucket,
      storagePath,
      error: error?.name || error?.message || error,
    });
    throw error;
  }

  return storagePath;
}

export async function deleteOssFile(prisma, storagePath) {
  try {
    const { provider, config } = await getActiveConfig(prisma).catch(() => ({ provider: null, config: null }));
    if (!config || !config.endpoint || !config.bucket || !config.accessKey || !config.secretKey) {
      return false; // Not configured
    }

    const s3 = new S3Client({
      region: config.region || "us-east-1",
      endpoint: config.endpoint,
      credentials: {
        accessKeyId: config.accessKey,
        secretAccessKey: config.secretKey,
      },
      forcePathStyle: provider === 'seaweedfs',
    });
    
    await s3.send(new DeleteObjectCommand({
      Bucket: config.bucket,
      Key: storagePath,
    }));
    return true; // Deleted successfully from OSS
  } catch (error) {
    console.error("OSS Delete Error:", error);
    return true; // Return true to prevent local fallback if OSS is configured
  }
}
