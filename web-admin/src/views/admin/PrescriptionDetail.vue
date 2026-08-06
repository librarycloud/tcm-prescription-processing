<template>
  <div :class="embedded ? 'page embedded-page' : 'page'">
    <div v-if="!embedded" class="page-header">
      <div class="title-row">
        <el-button :icon="Back" circle aria-label="返回处方列表" @click="router.back()" />
        <div>
          <h1 class="page-title">{{ prescription?.prescriptionNo || '处方详情' }}</h1>
          <p class="page-subtitle">查看处方信息、加工批次与领取记录</p>
        </div>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
    </div>

    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="card-header">
          <span>基础信息</span>
          <el-tag v-if="prescription" :type="prescriptionStatusType(prescription.status)">
            {{ prescriptionStatusText(prescription.status) }}
          </el-tag>
        </div>
      </template>
      <el-descriptions v-if="prescription" :column="3" border>
        <el-descriptions-item label="顾客姓名">{{
          prescription.customerName
        }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ prescription.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所属门店">{{
          prescription.store?.name || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="医生">{{
          prescription.doctor?.name || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="处方来源">{{
          prescription.source?.name || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="总价">{{
          prescription.totalPrice == null ? '-' : `¥${Number(prescription.totalPrice).toFixed(2)}`
        }}</el-descriptions-item>
        <el-descriptions-item label="录入时间">{{
          formatDate(prescription.createdAt)
        }}</el-descriptions-item>
        <el-descriptions-item label="剂数合计">
          {{ prescription.totalDose }} 剂
        </el-descriptions-item>
        <el-descriptions-item label="已加工 / 剩余">
          {{ prescription.takenDose }} / {{ prescription.remainingDose }} 剂
        </el-descriptions-item>
        <el-descriptions-item label="创建人">
          {{ prescription.creator?.nickname || prescription.creator?.phone || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="外方" :span="3">
          <template v-if="prescription.isExternal">
            <el-tag type="warning" effect="plain">外方</el-tag>
            <span class="external-text">
              {{ prescription.externalHospital || '未填写医院' }} ·
              {{ prescription.externalDoctor || '未填写医生' }}
              <template v-if="prescription.externalRemark">
                · {{ prescription.externalRemark }}</template
              >
            </span>
          </template>
          <span v-else>否</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{
          prescription.remark || '-'
        }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="attachment-card">
      <template #header>
        <div class="card-header">
          <div>
            <span>处方原件</span>
            <span class="header-note">支持 JPG、PNG、GIF、WEBP、BMP 或 PDF，单个文件不超过 5MB</span>
          </div>
          <el-upload
            ref="attachmentUploader"
            :auto-upload="false"
            :show-file-list="false"
            :disabled="attachmentUploading || loading"
            accept="image/jpeg,image/png,image/gif,image/webp,image/bmp,application/pdf,.jpg,.jpeg,.png,.gif,.webp,.bmp,.pdf"
            @change="handleAttachmentChange"
          >
            <el-button
              type="primary"
              :icon="Upload"
              :loading="attachmentUploading"
              :disabled="attachmentUploading || loading"
            >
              {{ prescription?.attachment ? '重新上传' : '上传处方' }}
            </el-button>
          </el-upload>
        </div>
      </template>
      <div v-if="prescription?.attachment" class="attachment-summary">
        <el-icon class="attachment-icon">
          <Picture v-if="isImageAttachment(prescription.attachment)" />
          <Document v-else />
        </el-icon>
        <div class="attachment-info">
          <div class="attachment-name" :title="prescription.attachment.originalName">
            {{ prescription.attachment.originalName }}
          </div>
          <div class="attachment-meta">
            {{ formatFileSize(prescription.attachment.fileSize) }} ·
            {{ formatDate(prescription.attachment.updatedAt || prescription.attachment.createdAt) }}
          </div>
        </div>
        <div class="attachment-actions">
          <el-button link type="primary" :icon="View" @click="openAttachmentPreview">预览</el-button>
          <el-button link :icon="Download" @click="downloadAttachment">下载</el-button>
          <el-button
            link
            type="danger"
            :icon="Delete"
            :loading="attachmentDeleting"
            @click="removeAttachment"
          >
            删除
          </el-button>
        </div>
      </div>
      <el-empty v-else description="暂无处方原件" :image-size="72" />
    </el-card>

    <el-dialog
      v-model="attachmentPreviewVisible"
      :title="attachmentPreviewName || '处方原件预览'"
      width="min(900px, calc(100vw - 32px))"
      destroy-on-close
      @closed="handlePreviewClosed"
    >
      <div v-loading="attachmentPreviewLoading" class="attachment-preview">
        <img
          v-if="attachmentPreviewUrl && isImageMime(attachmentPreviewMime)"
          :src="attachmentPreviewUrl"
          :alt="attachmentPreviewName"
        />
        <iframe
          v-else-if="attachmentPreviewUrl"
          :src="attachmentPreviewUrl"
          :title="attachmentPreviewName"
        />
        <el-empty v-else description="无法预览该文件" :image-size="72" />
      </div>
    </el-dialog>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>加工计划</span>
            <span class="header-note">一个批次对应一次领取；上下按钮可调整批次顺序</span>
          </div>
          <el-button
            type="primary"
            :icon="Plus"
            :disabled="prescription?.status === PRESCRIPTION_STATUS.CANCELLED"
            @click="openCreatePlan"
          >
            新增批次
          </el-button>
        </div>
      </template>
      <el-table :data="plans" row-key="id" border table-layout="auto">
        <template #empty><EmptyView description="暂无加工计划" /></template>
        <el-table-column label="操作" align="center">
          <template #default="{ row, $index }">
            <div class="table-actions plan-actions">
              <el-button
                link
                :icon="ArrowUp"
                :disabled="$index === 0 || reordering"
                title="上移"
                @click="movePlan($index, -1)"
              />
              <el-button
                link
                :icon="ArrowDown"
                :disabled="$index === plans.length - 1 || reordering"
                title="下移"
                @click="movePlan($index, 1)"
              />
              <el-button
                link
                type="primary"
                :disabled="!EDITABLE_PLAN_STATUSES.includes(row.status)"
                @click="openEditPlan(row)"
              >
                编辑
              </el-button>
              <el-button
                link
                type="danger"
                :disabled="row.status !== PROCESSING_STATUS.WAITING"
                @click="removePlan(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" align="center">
          <template #default="{ row }">第 {{ row.batchNo }} 批</template>
        </el-table-column>
        <el-table-column label="加工方式" align="center">
          <template #default="{ row }">{{ row.processType?.name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="totalDose" label="剂数" align="center" />
        <el-table-column prop="bagCount" label="袋数" align="center">
          <template #default="{ row }">{{ row.bagCount || '-' }}</template>
        </el-table-column>
        <el-table-column prop="volumeMl" label="毫升数" align="center">
          <template #default="{ row }">{{ row.volumeMl || '-' }}</template>
        </el-table-column>
        <el-table-column label="服用方法" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.usageMethod || '遵医嘱' }}</template>
        </el-table-column>
        <el-table-column label="取货方式" align="center">
          <template #default="{ row }">{{ pickupMethodText(row.pickupMethod) }}</template>
        </el-table-column>
        <el-table-column label="地址" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            {{ [1, 2].includes(Number(row.pickupMethod)) ? row.expressAddress || '-' : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="调度" align="center">
          <template #default="{ row }">
            <span v-if="row.scheduleType === SCHEDULE_TYPES.NOTICE">等待顾客通知</span>
            <span v-else>{{ dayText(row.processDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="优先级" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.priority === PRIORITY.URGENT" type="danger" effect="dark"
              >【加急】</el-tag
            >
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center">
          <template #default="{ row }">
            <el-tag :type="planStatusType(row.status)">{{ planStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="加工备注" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.processRemark || row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="取货码" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.package"
              link
              type="primary"
              @click="openPackageDetail(row.package)"
            >
              {{ formatPickupCode(row.pickupCode || row.package.pickupCode) }}
            </el-button>
            <span v-else>{{ formatPickupCode(row.pickupCode) || '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header><span>领取记录</span></template>
      <el-table :data="pickupRecords" border table-layout="auto">
        <template #empty><EmptyView description="暂无领取记录" /></template>
        <el-table-column label="批次" align="center">
          <template #default="{ row }">第 {{ row.batchNo }} 批</template>
        </el-table-column>
        <el-table-column label="加工方式">
          <template #default="{ row }">{{ row.processType?.name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="totalDose" label="剂数" align="center" />
        <el-table-column label="取货码" align="center">
          <template #default="{ row }">{{ formatPickupCode(row.package.pickupCode) }}</template>
        </el-table-column>
        <el-table-column label="领取状态" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === PROCESSING_STATUS.PICKED ? 'success' : 'info'">
              {{ row.status === PROCESSING_STATUS.PICKED ? '已领取' : '待领取' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="完成时间">
          <template #default="{ row }">{{ formatDate(row.finishDate) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPackageDetail(row.package)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="planVisible"
      :title="editingPlanId ? '编辑加工批次' : '新增加工批次'"
      width="min(900px, calc(100vw - 32px))"
      destroy-on-close
    >
      <el-form :model="planForm" label-width="100px">
        <div class="form-grid">
          <el-form-item label="批次号" required>
            <el-input-number
              v-model="planForm.batchNo"
              :min="1"
              :max="9999"
              :disabled="planMetadataOnlyEdit"
            />
          </el-form-item>
          <el-form-item label="加工方式" required>
            <el-select
              v-model="planForm.processTypeId"
              :disabled="planMetadataOnlyEdit"
              placeholder="请选择"
            >
              <el-option
                v-for="item in processTypes"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="取货方式" required>
            <el-select
              v-model="planForm.pickupMethod"
              :disabled="planMetadataOnlyEdit"
              placeholder="请选择取货方式"
            >
              <el-option
                v-for="item in PICKUP_METHOD_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            v-if="[1, 2].includes(Number(planForm.pickupMethod))"
            label="地址"
          >
            <el-input
              v-model.trim="planForm.expressAddress"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              placeholder="选填"
            />
          </el-form-item>
          <el-form-item label="剂数" required>
            <el-input-number
              v-model="planForm.totalDose"
              :min="1"
              :max="9999"
              :disabled="planMetadataOnlyEdit"
            />
          </el-form-item>
          <el-form-item v-if="planFormIsDecoction" label="袋数" required>
            <el-input-number
              v-model="planForm.bagCount"
              :min="1"
              :max="9999"
              :disabled="planMetadataOnlyEdit"
            />
          </el-form-item>
          <el-form-item v-if="planFormIsDecoction" label="毫升数" required>
            <el-input-number
              v-model="planForm.volumeMl"
              :min="1"
              :max="99999"
              :disabled="planMetadataOnlyEdit"
            />
          </el-form-item>
          <el-form-item label="服用方法" class="form-item-wide">
            <UsageMethodInput v-model="planForm.usageMethod" />
          </el-form-item>
          <el-form-item label="优先级">
            <el-switch
              v-model="planForm.priority"
              :active-value="PRIORITY.URGENT"
              :inactive-value="PRIORITY.NORMAL"
              :disabled="planMetadataOnlyEdit"
              active-text="加急"
            />
          </el-form-item>
          <el-form-item label="调度方式">
            <el-radio-group v-model="planForm.scheduleType" :disabled="planMetadataOnlyEdit">
              <el-radio :value="SCHEDULE_TYPES.DATE">指定日期</el-radio>
              <el-radio :value="SCHEDULE_TYPES.NOTICE">等待通知</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="planForm.scheduleType === SCHEDULE_TYPES.DATE" label="加工日期" required>
            <el-date-picker
              v-model="planForm.processDate"
              type="date"
              value-format="YYYY-MM-DD"
              :disabled="planMetadataOnlyEdit"
            />
          </el-form-item>
          <el-form-item label="提醒方式">
            <el-select v-model="planForm.notifyType">
              <el-option
                v-for="item in notifyTypes"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="通知状态">
            <el-radio-group v-model="planForm.notifyStatus">
              <el-radio :value="NOTIFY_STATUS.NOTIFIED">已通知</el-radio>
              <el-radio :value="NOTIFY_STATUS.PENDING">未通知</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="收费状态">
            <el-radio-group v-model="planForm.paymentStatus">
              <el-radio :value="PAYMENT_STATUS.PAID">已收费</el-radio>
              <el-radio :value="PAYMENT_STATUS.UNPAID">未收费</el-radio>
            </el-radio-group>
          </el-form-item>
        </div>
        <el-form-item label="加工备注">
          <el-input
            v-model="planForm.processRemark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            :disabled="planMetadataOnlyEdit"
          />
        </el-form-item>
        <el-form-item label="其它备注">
          <el-input v-model="planForm.remark" maxlength="500" :disabled="planMetadataOnlyEdit" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePlan">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs';
import {
  ArrowDown,
  ArrowUp,
  Back,
  Delete,
  Document,
  Download,
  Picture,
  Plus,
  Refresh,
  Upload,
  View
} from '@element-plus/icons-vue';
import EmptyView from '@/components/EmptyView.vue';
import UsageMethodInput from '@/components/UsageMethodInput.vue';
import {
  deletePrescriptionAttachment,
  getPrescription,
  getPrescriptionAttachment,
  uploadPrescriptionAttachment
} from '@/api/prescription';
import {
  createProcessingPlan,
  deleteProcessingPlan,
  getDictionaries,
  reorderPrescriptionPlans,
  updateProcessingPlan
} from '@/api/processing';
import {
  NOTIFY_STATUS,
  PAYMENT_STATUS,
  PRIORITY,
  PROCESS_TYPE_CODES,
  PROCESSING_STATUS,
  PROCESSING_STATUS_OPTIONS,
  PROCESSING_STATUS_TAG,
  SCHEDULE_TYPES
} from '@/constants/processing';
import { formatDate } from '@/utils/date';
import { compressImageForUpload } from '@/utils/imageUpload';
import { formatPickupCode, PICKUP_METHOD_OPTIONS, pickupMethodText } from '@/utils/status';

const props = defineProps({
  id: { type: [String, Number], default: null },
  embedded: { type: Boolean, default: false }
});
const emit = defineEmits(['package-detail']);

const PRESCRIPTION_STATUS = Object.freeze({ ACTIVE: 0, COMPLETED: 1, CANCELLED: 2 });
const EDITABLE_PLAN_STATUSES = Object.freeze([
  PROCESSING_STATUS.WAITING,
  PROCESSING_STATUS.PROCESSING,
  PROCESSING_STATUS.FINISHED,
  PROCESSING_STATUS.READY_PICKUP
]);
const route = useRoute();
const router = useRouter();
const loading = ref(false);
const saving = ref(false);
const reordering = ref(false);
const attachmentUploading = ref(false);
const attachmentDeleting = ref(false);
const attachmentUploader = ref(null);
const attachmentPreviewVisible = ref(false);
const attachmentPreviewLoading = ref(false);
const attachmentPreviewUrl = ref('');
const attachmentPreviewMime = ref('');
const attachmentPreviewName = ref('');
const attachmentPreviewRequestId = ref(0);
const planVisible = ref(false);
const editingPlanId = ref(null);
const prescription = ref(null);
const processTypes = ref([]);
const notifyTypes = ref([]);
const planForm = reactive({});
const plans = computed(() => prescription.value?.plans || []);
const pickupRecords = computed(() => plans.value.filter((plan) => plan.package));
const planFormIsDecoction = computed(() => isDecoctionProcessType(planForm.processTypeId));
const planMetadataOnlyEdit = computed(() =>
  [PROCESSING_STATUS.FINISHED, PROCESSING_STATUS.READY_PICKUP].includes(planForm.status)
);
const MAX_ATTACHMENT_SIZE = 5 * 1024 * 1024;
const MAX_ATTACHMENT_SOURCE_IMAGE_SIZE = 30 * 1024 * 1024;
const ATTACHMENT_COMPRESSION_THRESHOLD = 1024 * 1024;
const ALLOWED_ATTACHMENT_MIME_TYPES = new Set([
  'image/jpeg',
  'image/png',
  'image/gif',
  'image/webp',
  'image/bmp',
  'application/pdf'
]);
const ALLOWED_ATTACHMENT_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'pdf']);
const COMPRESSIBLE_ATTACHMENT_MIME_TYPES = new Set([
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/bmp'
]);
const COMPRESSIBLE_ATTACHMENT_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'webp', 'bmp']);

function todayText() {
  const date = new Date();
  const pad = (value) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function dayText(value) {
  return value ? String(value).slice(0, 10) : '-';
}

function prescriptionStatusText(status) {
  return ['进行中', '已完成', '已取消'][Number(status)] || '未知';
}

function prescriptionStatusType(status) {
  return ['info', 'success', 'danger'][Number(status)] || 'info';
}

function planStatusText(status) {
  return PROCESSING_STATUS_OPTIONS.find((item) => item.value === status)?.label || status;
}

function planStatusType(status) {
  return PROCESSING_STATUS_TAG[status] || 'info';
}

function isImageMime(mimeType) {
  return String(mimeType || '').startsWith('image/');
}

function isImageAttachment(attachment) {
  return isImageMime(attachment?.mimeType);
}

function formatFileSize(value) {
  const size = Number(value);
  if (!Number.isFinite(size) || size <= 0) return '0 B';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

function hasAllowedAttachmentType(file) {
  const mimeType = String(file?.type || '').toLowerCase();
  const extension = attachmentExtension(file);
  return ALLOWED_ATTACHMENT_MIME_TYPES.has(mimeType) || ALLOWED_ATTACHMENT_EXTENSIONS.has(extension);
}

function attachmentExtension(file) {
  return String(file?.name || '')
    .split('.')
    .pop()
    .toLowerCase();
}

function isCompressibleAttachment(file) {
  const mimeType = String(file?.type || '').toLowerCase();
  return (
    COMPRESSIBLE_ATTACHMENT_MIME_TYPES.has(mimeType) ||
    COMPRESSIBLE_ATTACHMENT_EXTENSIONS.has(attachmentExtension(file))
  );
}

async function handleAttachmentChange(uploadFile) {
  const file = uploadFile?.raw;
  if (!file) return;
  if (!hasAllowedAttachmentType(file)) {
    ElMessage.warning('仅支持 JPG、PNG、GIF、WEBP、BMP 图片或 PDF 文件');
    attachmentUploader.value?.clearFiles();
    return;
  }
  const compressible = isCompressibleAttachment(file);
  if (compressible && Number(file.size) > MAX_ATTACHMENT_SOURCE_IMAGE_SIZE) {
    ElMessage.warning('待压缩图片不能超过 30MB');
    attachmentUploader.value?.clearFiles();
    return;
  }
  if (!compressible && Number(file.size) > MAX_ATTACHMENT_SIZE) {
    ElMessage.warning('处方文件不能超过 5MB');
    attachmentUploader.value?.clearFiles();
    return;
  }

  attachmentUploading.value = true;
  try {
    const preparedFile = compressible
      ? await compressImageForUpload(file, {
          maxBytes: MAX_ATTACHMENT_SIZE,
          compressionThresholdBytes: ATTACHMENT_COMPRESSION_THRESHOLD,
          fallbackBaseName: '处方原件'
        })
      : file;
    await uploadPrescriptionAttachment(props.id ?? route.params.id, preparedFile);
    await loadData();
    ElMessage.success(
      preparedFile === file
        ? '处方原件已上传'
        : `处方原件已压缩并上传（${formatFileSize(file.size)} → ${formatFileSize(preparedFile.size)}）`
    );
  } catch (error) {
    if (error.imageCompressionFailed) ElMessage.error(error.message || '图片压缩失败');
  } finally {
    attachmentUploading.value = false;
    attachmentUploader.value?.clearFiles();
  }
}

function releaseAttachmentPreviewUrl() {
  if (attachmentPreviewUrl.value) URL.revokeObjectURL(attachmentPreviewUrl.value);
  attachmentPreviewUrl.value = '';
}

async function openAttachmentPreview() {
  const attachment = prescription.value?.attachment;
  if (!attachment) return;
  const requestId = attachmentPreviewRequestId.value + 1;
  attachmentPreviewRequestId.value = requestId;
  releaseAttachmentPreviewUrl();
  attachmentPreviewName.value = attachment.originalName || '处方原件';
  attachmentPreviewMime.value = attachment.mimeType || '';
  attachmentPreviewVisible.value = true;
  attachmentPreviewLoading.value = true;
  try {
    const blob = await getPrescriptionAttachment(props.id ?? route.params.id);
    if (requestId === attachmentPreviewRequestId.value && attachmentPreviewVisible.value) {
      attachmentPreviewUrl.value = URL.createObjectURL(blob);
    }
  } finally {
    if (requestId === attachmentPreviewRequestId.value) attachmentPreviewLoading.value = false;
  }
}

async function downloadAttachment() {
  const attachment = prescription.value?.attachment;
  if (!attachment) return;
  const blob = await getPrescriptionAttachment(props.id ?? route.params.id);
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = attachment.originalName || '处方原件';
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

async function removeAttachment() {
  await ElMessageBox.confirm('确认删除该处方原件？删除后无法恢复。', '删除处方原件', {
    type: 'warning',
    confirmButtonText: '删除',
    confirmButtonClass: 'el-button--danger'
  });
  attachmentDeleting.value = true;
  try {
    await deletePrescriptionAttachment(props.id ?? route.params.id);
    attachmentPreviewVisible.value = false;
    handlePreviewClosed();
    await loadData();
    ElMessage.success('处方原件已删除');
  } finally {
    attachmentDeleting.value = false;
  }
}

function handlePreviewClosed() {
  attachmentPreviewRequestId.value += 1;
  attachmentPreviewLoading.value = false;
  releaseAttachmentPreviewUrl();
  attachmentPreviewMime.value = '';
  attachmentPreviewName.value = '';
}

function isDecoctionProcessType(processTypeId) {
  return (
    processTypes.value.find((item) => Number(item.id) === Number(processTypeId))?.code ===
    PROCESS_TYPE_CODES.DECOCTION
  );
}

async function loadData() {
  loading.value = true;
  try {
    prescription.value = await getPrescription(props.id ?? route.params.id);
  } finally {
    loading.value = false;
  }
}

function resetPlanForm() {
  const nextBatchNo = plans.value.reduce((max, plan) => Math.max(max, plan.batchNo), 0) + 1;
  Object.assign(planForm, {
    status: null,
    prescriptionId: Number(props.id ?? route.params.id),
    batchNo: nextBatchNo,
    processTypeId: null,
    totalDose: 1,
    bagCount: null,
    volumeMl: null,
    usageMethod: '',
    scheduleType: SCHEDULE_TYPES.DATE,
    processDate: todayText(),
    priority: PRIORITY.NORMAL,
    notifyType: null,
    notifyStatus: NOTIFY_STATUS.PENDING,
    paymentStatus: PAYMENT_STATUS.PAID,
    pickupMethod: PICKUP_METHOD_OPTIONS[0].value,
    expressAddress: '',
    processRemark: '',
    remark: ''
  });
}

function openCreatePlan() {
  editingPlanId.value = null;
  resetPlanForm();
  planVisible.value = true;
}

function openEditPlan(row) {
  editingPlanId.value = row.id;
  Object.assign(planForm, {
    status: row.status,
    prescriptionId: row.prescriptionId,
    batchNo: row.batchNo,
    processTypeId: row.processTypeId,
    totalDose: row.totalDose,
    bagCount: row.bagCount ?? null,
    volumeMl: row.volumeMl ?? null,
    usageMethod: row.usageMethod || '',
    scheduleType: row.scheduleType,
    processDate: dayText(row.processDate) === '-' ? '' : dayText(row.processDate),
    priority: row.priority ?? PRIORITY.NORMAL,
    notifyType: row.notifyType ?? null,
    notifyStatus: Number(row.notifyStatus ?? NOTIFY_STATUS.PENDING),
    paymentStatus: row.paymentStatus,
    pickupMethod: row.pickupMethod ?? PICKUP_METHOD_OPTIONS[0].value,
    expressAddress: row.expressAddress || '',
    processRemark: row.processRemark || '',
    remark: row.remark || ''
  });
  planVisible.value = true;
}

async function savePlan() {
  if (!planMetadataOnlyEdit.value) {
    if (!planForm.processTypeId) return ElMessage.warning('请选择加工方式');
    if (planFormIsDecoction.value) {
      if (!Number.isInteger(Number(planForm.bagCount)) || Number(planForm.bagCount) <= 0) {
        return ElMessage.warning('袋数必须为正整数');
      }
      if (!Number.isInteger(Number(planForm.volumeMl)) || Number(planForm.volumeMl) <= 0) {
        return ElMessage.warning('毫升数必须为正整数');
      }
    }
    if (planForm.scheduleType === SCHEDULE_TYPES.DATE && !planForm.processDate) {
      return ElMessage.warning('请选择加工日期');
    }
  }
  saving.value = true;
  try {
    const payload = planMetadataOnlyEdit.value
      ? {
          notifyType: planForm.notifyType,
          notifyStatus: planForm.notifyStatus,
          paymentStatus: planForm.paymentStatus,
          usageMethod: planForm.usageMethod,
          pickupMethod: planForm.pickupMethod,
          expressAddress: planForm.expressAddress
        }
      : {
          ...planForm,
          status: undefined,
          bagCount: planFormIsDecoction.value ? Number(planForm.bagCount) : null,
          volumeMl: planFormIsDecoction.value ? Number(planForm.volumeMl) : null,
          processDate: planForm.scheduleType === SCHEDULE_TYPES.DATE ? planForm.processDate : null
        };
    if (editingPlanId.value) await updateProcessingPlan(editingPlanId.value, payload);
    else await createProcessingPlan(payload);
    planVisible.value = false;
    ElMessage.success(editingPlanId.value ? '加工批次已更新' : '加工批次已创建');
    await loadData();
  } finally {
    saving.value = false;
  }
}

async function removePlan(row) {
  await ElMessageBox.confirm(`确认删除第 ${row.batchNo} 批加工计划？`, '删除加工批次', {
    type: 'warning'
  });
  await deleteProcessingPlan(row.id);
  ElMessage.success('加工批次已删除');
  await loadData();
}

async function movePlan(index, offset) {
  const target = index + offset;
  if (target < 0 || target >= plans.value.length) return;
  const ordered = [...plans.value];
  const [moved] = ordered.splice(index, 1);
  ordered.splice(target, 0, moved);
  reordering.value = true;
  try {
    await reorderPrescriptionPlans(
      props.id ?? route.params.id,
      ordered.map((item) => item.id)
    );
    ElMessage.success('批次顺序已更新');
    await loadData();
  } finally {
    reordering.value = false;
  }
}

function openPackageDetail(pkg) {
  if (props.embedded) emit('package-detail', pkg);
  else router.push(`/admin/packages/${pkg.id}`);
}

onMounted(async () => {
  const [processData, notifyData] = await Promise.all([
    getDictionaries('ProcessType'),
    getDictionaries('NotifyType')
  ]);
  processTypes.value = processData || [];
  const mappedNotifyTypes = (notifyData || []).map((item) => ({
    label: item.name,
    value: item.id,
    code: item.code
  }));
  if (mappedNotifyTypes.length) {
    notifyTypes.value = mappedNotifyTypes;
    if (planForm.notifyType === null)
      planForm.notifyType = mappedNotifyTypes.find((item) => item.code === 'NONE')?.value ?? null;
  }
  await loadData();
});

onBeforeUnmount(() => {
  releaseAttachmentPreviewUrl();
});
</script>

<style scoped>
.embedded-page {
  padding: 0;
}

.title-row,
.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-header {
  justify-content: space-between;
}

.header-note,
.external-text {
  margin-left: 12px;
  color: var(--app-muted);
  font-size: 13px;
}

.attachment-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.attachment-icon {
  flex: none;
  width: 42px;
  height: 42px;
  border-radius: 8px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  font-size: 24px;
}

.attachment-info {
  min-width: 0;
  flex: 1;
}

.attachment-name {
  overflow: hidden;
  color: var(--app-text);
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-meta {
  margin-top: 4px;
  color: var(--app-muted);
  font-size: 13px;
}

.attachment-actions {
  display: flex;
  flex: none;
  gap: 8px;
}

.attachment-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: min(70vh, 720px);
  overflow: hidden;
  background: var(--el-fill-color-light);
}

.attachment-preview img {
  display: block;
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
}

.attachment-preview iframe {
  width: 100%;
  height: 70vh;
  border: 0;
  background: #fff;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 12px;
}

.form-item-wide {
  grid-column: 1 / -1;
}

.form-grid :deep(.el-select),
.form-grid :deep(.el-date-editor) {
  width: 100%;
}

.plan-actions {
  gap: 2px;
  white-space: nowrap;
}

.plan-actions :deep(.el-button) {
  margin-left: 0;
  padding-right: 2px;
  padding-left: 2px;
}

@media (max-width: 768px) {
  :deep(.el-descriptions__body .el-descriptions__table) {
    display: block;
    overflow-x: auto;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .card-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .attachment-summary {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .attachment-info {
    min-width: calc(100% - 58px);
  }

  .attachment-actions {
    width: 100%;
    margin-left: 54px;
  }
}
</style>
