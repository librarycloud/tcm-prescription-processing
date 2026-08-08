const MAX_FILE_SIZE = 5 * 1024 * 1024;
const MAX_SOURCE_IMAGE_SIZE = 30 * 1024 * 1024;
const COMPRESSION_THRESHOLD = 1024 * 1024;

function getFileSize(filePath) {
  return new Promise((resolve, reject) => {
    wx.getFileInfo({
      filePath,
      success: (res) => resolve(Number(res.size) || 0),
      fail: reject
    });
  });
}

function compressImage(filePath, options) {
  return new Promise((resolve, reject) => {
    wx.compressImage({
      src: filePath,
      quality: options.quality,
      compressedWidth: options.width,
      success: (res) => resolve(res.tempFilePath),
      fail: reject
    });
  });
}

function chooseImage() {
  return new Promise((resolve) => {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera', 'album'],
      sizeType: ['original'],
      camera: 'back',
      success: (res) => {
        const file = res.tempFiles?.[0];
        resolve(file?.tempFilePath ? file : null);
      },
      fail: () => resolve(null)
    });
  });
}

function choosePdf() {
  return new Promise((resolve) => {
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: ['pdf'],
      success: (res) => resolve(res.tempFiles?.[0] || null),
      fail: () => resolve(null)
    });
  });
}

function chooseType() {
  return new Promise((resolve) => {
    wx.showActionSheet({
      itemList: ['拍照或从相册选择', '选择 PDF 文件'],
      success: (res) => resolve(res.tapIndex),
      fail: () => resolve(null)
    });
  });
}

async function prepareImage(file) {
  const sourcePath = file.tempFilePath;
  const sourceSize = Number(file.size) || (await getFileSize(sourcePath));
  if (sourceSize > MAX_SOURCE_IMAGE_SIZE) {
    throw new Error('待压缩图片不能超过 30MB');
  }
  if (sourceSize <= COMPRESSION_THRESHOLD) {
    return { filePath: sourcePath, size: sourceSize, compressed: false };
  }

  const strategies = [
    { quality: 88, width: 2400 },
    { quality: 80, width: 1920 },
    { quality: 72, width: 1600 }
  ];
  let best = null;
  for (const strategy of strategies) {
    try {
      const filePath = await compressImage(sourcePath, strategy);
      const size = await getFileSize(filePath);
      if (!best || size < best.size) best = { filePath, size };
      if (size <= MAX_FILE_SIZE && size < sourceSize) {
        return { filePath, size, compressed: true };
      }
    } catch (error) {
      // Try the next compression level before falling back to the source image.
    }
  }

  if (sourceSize <= MAX_FILE_SIZE) {
    return { filePath: sourcePath, size: sourceSize, compressed: false };
  }
  if (best && best.size <= MAX_FILE_SIZE) {
    return { ...best, compressed: true };
  }
  throw new Error('图片压缩后仍超过 5MB');
}

function imageExtension(filePath) {
  const matched = String(filePath || '').match(/\.(jpe?g|png|gif|webp|bmp)(?:$|\?)/i);
  return matched ? matched[1].toLowerCase().replace('jpeg', 'jpg') : 'jpg';
}

export function formatAttachmentSize(value) {
  const size = Number(value);
  if (!Number.isFinite(size) || size <= 0) return '0 B';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

export async function choosePrescriptionAttachment() {
  const type = await chooseType();
  if (type === null) return null;

  if (type === 0) {
    const file = await chooseImage();
    if (!file) return null;
    const prepared = await prepareImage(file);
    const originalSize = Number(file.size) || prepared.size;
    return {
      ...prepared,
      originalSize,
      originalName: `处方照片-${Date.now()}.${prepared.compressed ? 'jpg' : imageExtension(file.tempFilePath)}`,
      mimeType: 'image/jpeg',
      isImage: true,
      sizeText: formatAttachmentSize(prepared.size)
    };
  }

  const file = await choosePdf();
  if (!file?.path) return null;
  const size = Number(file.size) || (await getFileSize(file.path));
  if (!/\.pdf$/i.test(String(file.name || '')) || size > MAX_FILE_SIZE) {
    throw new Error(size > MAX_FILE_SIZE ? 'PDF 文件不能超过 5MB' : '请选择 PDF 文件');
  }
  return {
    filePath: file.path,
    originalName: file.name || `处方原件-${Date.now()}.pdf`,
    mimeType: 'application/pdf',
    isImage: false,
    size,
    originalSize: size,
    compressed: false,
    sizeText: formatAttachmentSize(size)
  };
}
