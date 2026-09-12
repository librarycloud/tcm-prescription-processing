import request from '@/api/request';
import axios from 'axios';

export async function uploadToS3(file, category, onProgress) {
  const mimeType = file.type || 'application/octet-stream';
  onProgress?.(0);
  
  const strategy = await request.get('/admin/upload/strategy', {
    params: {
      category,
      filename: file.name,
      mimeType
    }
  });

  if (!strategy.uploadUrl) {
    throw new Error('后台暂未配置有效的上传参数');
  }

  // SeaweedFS 等轻量级网关可能不支持复杂的 POST 表单上传 (405 Method Not Allowed)
  // 直接强制使用最兼容的 PUT 直传
  await axios.put(strategy.uploadUrl, file, {
    headers: {
      // 必须带上正确的 Content-Type，否则 S3/SeaweedFS 会根据签名拦截
      'Content-Type': mimeType
    },
    onUploadProgress: (progressEvent) => {
      const total = progressEvent.total || file.size;
      if (!onProgress || !total) return;
      const percent = Math.min(100, Math.round((progressEvent.loaded / total) * 100));
      onProgress(percent);
    }
  });

  onProgress?.(100);
  return strategy.storagePath;
}
