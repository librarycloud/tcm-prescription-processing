<template>
  <el-input
    :model-value="modelValue"
    maxlength="100"
    clearable
    placeholder="输入或扫描快递单号"
    :disabled="disabled"
    @update:model-value="updateValue"
  >
    <template #append>
      <el-button
        :icon="Camera"
        :disabled="disabled"
        title="扫描快递单号"
        aria-label="扫描快递单号"
        @click="openScanner"
      />
    </template>
  </el-input>

  <el-dialog
    v-model="scannerVisible"
    title="扫描快递单号"
    width="min(520px, calc(100vw - 32px))"
    @closed="stopScanner"
  >
    <video ref="videoRef" class="scanner-video" autoplay muted playsinline />
    <p class="scanner-hint">将顺丰运单条码或二维码置于取景框内</p>
  </el-dialog>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref } from 'vue';
import { Camera } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { normalizeExpressTrackingNo } from '@/utils/expressTracking';

defineProps({
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false }
});
const emit = defineEmits(['update:modelValue']);
const scannerVisible = ref(false);
const videoRef = ref(null);
let stream = null;
let scanFrame = 0;

function updateValue(value) {
  emit('update:modelValue', String(value || '').slice(0, 100));
}

async function openScanner() {
  if (!('BarcodeDetector' in window) || !navigator.mediaDevices?.getUserMedia) {
    ElMessage.warning('当前浏览器不支持摄像头扫码，可使用扫码枪或手动输入');
    return;
  }
  scannerVisible.value = true;
  await nextTick();
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
    videoRef.value.srcObject = stream;
    const detector = new window.BarcodeDetector({ formats: ['code_128', 'qr_code'] });
    const detect = async () => {
      if (!scannerVisible.value || !videoRef.value) return;
      try {
        const [result] = await detector.detect(videoRef.value);
        if (result?.rawValue) {
          emit('update:modelValue', normalizeExpressTrackingNo(result.rawValue));
          scannerVisible.value = false;
          ElMessage.success('已识别快递单号');
          return;
        }
      } catch {
        // The video may not have produced a decodable frame yet.
      }
      scanFrame = window.requestAnimationFrame(detect);
    };
    scanFrame = window.requestAnimationFrame(detect);
  } catch {
    scannerVisible.value = false;
    ElMessage.warning('无法打开摄像头，请检查浏览器权限');
  }
}

function stopScanner() {
  window.cancelAnimationFrame(scanFrame);
  stream?.getTracks().forEach((track) => track.stop());
  stream = null;
  if (videoRef.value) videoRef.value.srcObject = null;
}

onBeforeUnmount(stopScanner);
</script>

<style scoped>
.scanner-video {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  background: #111827;
}
.scanner-hint {
  margin: 12px 0 0;
  color: var(--app-muted);
  text-align: center;
}
</style>
