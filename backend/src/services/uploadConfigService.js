import { S3Client, PutObjectCommand } from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";
import { createPresignedPost } from "@aws-sdk/s3-presigned-post";
import crypto from "node:crypto";

const CONFIG_ITEM_KEY = "oss_upload";

export async function getUploadConfig(prisma) {
  let record = await prisma.systemConfig.findUnique({
    where: { item: CONFIG_ITEM_KEY },
  });
  
  if (!record) {
    record = await prisma.systemConfig.create({
      data: {
        item: CONFIG_ITEM_KEY,
        value: JSON.stringify({
          endpoint: "",
          bucket: "",
          accessKey: "",
          secretKey: "",
          region: "us-east-1",
          cdnDomain: "",
        }),
        class: "upload",
        isPublic: true,
        type: "json",
        mark: "对象存储配置",
      },
    });
  }
  
  try {
    return JSON.parse(record.value);
  } catch (e) {
    return {};
  }
}

export async function updateUploadConfig(prisma, data) {
  return prisma.systemConfig.upsert({
    where: { item: CONFIG_ITEM_KEY },
    update: {
      value: JSON.stringify({
        endpoint: data.endpoint || "",
        bucket: data.bucket || "",
        accessKey: data.accessKey || "",
        secretKey: data.secretKey || "",
        region: data.region || "us-east-1",
        cdnDomain: data.cdnDomain || "",
      }),
    },
    create: {
      item: CONFIG_ITEM_KEY,
      value: JSON.stringify({
        endpoint: data.endpoint || "",
        bucket: data.bucket || "",
        accessKey: data.accessKey || "",
        secretKey: data.secretKey || "",
        region: data.region || "us-east-1",
        cdnDomain: data.cdnDomain || "",
      }),
      class: "upload",
      isPublic: true,
      type: "json",
      mark: "对象存储配置",
    }
  });
}

// 统一的直传策略：直接向前端下发凭证信息，让前端自己决定是普通 PUT 还是 Multipart 续传
export async function generateUploadStrategy(prisma, category, filename) {
  const config = await getUploadConfig(prisma);
  
  if (!config.endpoint || !config.bucket || !config.accessKey || !config.secretKey) {
    throw new Error("请先在系统设置中配置 S3/OSS 上传参数");
  }

  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const extension = filename && filename.includes('.') ? filename.split('.').pop() : 'tmp';
  const uuid = crypto.randomUUID();
  const storagePath = `${category}/${year}/${month}/${uuid}.${extension}`;

  const s3 = new S3Client({
    region: config.region || "us-east-1",
    endpoint: config.endpoint,
    credentials: {
      accessKeyId: config.accessKey,
      secretAccessKey: config.secretKey,
    },
    forcePathStyle: true,
  });
  
  const command = new PutObjectCommand({
    Bucket: config.bucket,
    Key: storagePath,
  });
  
  const uploadUrl = await getSignedUrl(s3, command, { expiresIn: 3600 });
  
  const presignedPost = await createPresignedPost(s3, {
    Bucket: config.bucket,
    Key: storagePath,
    Expires: 3600,
  });

  return {
    endpoint: config.endpoint,
    region: config.region || "us-east-1",
    bucket: config.bucket,
    accessKey: config.accessKey,
    secretKey: config.secretKey,
    storagePath,
    uploadUrl,
    presignedPost,
    cdnDomain: config.cdnDomain,
  };
}
