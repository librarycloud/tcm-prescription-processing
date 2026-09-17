function canvasToBlob(canvas, mimeType, quality) {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('图片压缩失败'))),
      mimeType,
      quality
    );
  });
}

function compressionError(message) {
  const error = new Error(message);
  error.imageCompressionFailed = true;
  return error;
}

export async function compressImageForUpload(file, options = {}) {
  const maxBytes = Number(options.maxBytes) || 5 * 1024 * 1024;
  const compressionThresholdBytes = Number(options.compressionThresholdBytes) || maxBytes;
  if (!file || Number(file.size) <= compressionThresholdBytes) return file;

  const outputType = options.outputType || 'image/jpeg';
  const strategies = options.strategies || [
    { quality: 0.88, maxEdge: 3000 },
    { quality: 0.82, maxEdge: 2400 },
    { quality: 0.76, maxEdge: 1920 }
  ];
  let bitmap;
  try {
    bitmap = await createImageBitmap(file);
    for (const strategy of strategies) {
      const maxEdge = Math.max(bitmap.width, bitmap.height);
      const scale = Math.min(1, strategy.maxEdge / maxEdge);
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round(bitmap.width * scale));
      canvas.height = Math.max(1, Math.round(bitmap.height * scale));
      const context = canvas.getContext('2d');
      if (!context) throw new Error('浏览器无法处理该图片');
      context.fillStyle = '#ffffff';
      context.fillRect(0, 0, canvas.width, canvas.height);
      context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
      const blob = await canvasToBlob(canvas, outputType, strategy.quality);
      if (blob.size <= maxBytes && (file.size > maxBytes || blob.size < file.size)) {
        const baseName = file.name.replace(/\.[^.]+$/, '') || options.fallbackBaseName || '图片';
        return new File([blob], `${baseName}.jpg`, {
          type: outputType,
          lastModified: Date.now()
        });
      }
    }
  } catch (error) {
    if (file.size <= maxBytes) return file;
    throw compressionError(error?.message || '图片压缩失败');
  } finally {
    bitmap?.close();
  }

  if (file.size <= maxBytes) return file;
  throw compressionError('图片压缩后仍超过 5MB');
}
