import request from '@/api/request';
import axios from 'axios';

/**
 * 统一的大文件/小文件分片直传 OSS 函数
 * @param {File} file - 要上传的文件对象
 * @param {String} category - 业务分类（如 prescriptions, processing）
 * @param {Function} onProgress - 进度回调 (progress) => {}
 * @returns {Promise<String>} - 返回最终的 storagePath
 */
export async function uploadToS3(file, category, onProgress) {
  // 1. 向后端获取直传策略和凭证
  const strategy = await request.get('/admin/upload/strategy', {
    params: {
      category,
      filename: file.name
    }
  });

  if (!strategy.uploadUrl && !strategy.presignedPost) {
    throw new Error('后台暂未配置有效的上传参数');
  }

  // 2. 使用 Presigned POST 表单上传（兼容性最好）
  if (strategy.presignedPost) {
    const formData = new FormData();
    for (const [key, value] of Object.entries(strategy.presignedPost.fields)) {
      formData.append(key, value);
    }
    formData.append('file', file);
    
    await axios.post(strategy.presignedPost.url, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      onUploadProgress: (progressEvent) => {
        if (onProgress && progressEvent.total) {
          const percent = Math.round((progressEvent.loaded / progressEvent.total) * 100);
          onProgress(percent);
        }
      }
    });
  } else {
    // 回退到 PUT 上传
    await axios.put(strategy.uploadUrl, file, {
      headers: {
        'Content-Type': file.type || 'application/octet-stream'
      },
      onUploadProgress: (progressEvent) => {
        if (onProgress && progressEvent.total) {
          const percent = Math.round((progressEvent.loaded / progressEvent.total) * 100);
          onProgress(percent);
        }
      }
    });
  }

  // 3. 返回云端路径给调用方
  return strategy.storagePath;
}
