import request from './request';
import { uploadToS3 } from '@/utils/s3Upload';

export const getPrescriptions = (params) => request.get('/admin/prescriptions', { params });
export const getPrescription = (id) => request.get(`/admin/prescriptions/${id}`);
export const createPrescription = (data) => request.post('/admin/prescriptions', data);
export const updatePrescription = (id, data) => request.put(`/admin/prescriptions/${id}`, data);
export const deletePrescription = (id) => request.delete(`/admin/prescriptions/${id}`);

export const uploadPrescriptionAttachment = async (id, file) => {
  // 1. 直传文件到云端 (或本地 MinIO)，自带断点续传
  // file.size 如果太大，S3 分片上传会显示威力
  const storagePath = await uploadToS3(file, 'prescriptions');
  
  // 2. 将结果上报给业务接口
  return request.post(`/admin/prescriptions/${id}/attachment`, {
    storagePath,
    filename: file.name,
    mimetype: file.type,
    size: file.size
  });
};

export const getPrescriptionAttachment = (id) =>
  request.get(`/admin/prescriptions/${id}/attachment`, { responseType: 'blob' });

export const deletePrescriptionAttachment = (id) =>
  request.delete(`/admin/prescriptions/${id}/attachment`);
