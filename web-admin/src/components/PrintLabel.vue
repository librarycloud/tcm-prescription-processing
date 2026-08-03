<template>
  <section class="print-label" :style="labelStyle" aria-label="打印标签">
    <div
      v-for="field in visibleFields"
      :key="field.id"
      class="print-field"
      :class="{ 'print-field--qrcode': field.id === 'qrcode' }"
      :style="fieldStyle(field)"
    >
      <img v-if="field.id === 'qrcode' && qrDataUrl" :src="qrDataUrl" alt="取货码二维码" />
      <span v-else>{{ fieldValue(field) }}</span>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import { formatDate } from '@/utils/date';
import { maskPhone } from '@/utils/phone';
import { formatPickupCode, pickupMethodText } from '@/utils/status';
import { printFontFamily } from '@/utils/printTemplate';
import { usageMethodForPrint } from '@/utils/usageMethod';

const props = defineProps({
  packageInfo: { type: Object, default: null },
  planInfo: { type: Object, default: null },
  qrDataUrl: { type: String, default: '' },
  template: { type: Object, required: true }
});

const visibleFields = computed(() =>
  (props.template.fields || []).filter((field) => field.visible !== false)
);
const labelStyle = computed(() => ({
  width: `${Number(props.template.widthMm)}mm`,
  height: `${Number(props.template.heightMm)}mm`
}));

function fieldStyle(field) {
  return {
    left: `${Number(field.x)}mm`,
    top: `${Number(field.y)}mm`,
    width: `${Number(field.width)}mm`,
    height: `${Number(field.height)}mm`,
    fontSize: `${Number(field.fontSize || 3)}mm`,
    fontFamily: printFontFamily(field.fontFamily),
    fontWeight: field.bold ? 800 : 400,
    textAlign: field.align || 'left',
    whiteSpace: fieldWhiteSpace(field)
  };
}

function fieldWhiteSpace(field) {
  if (field.id === 'usageMethod') return 'pre-wrap';
  if (String(field.id).startsWith('custom_')) return field.wrap ? 'pre-wrap' : 'pre';
  return field.wrap ? 'pre-wrap' : 'nowrap';
}

function fieldValue(field) {
  if (String(field.id).startsWith('custom_')) return field.text || '';
  const item = props.planInfo || props.packageInfo || {};
  const prescription = item.prescription || {};
  const values = {
    pickupCode: formatPickupCode(item.pickupCode) || '-',
    itemName: item.itemName || '-',
    itemInfo: item.itemInfo ? `备注：${item.itemInfo}` : '-',
    receiverName: item.receiverName || '-',
    receiverPhone: maskPhone(item.receiverPhone),
    pickupMethod: pickupMethodText(item.pickupMethod),
    createdAt: `录入：${formatDate(item.createdAt).split(' ')[0]}`,
    pickedAt: item.pickedAt ? `取货：${formatDate(item.pickedAt).split(' ')[0]}` : '-',
    storeName: item.store?.name || '-',
    prescriptionNo: prescription.prescriptionNo || '-',
    customerName: `姓名：${prescription.customerName || item.customerName || '-'}`,
    batchNo: item.batchNo ? `第${item.batchNo}批` : '-',
    processType: item.processType?.name || '-',
    totalDose: item.totalDose ? `${item.totalDose}剂` : '-',
    bagCount: item.bagCount ? `${item.bagCount}袋` : '-',
    volumeMl: item.volumeMl ? `${item.volumeMl}ml` : '-',
    processDate: item.processDate ? `加工：${String(item.processDate).slice(0, 10)}` : '-',
    usageMethod: `服用方法：\n${usageMethodForPrint(item.usageMethod)}`
  };
  return values[field.id] || '-';
}
</script>

<style src="../styles/print-label.css"></style>
