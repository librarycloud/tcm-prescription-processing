<template>
  <el-dialog
    :model-value="modelValue"
    title="打印取货标签"
    width="min(900px, calc(100vw - 32px))"
    align-center
    destroy-on-close
    @close="emit('update:modelValue', false)"
  >
    <div class="print-options">
      <el-select v-model="selectedTemplateId" class="template-select" aria-label="打印模板">
        <el-option
          v-for="template in templates"
          :key="template.id"
          :label="`${template.name}（${template.widthMm} × ${template.heightMm} mm）`"
          :value="template.id"
        />
      </el-select>
      <el-input-number v-model="copies" :min="1" :max="20" aria-label="打印份数" />
      <span class="template-hint">可在系统管理 / 打印设置中维护模板</span>
    </div>

    <div ref="printAreaRef" class="print-area">
      <div v-for="copy in copies" :key="copy" class="label-copy">
        <PrintLabel
          v-if="packageInfo && activeTemplate"
          :key="`${activeTemplate.id}-${copy}`"
          :package-info="packageInfo"
          :qr-data-url="qrDataUrl"
          :template="activeTemplate"
        />
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :icon="Printer"
        :disabled="!packageInfo || !qrDataUrl || !activeTemplate"
        @click="printLabels"
      >
        打印
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { Printer } from '@element-plus/icons-vue';
import PrintLabel from '@/components/PrintLabel.vue';
import printLabelCss from '@/styles/print-label.css?raw';
import { getPrintTemplates } from '@/api/printTemplate';
import { createQRCodeDataUrl } from '@/utils/qrcode';
import { DEFAULT_PICKUP_TEMPLATE, PACKAGE_PICKUP_TEMPLATE_TYPE } from '@/utils/printTemplate';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  packageInfo: { type: Object, default: null }
});

const emit = defineEmits(['update:modelValue']);
const printAreaRef = ref(null);
const qrDataUrl = ref('');
const templates = ref([DEFAULT_PICKUP_TEMPLATE]);
const selectedTemplateId = ref(DEFAULT_PICKUP_TEMPLATE.id);
const copies = ref(1);

const activeTemplate = computed(
  () => templates.value.find((template) => template.id === selectedTemplateId.value) || templates.value[0]
);

async function loadTemplates() {
  try {
    const data = await getPrintTemplates({
      type: PACKAGE_PICKUP_TEMPLATE_TYPE,
      storeId: props.packageInfo?.storeId || props.packageInfo?.store?.id
    });
    const available = data?.templates?.filter((template) => template.enabled) || [];
    templates.value = available.length ? available : [DEFAULT_PICKUP_TEMPLATE];
    selectedTemplateId.value = (available.find((template) => template.isDefault) || templates.value[0]).id;
  } catch {
    templates.value = [DEFAULT_PICKUP_TEMPLATE];
    selectedTemplateId.value = DEFAULT_PICKUP_TEMPLATE.id;
    ElMessage.warning('打印模板加载失败，已使用本地默认模板');
  }
}

async function loadQrCode() {
  if (!props.packageInfo?.pickupCode) return;
  qrDataUrl.value = await createQRCodeDataUrl(
    props.packageInfo.pickupQrContent || props.packageInfo.pickupCode,
    { width: 360 }
  );
}

watch(
  () => [props.modelValue, props.packageInfo?.pickupCode, props.packageInfo?.pickupQrContent],
  async ([visible]) => {
    if (!visible) return;
    await Promise.all([loadTemplates(), loadQrCode()]);
  },
  { immediate: true }
);

function appendPrintStyles(targetDocument) {
  const template = activeTemplate.value;
  const style = targetDocument.createElement('style');
  style.textContent = `
    ${printLabelCss}
    @page { size: ${template.widthMm}mm ${template.heightMm}mm; margin: 0; }
    html, body { margin: 0; padding: 0; background: #fff; }
    .print-area { display: block; margin: 0; padding: 0; }
    .label-copy { break-after: page; page-break-after: always; }
    .label-copy:last-child { break-after: auto; page-break-after: auto; }
    .print-label { border: 0 !important; }
  `;
  targetDocument.head.appendChild(style);
}

function waitForImages(targetDocument) {
  return Promise.all(Array.from(targetDocument.images).map((image) => {
    if (image.complete && image.naturalWidth > 0) return Promise.resolve();
    return new Promise((resolve) => {
      image.addEventListener('load', resolve, { once: true });
      image.addEventListener('error', resolve, { once: true });
    });
  }));
}

async function printLabels() {
  if (!printAreaRef.value || !activeTemplate.value) return;
  const iframe = document.createElement('iframe');
  iframe.setAttribute('aria-hidden', 'true');
  iframe.className = 'print-frame';
  document.body.appendChild(iframe);
  const printDocument = iframe.contentDocument;
  printDocument.open();
  printDocument.write('<!doctype html><html><head><meta charset="UTF-8"></head><body></body></html>');
  printDocument.close();
  appendPrintStyles(printDocument);
  printDocument.body.appendChild(printAreaRef.value.cloneNode(true));
  await waitForImages(printDocument);
  await new Promise((resolve) => window.requestAnimationFrame(resolve));
  const cleanup = () => iframe.remove();
  iframe.contentWindow.addEventListener('afterprint', cleanup, { once: true });
  iframe.contentWindow.focus();
  iframe.contentWindow.print();
  window.setTimeout(cleanup, 60000);
}
</script>

<style scoped>
.print-options {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.template-select {
  width: min(360px, 100%);
}

.template-hint {
  color: var(--app-muted);
  font-size: 12px;
}

.print-area {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  min-height: 300px;
  padding: 28px;
  overflow: auto;
  background: #f3f4f6;
  border: 1px solid var(--app-border);
  border-radius: 8px;
}

.label-copy {
  flex: 0 0 auto;
}

:global(.print-frame) {
  position: fixed;
  left: -10000px;
  width: 1px;
  height: 1px;
  border: 0;
}

@media (max-width: 640px) {
  .print-options {
    align-items: stretch;
    flex-direction: column;
  }

  .template-select {
    width: 100%;
  }

  .print-area {
    justify-content: flex-start;
    padding: 18px;
  }
}
</style>
