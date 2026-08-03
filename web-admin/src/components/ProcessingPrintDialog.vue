<template>
  <el-dialog
    :model-value="modelValue"
    :title="`${labelTitle}打印`"
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
      <span class="template-hint">可在打印设置中维护本门店模板</span>
    </div>

    <section v-if="canSetUsageMethod" class="usage-setting">
      <div class="usage-setting-header">
        <div>
          <strong>本次打印服用方法</strong>
          <span>默认按标准服用说明生成，可在打印前直接修改。</span>
        </div>
        <div class="usage-setting-actions">
          <el-button text @click="resetUsageMethod">恢复初始内容</el-button>
          <el-button
            type="primary"
            plain
            :loading="savingUsageMethod"
            :disabled="!usageMethodChanged"
            @click="saveUsageMethod"
          >
            保存到加工计划
          </el-button>
        </div>
      </div>
      <UsageMethodInput v-model="printUsageMethod" />
    </section>

    <div ref="printAreaRef" class="print-area">
      <div v-for="copy in copies" :key="copy" class="label-copy">
        <PrintLabel
          v-if="planInfo && activeTemplate"
          :key="`${activeTemplate.id}-${copy}`"
          :plan-info="printPlanInfo"
          :template="activeTemplate"
          :qr-data-url="qrDataUrl"
        />
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :icon="Printer"
        :disabled="!planInfo || !activeTemplate"
        @click="printLabels"
        >打印</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { Printer } from '@element-plus/icons-vue';
import PrintLabel from '@/components/PrintLabel.vue';
import UsageMethodInput from '@/components/UsageMethodInput.vue';
import printLabelCss from '@/styles/print-label.css?raw';
import { getPrintTemplates } from '@/api/printTemplate';
import { updateProcessingPlan } from '@/api/processing';
import { usageMethodForPrint } from '@/utils/usageMethod';
import { createQRCodeDataUrl } from '@/utils/qrcode';
import {
  DEFAULT_PICKUP_TEMPLATE,
  DEFAULT_PACKAGING_TEMPLATE,
  DEFAULT_PROCESSING_TEMPLATE,
  PACKAGE_PICKUP_TEMPLATE_TYPE,
  PACKAGING_TEMPLATE_TYPE,
  PROCESSING_TEMPLATE_TYPE
} from '@/utils/printTemplate';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  planInfo: { type: Object, default: null },
  templateType: { type: String, default: PROCESSING_TEMPLATE_TYPE }
});
const emit = defineEmits(['update:modelValue', 'usage-method-saved']);
const printAreaRef = ref(null);
const copies = ref(1);
const printUsageMethod = ref('');
const savingUsageMethod = ref(false);
const qrDataUrl = ref('');
const fallbackTemplate = computed(() =>
  props.templateType === PACKAGE_PICKUP_TEMPLATE_TYPE
    ? DEFAULT_PICKUP_TEMPLATE
    : props.templateType === PACKAGING_TEMPLATE_TYPE
      ? DEFAULT_PACKAGING_TEMPLATE
      : DEFAULT_PROCESSING_TEMPLATE
);
const labelTitle = computed(() => {
  if (props.templateType === PACKAGE_PICKUP_TEMPLATE_TYPE) return '取货标签';
  return props.templateType === PACKAGING_TEMPLATE_TYPE ? '包装标签' : '加工标签';
});
const templates = ref([DEFAULT_PROCESSING_TEMPLATE]);
const selectedTemplateId = ref(DEFAULT_PROCESSING_TEMPLATE.id);
const activeTemplate = computed(
  () =>
    templates.value.find((template) => template.id === selectedTemplateId.value) ||
    templates.value[0]
);
const canSetUsageMethod = computed(() => props.templateType === PACKAGING_TEMPLATE_TYPE);
const printPlanInfo = computed(() => ({
  ...(props.planInfo || {}),
  pickupCode: props.planInfo?.pickupCode || props.planInfo?.package?.pickupCode || '',
  itemName: [
    props.planInfo?.processType?.name,
    props.planInfo?.totalDose ? `${props.planInfo.totalDose}剂` : '',
    props.planInfo?.bagCount ? `${props.planInfo.bagCount}袋` : ''
  ]
    .filter(Boolean)
    .join(' '),
  itemInfo: props.planInfo?.processRemark || props.planInfo?.remark || '',
  receiverName: props.planInfo?.prescription?.customerName || '',
  receiverPhone: props.planInfo?.prescription?.phone || '',
  usageMethod: canSetUsageMethod.value ? printUsageMethod.value.trim() : props.planInfo?.usageMethod
}));
const usageMethodChanged = computed(
  () => printUsageMethod.value.trim() !== String(props.planInfo?.usageMethod || '').trim()
);

async function loadTemplates() {
  try {
    const data = await getPrintTemplates({
      type: props.templateType,
      storeId: props.planInfo?.storeId || props.planInfo?.store?.id
    });
    const available = data?.templates?.filter((template) => template.enabled) || [];
    templates.value = available.length ? available : [fallbackTemplate.value];
    selectedTemplateId.value = (
      available.find((template) => template.isDefault) || templates.value[0]
    ).id;
  } catch {
    templates.value = [fallbackTemplate.value];
    selectedTemplateId.value = fallbackTemplate.value.id;
    ElMessage.warning('打印模板加载失败，已使用本地默认模板');
  }
}

watch(
  () => [props.modelValue, props.templateType, props.planInfo?.id],
  ([visible]) => {
    if (!visible) return;
    resetUsageMethod();
    loadTemplates();
    createQRCodeDataUrl(props.planInfo?.pickupCode || props.planInfo?.package?.pickupCode || '', {
      width: 360
    }).then((value) => {
      qrDataUrl.value = value;
    });
  },
  { immediate: true }
);

function resetUsageMethod() {
  printUsageMethod.value = usageMethodForPrint(props.planInfo?.usageMethod);
}

async function saveUsageMethod() {
  if (!props.planInfo?.id || !usageMethodChanged.value) return;
  savingUsageMethod.value = true;
  try {
    const usageMethod = printUsageMethod.value.trim();
    await updateProcessingPlan(props.planInfo.id, { usageMethod });
    emit('usage-method-saved', usageMethod);
    ElMessage.success('服用方法已保存到加工计划');
  } finally {
    savingUsageMethod.value = false;
  }
}

function appendPrintStyles(targetDocument) {
  const template = activeTemplate.value;
  const style = targetDocument.createElement('style');
  style.textContent = `${printLabelCss}\n@page { size: ${template.widthMm}mm ${template.heightMm}mm; margin: 0; }\nhtml, body { margin: 0; padding: 0; background: #fff; }\n.print-area { display: block; margin: 0; padding: 0; }\n.label-copy { break-after: page; page-break-after: always; }\n.label-copy:last-child { break-after: auto; page-break-after: auto; }\n.print-label { border: 0 !important; }`;
  targetDocument.head.appendChild(style);
}

async function printLabels() {
  if (!printAreaRef.value || !activeTemplate.value) return;
  const iframe = document.createElement('iframe');
  iframe.setAttribute('aria-hidden', 'true');
  iframe.className = 'print-frame';
  document.body.appendChild(iframe);
  const printDocument = iframe.contentDocument;
  printDocument.open();
  printDocument.write(
    '<!doctype html><html><head><meta charset="UTF-8"></head><body></body></html>'
  );
  printDocument.close();
  appendPrintStyles(printDocument);
  printDocument.body.appendChild(printAreaRef.value.cloneNode(true));
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
.usage-setting {
  padding: 16px 0 18px;
  border-top: 1px solid var(--app-border);
}
.usage-setting-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}
.usage-setting-header > div:first-child {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.usage-setting-header span {
  color: var(--app-muted);
  font-size: 12px;
}
.usage-setting-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
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
  .print-options,
  .usage-setting-header {
    align-items: stretch;
    flex-direction: column;
  }
  .template-select {
    width: 100%;
  }
  .usage-setting-actions {
    justify-content: flex-end;
  }
  .print-area {
    justify-content: flex-start;
    padding: 18px;
  }
}
</style>
