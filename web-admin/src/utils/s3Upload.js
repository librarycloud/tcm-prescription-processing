import request from '@/api/request';
import { S3Client } from '@aws-sdk/client-s3';
import { Upload } from '@aws-sdk/lib-storage';

/**
 * 统一的大文件/小文件分片直传 OSS 函数
 * @param {File} file - 要上传的文件对象
 * @param {String} category - 业务分类（如 prescriptions, processing）
 * @param {Function} onProgress - 进度回调 (progress) => {}
 * @returns {Promise<String>} - 返回最终的 storagePath
 */
export async function uploadToS3(file, category, onProgress) {
  // 1. 向后端获取直传策略和凭证
  const strategy = await request.get('/upload/strategy', {
    params: {
      category,
      filename: file.name
    }
  });

  if (!strategy.endpoint || !strategy.bucket || !strategy.accessKey) {
    throw new Error('后台暂未配置有效的上传参数');
  }

  // 2. 初始化 S3 客户端
  const s3Client = new S3Client({
    endpoint: strategy.endpoint.startsWith('http') ? strategy.endpoint : `https://${strategy.endpoint}`,
    region: strategy.region || 'us-east-1',
    credentials: {
      accessKeyId: strategy.accessKey,
      secretAccessKey: strategy.secretKey
    },
    forcePathStyle: true // 强制 PathStyle 以兼容 MinIO 等私有化存储
  });

  // 3. 构造智能上传任务 (支持超大文件自动分片和断点续传机制)
  const parallelUploads3 = new Upload({
    client: s3Client,
    params: {
      Bucket: strategy.bucket,
      Key: strategy.storagePath,
      Body: file,
      ContentType: file.type || 'application/octet-stream'
    },
    // 分片大小 5MB
    partSize: 5 * 1024 * 1024,
    queueSize: 4 // 并发度
  });

  parallelUploads3.on('httpUploadProgress', (progress) => {
    if (onProgress && progress.total) {
      const percent = Math.round((progress.loaded / progress.total) * 100);
      onProgress(percent);
    }
  });

  // 4. 执行上传
  await parallelUploads3.done();

  // 5. 返回云端路径给调用方
  return strategy.storagePath;
}
