<template>
  <el-dialog
    :model-value="modelValue"
    title="设备标签打印"
    width="min(720px, calc(100vw - 32px))"
    align-center
    destroy-on-close
    @close="emit('update:modelValue', false)"
  >
    <div class="print-options">
      <el-select v-model="selectedTemplateId" aria-label="设备标签模板">
        <el-option
          v-for="item in templates"
          :key="item.id"
          :label="`${item.name}（${item.widthMm} × ${item.heightMm} mm）`"
          :value="item.id"
        />
      </el-select>
      <el-input-number v-model="copies" :min="1" :max="20" aria-label="打印份数" />
    </div>

    <div ref="printAreaRef" class="print-area equipment-print-area">
      <div v-for="copy in copies" :key="copy" class="label-copy">
        <PrintLabel
          v-if="equipment && activeTemplate"
          :equipment-info="equipment"
          :template="activeTemplate"
          :qr-data-url="qrDataUrl"
        />
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :icon="Printer" :disabled="!equipment" @click="printLabels">
        打印
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Printer } from '@element-plus/icons-vue';
import PrintLabel from '@/components/PrintLabel.vue';
import printLabelCss from '@/styles/print-label.css?raw';
import { getPrintTemplates } from '@/api/printTemplate';
import { createQRCodeDataUrl } from '@/utils/qrcode';
import { DEFAULT_EQUIPMENT_TEMPLATE, EQUIPMENT_TEMPLATE_TYPE } from '@/utils/printTemplate';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  equipment: { type: Object, default: null }
});
const emit = defineEmits(['update:modelValue']);
const templates = ref([DEFAULT_EQUIPMENT_TEMPLATE]);
const selectedTemplateId = ref(DEFAULT_EQUIPMENT_TEMPLATE.id);
const copies = ref(1);
const qrDataUrl = ref('');
const printAreaRef = ref(null);
const activeTemplate = computed(
  () =>
    templates.value.find((item) => item.id === selectedTemplateId.value) || templates.value[0]
);

async function prepare() {
  if (!props.equipment) return;
  try {
    const data = await getPrintTemplates({
      type: EQUIPMENT_TEMPLATE_TYPE,
      storeId: props.equipment.storeId
    });
    const available = data?.templates?.filter((item) => item.enabled) || [];
    templates.value = available.length ? available : [DEFAULT_EQUIPMENT_TEMPLATE];
    selectedTemplateId.value = (
      available.find((item) => item.isDefault) || templates.value[0]
    ).id;
  } catch {
    templates.value = [DEFAULT_EQUIPMENT_TEMPLATE];
    selectedTemplateId.value = DEFAULT_EQUIPMENT_TEMPLATE.id;
    ElMessage.warning('设备标签模板加载失败，已使用默认模板');
  }
  qrDataUrl.value = await createQRCodeDataUrl(props.equipment.qrContent, { width: 320 });
}

watch(
  () => [props.modelValue, props.equipment?.id],
  ([visible]) => {
    if (visible) prepare();
  },
  { immediate: true }
);

function appendPrintStyles(targetDocument) {
  const template = activeTemplate.value;
  const style = targetDocument.createElement('style');
  style.textContent = `${printLabelCss}\n@page { size: ${template.widthMm}mm ${template.heightMm}mm; margin: 0; }\nhtml, body { margin: 0; padding: 0; background: var(--el-bg-color); }\n.label-copy { break-after: page; page-break-after: always; }\n.label-copy:last-child { break-after: auto; page-break-after: auto; }\n.print-label { border: 0 !important; }`;
  targetDocument.head.appendChild(style);
}

async function printLabels() {
  if (!printAreaRef.value || !activeTemplate.value) return;
  const iframe = document.createElement('iframe');
  iframe.className = 'print-frame';
  iframe.setAttribute('aria-hidden', 'true');
  document.body.appendChild(iframe);
  const printDocument = iframe.contentDocument;
  printDocument.open();
  printDocument.write('<!doctype html><html><head><meta charset="UTF-8"></head><body></body></html>');
  printDocument.close();
  appendPrintStyles(printDocument);
  printDocument.body.appendChild(printAreaRef.value.cloneNode(true));
  await new Promise((resolve) => window.requestAnimationFrame(resolve));
  iframe.contentWindow.focus();
  iframe.contentWindow.print();
  setTimeout(() => iframe.remove(), 1000);
}
</script>

<style scoped>
.print-options {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  margin-bottom: 18px;
}

.equipment-print-area {
  display: grid;
  place-items: center;
  min-height: 260px;
  overflow: auto;
  background: var(--el-bg-color-page);
  border: 1px solid var(--app-border);
}

.label-copy {
  background: var(--el-bg-color);
}
</style>
