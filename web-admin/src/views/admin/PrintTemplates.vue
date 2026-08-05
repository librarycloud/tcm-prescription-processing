<template>
  <div class="page print-templates-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">打印设置</h1>
        <p class="page-subtitle">管理不同业务的打印模板、字段和纸张尺寸</p>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadTemplates">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增模板</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-select
          v-model="typeFilter"
          clearable
          placeholder="全部模板类型"
          @change="loadTemplates"
        >
          <el-option
            v-for="type in templateTypes"
            :key="type.value"
            :label="type.label"
            :value="type.value"
          />
        </el-select>
        <el-select
          v-if="userStore.isSuperAdmin"
          v-model="storeId"
          clearable
          placeholder="全部门店"
          @change="loadTemplates"
        >
          <el-option
            v-for="store in stores"
            :key="store.id"
            :label="store.name"
            :value="store.id"
          />
        </el-select>
        <span class="toolbar-tip"
          >默认模板会在打印窗口中自动选中，停用模板不会出现在打印选择框中。</span
        >
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="templates" border row-key="id" table-layout="auto">
        <template #empty><EmptyView description="暂无打印模板" /></template>
        <el-table-column prop="name" label="模板名称" />
        <el-table-column v-if="userStore.isSuperAdmin" label="所属门店">
          <template #default="{ row }">{{ row.store?.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="模板类型">
          <template #default="{ row }">{{ typeLabel(row.templateType) }}</template>
        </el-table-column>
        <el-table-column label="纸张尺寸" align="center">
          <template #default="{ row }">{{ row.widthMm }} × {{ row.heightMm }} mm</template>
        </el-table-column>
        <el-table-column label="字段" align="center">
          <template #default="{ row }"
            >{{ visibleFieldCount(row) }}/{{ row.fields?.length || 0 }}</template
          >
        </el-table-column>
        <el-table-column label="状态" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{
              row.enabled ? '已启用' : '已停用'
            }}</el-tag>
            <el-tag v-if="row.isDefault" type="primary" effect="plain">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" align="center">
          <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="!row.isDefault" link type="success" @click="setDefault(row)"
                >设为默认</el-button
              >
              <el-button
                link
                :type="row.enabled ? 'warning' : 'success'"
                @click="toggleEnabled(row)"
                >{{ row.enabled ? '停用' : '启用' }}</el-button
              >
              <el-button v-if="!row.isDefault" link type="danger" @click="removeTemplate(row)"
                >删除</el-button
              >
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer
      v-model="editorVisible"
      :title="editingId ? '编辑打印模板' : '新增打印模板'"
      direction="rtl"
      size="100%"
      destroy-on-close
    >
      <div v-if="form" class="editor-layout">
        <div class="editor-top">
          <section class="editor-form">
            <div class="section-title editor-form-title">
              <div><strong>模板信息</strong></div>
            </div>
            <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
              <div class="basic-grid">
                <el-form-item label="模板名称" prop="name"
                  ><el-input v-model.trim="form.name" maxlength="100" show-word-limit
                /></el-form-item>
                <el-form-item label="模板类型" prop="templateType">
                  <el-select
                    v-model="form.templateType"
                    :disabled="Boolean(editingId)"
                    @change="changeTemplateType"
                  >
                    <el-option
                      v-for="type in templateTypes"
                      :key="type.value"
                      :label="type.label"
                      :value="type.value"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item v-if="userStore.isSuperAdmin" label="所属门店" prop="storeId">
                  <el-select
                    v-model="form.storeId"
                    :disabled="Boolean(editingId)"
                    placeholder="请选择门店"
                  >
                    <el-option
                      v-for="store in stores"
                      :key="store.id"
                      :label="store.name"
                      :value="store.id"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="纸张宽度（mm）" prop="widthMm"
                  ><el-input-number
                    v-model="form.widthMm"
                    :min="1"
                    :max="300"
                    :precision="2"
                    controls-position="right"
                /></el-form-item>
                <el-form-item label="纸张高度（mm）" prop="heightMm"
                  ><el-input-number
                    v-model="form.heightMm"
                    :min="1"
                    :max="300"
                    :precision="2"
                    controls-position="right"
                /></el-form-item>
                <!--
                  <el-form-item label="模板状态">
                  <div class="switch-row">
                    <el-switch v-model="form.enabled" active-text="启用模板" />
                    <el-switch v-model="form.isDefault" active-text="设为默认模板" />
                  </div>
                </el-form-item>
              --></div>
            </el-form>
          </section>
          <section class="preview-panel">
            <div class="section-title preview-title">
              <div>
                <strong>实时预览</strong><span>{{ form.widthMm }} × {{ form.heightMm }} mm</span>
              </div>
            </div>
            <div class="preview-stage" @pointerdown.self="selectedFieldId = ''">
              <div ref="previewCanvasRef" class="layout-preview-canvas" :style="previewCanvasStyle">
                <PrintLabel
                  :package-info="isPackageTemplate ? previewPackage : null"
                  :plan-info="!isPackageTemplate && !isEquipmentTemplate ? previewPlan : null"
                  :equipment-info="isEquipmentTemplate ? previewEquipment : null"
                  :qr-data-url="qrDataUrl"
                  :template="form"
                />
                <div
                  v-for="field in editableFields"
                  :key="field.id"
                  class="layout-field-overlay"
                  :class="{ selected: selectedFieldId === field.id }"
                  :style="fieldOverlayStyle(field)"
                  role="button"
                  tabindex="0"
                  :aria-label="`调整${fieldLabel(field.id)}的位置和尺寸`"
                  @pointerdown="startLayoutInteraction($event, field, 'drag')"
                  @keydown="nudgeField($event, field)"
                >
                  <span v-if="selectedFieldId === field.id" class="layout-field-name">
                    {{ fieldLabel(field.id) }}
                  </span>
                  <span
                    v-if="selectedFieldId === field.id"
                    class="layout-resize-handle"
                    title="拖动调整尺寸"
                    @pointerdown.stop="startLayoutInteraction($event, field, 'resize')"
                  />
                </div>
              </div>
            </div>
            <el-alert
              :title="
                isPackageTemplate
                  ? '预览使用示例包裹数据，实际打印内容以包裹详情为准。'
                  : isEquipmentTemplate
                    ? '预览使用示例设备数据，实际打印内容以设备档案为准。'
                    : '预览使用示例加工计划数据，实际打印内容以加工计划详情为准。'
              "
              type="info"
              :closable="false"
            />
          </section>
        </div>

        <section class="fields-panel">
          <div class="section-title">
            <div><strong>字段布局</strong><span>单位为 mm，字段位置以纸张左上角为原点。</span></div>
            <div class="field-actions">
              <el-button :icon="Plus" :disabled="form.fields.length >= 30" @click="addCustomText"
                >新增文本</el-button
              >
              <el-button text type="primary" @click="resetFields">恢复默认布局</el-button>
            </div>
          </div>
          <div class="field-editor-wrap">
            <el-table
              class="field-layout-table"
              :data="form.fields"
              border
              size="small"
              table-layout="auto"
            >
              <el-table-column label="字段 / 文本" align="center" header-align="center">
                <template #default="{ row }">
                  <div v-if="isCustomField(row)" class="custom-field-editor">
                    <el-input
                      v-model="row.text"
                      type="textarea"
                      :rows="2"
                      resize="none"
                      maxlength="200"
                      placeholder="请输入打印文本"
                    />
                    <el-tooltip content="删除自定义文本" placement="top"
                      ><el-button
                        text
                        circle
                        type="danger"
                        :icon="Delete"
                        aria-label="删除自定义文本"
                        @click="removeCustomText(row)"
                    /></el-tooltip>
                  </div>
                  <div v-else class="field-name">
                    <span>{{ fieldLabel(row.id) }}</span
                    ><el-tag v-if="row.id === 'qrcode'" size="small" effect="plain">二维码</el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="显示" align="center" header-align="center"
                ><template #default="{ row }"><el-switch v-model="row.visible" /></template
              ></el-table-column>
              <el-table-column label="X" align="center" header-align="center"
                ><template #default="{ row }"
                  ><el-input-number
                    v-model="row.x"
                    class="layout-number-input"
                    :min="0"
                    :max="300"
                    :precision="2"
                    controls-position="right" /></template
              ></el-table-column>
              <el-table-column label="Y" align="center" header-align="center"
                ><template #default="{ row }"
                  ><el-input-number
                    v-model="row.y"
                    class="layout-number-input"
                    :min="0"
                    :max="300"
                    :precision="2"
                    controls-position="right" /></template
              ></el-table-column>
              <el-table-column label="宽度" align="center" header-align="center"
                ><template #default="{ row }"
                  ><el-input-number
                    v-model="row.width"
                    class="layout-number-input"
                    :min="0.1"
                    :max="300"
                    :precision="2"
                    controls-position="right" /></template
              ></el-table-column>
              <el-table-column label="高度" align="center" header-align="center"
                ><template #default="{ row }"
                  ><el-input-number
                    v-model="row.height"
                    class="layout-number-input"
                    :min="0.1"
                    :max="300"
                    :precision="2"
                    controls-position="right" /></template
              ></el-table-column>
              <el-table-column label="字号" align="center" header-align="center"
                ><template #default="{ row }"
                  ><el-input-number
                    v-model="row.fontSize"
                    class="layout-number-input"
                    :min="0.1"
                    :max="50"
                    :precision="2"
                    controls-position="right" /></template
              ></el-table-column>
              <el-table-column label="字体" align="center" header-align="center"
                ><template #default="{ row }"
                  ><el-select v-model="row.fontFamily" class="layout-font-select"
                    ><el-option
                      v-for="font in fontOptions"
                      :key="font.value"
                      :label="font.label"
                      :value="font.value" /></el-select></template
              ></el-table-column>
              <el-table-column label="对齐" align="center" header-align="center"
                ><template #default="{ row }"
                  ><el-select v-model="row.align" class="layout-align-select"
                    ><el-option label="左对齐" value="left" /><el-option
                      label="居中"
                      value="center" /><el-option
                      label="右对齐"
                      value="right" /></el-select></template
              ></el-table-column>
              <el-table-column label="粗体" align="center" header-align="center"
                ><template #default="{ row }"><el-switch v-model="row.bold" /></template
              ></el-table-column>
              <el-table-column label="换行" align="center" header-align="center"
                ><template #default="{ row }"><el-switch v-model="row.wrap" /></template
              ></el-table-column>
            </el-table>
          </div>
        </section>
      </div>
      <template #footer
        ><div class="drawer-footer">
          <el-button @click="editorVisible = false">取消</el-button
          ><el-button type="primary" :loading="saving" @click="saveForm">保存模板</el-button>
        </div></template
      >
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Delete, Plus, Refresh } from '@element-plus/icons-vue';
import EmptyView from '@/components/EmptyView.vue';
import PrintLabel from '@/components/PrintLabel.vue';
import {
  createPrintTemplate,
  deletePrintTemplate,
  getPrintTemplates,
  updatePrintTemplate
} from '@/api/printTemplate';
import { createQRCodeDataUrl } from '@/utils/qrcode';
import {
  cloneTemplate,
  DEFAULT_PACKAGING_TEMPLATE,
  DEFAULT_EQUIPMENT_TEMPLATE,
  DEFAULT_PICKUP_TEMPLATE,
  DEFAULT_PROCESSING_TEMPLATE,
  PORTRAIT_PACKAGING_TEMPLATE,
  PORTRAIT_PROCESSING_TEMPLATE,
  PRINT_FONT_OPTIONS,
  THERMAL_PROCESSING_TEMPLATE
} from '@/utils/printTemplate';
import { formatDate } from '@/utils/date';
import { getStores } from '@/api/store';
import { useUserStore } from '@/stores/user';
import { DEFAULT_USAGE_METHOD } from '@/utils/usageMethod';

const loading = ref(false);
const saving = ref(false);
const editorVisible = ref(false);
const editingId = ref(null);
const formRef = ref(null);
const typeFilter = ref('');
const storeId = ref('');
const stores = ref([]);
const templates = ref([]);
const templateTypes = ref([]);
const fieldDefinitions = ref({});
const fontOptions = ref([...PRINT_FONT_OPTIONS]);
const form = ref(null);
const qrDataUrl = ref('');
const previewCanvasRef = ref(null);
const selectedFieldId = ref('');
const previewPackage = reactive({
  pickupCode: '457123',
  itemName: '示例包裹',
  itemInfo: '请核对包裹信息',
  receiverName: '张三',
  receiverPhone: '13800138000',
  pickupMethod: 0,
  createdAt: new Date().toISOString(),
  pickedAt: null,
  store: { name: '总部' }
});
const previewPlan = reactive({
  planCode: 'JG260805-0123',
  prescription: {
    prescriptionNo: 'CF202608050123',
    customerName: '张三',
    doctor: { name: '王医生' }
  },
  batchNo: 1,
  processType: { name: '代煎' },
  totalDose: 7,
  bagCount: 14,
  volumeMl: 200,
  processDate: new Date().toISOString(),
  usageMethod: DEFAULT_USAGE_METHOD,
  processRemark: '附子先煎30分钟，其余药味后下同煎',
  store: { name: '总部' }
});
const previewEquipment = reactive({
  equipmentNo: 'P01',
  name: '1号煎药锅',
  typeName: '煎药锅',
  store: { name: '总部' }
});
const userStore = useUserStore();
const isPackageTemplate = computed(() => form.value?.templateType === 'PACKAGE_PICKUP');
const isEquipmentTemplate = computed(() => form.value?.templateType === 'EQUIPMENT');
const editableFields = computed(() =>
  (form.value?.fields || []).filter((field) => field.visible !== false)
);
const previewCanvasStyle = computed(() => ({
  width: `${Number(form.value?.widthMm || 0)}mm`,
  height: `${Number(form.value?.heightMm || 0)}mm`
}));
let customFieldSequence = 0;
let layoutInteraction = null;
const rules = {
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  templateType: [{ required: true, message: '请选择模板类型', trigger: 'change' }],
  storeId: [{ required: true, message: '请选择所属门店', trigger: 'change' }],
  widthMm: [{ required: true, message: '请输入纸张宽度', trigger: 'change' }],
  heightMm: [{ required: true, message: '请输入纸张高度', trigger: 'change' }]
};

function typeLabel(type) {
  return templateTypes.value.find((item) => item.value === type)?.label || type;
}
function fieldLabel(id) {
  const customField = form.value?.fields?.find((item) => item.id === id && isCustomField(item));
  if (customField) return customField.text?.trim() || '自定义文本';
  for (const definitions of Object.values(fieldDefinitions.value)) {
    const field = definitions.find((item) => item.id === id);
    if (field) return field.label;
  }
  return id;
}
function isCustomField(field) {
  return String(field?.id || '').startsWith('custom_');
}
function visibleFieldCount(template) {
  return (template.fields || []).filter((field) => field.visible !== false).length;
}

function rounded(value) {
  return Math.round(value * 10) / 10;
}

function bounded(value, min, max) {
  return Math.min(Math.max(value, min), Math.max(max, min));
}

function fieldOverlayStyle(field) {
  return {
    left: `${Number(field.x)}mm`,
    top: `${Number(field.y)}mm`,
    width: `${Number(field.width)}mm`,
    height: `${Number(field.height)}mm`
  };
}

function startLayoutInteraction(event, field, mode) {
  if (!previewCanvasRef.value || !form.value) return;
  event.preventDefault();
  selectedFieldId.value = field.id;
  const bounds = previewCanvasRef.value.getBoundingClientRect();
  layoutInteraction = {
    mode,
    field,
    startClientX: event.clientX,
    startClientY: event.clientY,
    startX: Number(field.x),
    startY: Number(field.y),
    startWidth: Number(field.width),
    startHeight: Number(field.height),
    mmPerPixelX: Number(form.value.widthMm) / bounds.width,
    mmPerPixelY: Number(form.value.heightMm) / bounds.height
  };
  document.body.classList.add('print-layout-interacting');
  window.addEventListener('pointermove', moveLayoutField);
  window.addEventListener('pointerup', stopLayoutInteraction, { once: true });
}

function moveLayoutField(event) {
  if (!layoutInteraction || !form.value) return;
  event.preventDefault();
  const interaction = layoutInteraction;
  const deltaX = (event.clientX - interaction.startClientX) * interaction.mmPerPixelX;
  const deltaY = (event.clientY - interaction.startClientY) * interaction.mmPerPixelY;
  if (interaction.mode === 'drag') {
    interaction.field.x = rounded(
      bounded(
        interaction.startX + deltaX,
        0,
        Number(form.value.widthMm) - Number(interaction.field.width)
      )
    );
    interaction.field.y = rounded(
      bounded(
        interaction.startY + deltaY,
        0,
        Number(form.value.heightMm) - Number(interaction.field.height)
      )
    );
    return;
  }
  interaction.field.width = rounded(
    bounded(
      interaction.startWidth + deltaX,
      1,
      Number(form.value.widthMm) - Number(interaction.field.x)
    )
  );
  interaction.field.height = rounded(
    bounded(
      interaction.startHeight + deltaY,
      1,
      Number(form.value.heightMm) - Number(interaction.field.y)
    )
  );
}

function stopLayoutInteraction() {
  layoutInteraction = null;
  document.body.classList.remove('print-layout-interacting');
  window.removeEventListener('pointermove', moveLayoutField);
}

function nudgeField(event, field) {
  const directions = {
    ArrowLeft: [-1, 0],
    ArrowRight: [1, 0],
    ArrowUp: [0, -1],
    ArrowDown: [0, 1]
  };
  const direction = directions[event.key];
  if (!direction || !form.value) return;
  event.preventDefault();
  selectedFieldId.value = field.id;
  const step = event.shiftKey ? 1 : 0.5;
  field.x = rounded(
    bounded(
      Number(field.x) + direction[0] * step,
      0,
      Number(form.value.widthMm) - Number(field.width)
    )
  );
  field.y = rounded(
    bounded(
      Number(field.y) + direction[1] * step,
      0,
      Number(form.value.heightMm) - Number(field.height)
    )
  );
}

function addCustomText() {
  if (!form.value || form.value.fields.length >= 30) {
    ElMessage.warning('每个模板最多支持30个字段');
    return;
  }
  customFieldSequence += 1;
  const width = Math.max(Math.min(Number(form.value.widthMm) - 8, 40), 1);
  const height = Math.max(Math.min(6, Number(form.value.heightMm)), 1);
  const field = {
    id: `custom_${Date.now().toString(36)}_${customFieldSequence}`,
    text: '自定义文本',
    x: Math.max((Number(form.value.widthMm) - width) / 2, 0),
    y: Math.max(Math.min(4 + customFieldSequence * 2, Number(form.value.heightMm) - height), 0),
    width,
    height,
    fontSize: 3.2,
    fontFamily: 'system',
    align: 'center',
    bold: false,
    wrap: false,
    visible: true
  };
  form.value.fields.push(field);
  selectedFieldId.value = field.id;
}

function removeCustomText(field) {
  if (!form.value || !isCustomField(field)) return;
  form.value.fields = form.value.fields.filter((item) => item.id !== field.id);
  if (selectedFieldId.value === field.id) selectedFieldId.value = '';
}

function defaultFields(type, widthMm, heightMm) {
  const localDefaults = [
    DEFAULT_PICKUP_TEMPLATE,
    DEFAULT_PROCESSING_TEMPLATE,
    THERMAL_PROCESSING_TEMPLATE,
    DEFAULT_EQUIPMENT_TEMPLATE,
    PORTRAIT_PROCESSING_TEMPLATE,
    DEFAULT_PACKAGING_TEMPLATE,
    PORTRAIT_PACKAGING_TEMPLATE
  ];
  const matched = localDefaults.find(
    (template) =>
      template.templateType === type &&
      Number(widthMm) === template.widthMm &&
      Number(heightMm) === template.heightMm
  );
  if (matched) {
    return cloneTemplate(matched).fields.map((field) => ({
      ...field,
      fontFamily: field.fontFamily || 'system'
    }));
  }
  const definitions = fieldDefinitions.value[type] || [];
  const rowHeight = Math.max(
    4,
    Math.min(7, (Number(heightMm) - 8) / Math.max(definitions.length, 1))
  );
  return definitions.map((field, index) => {
    const y = Math.min(4 + index * rowHeight, Math.max(Number(heightMm) - 5, 1) - 4);
    return {
      id: field.id,
      x: 4,
      y: Math.max(y, 0),
      width: Math.max(Number(widthMm) - 8, 1),
      height: 4,
      fontSize: field.kind === 'qrcode' ? 3 : 3.2,
      fontFamily: 'system',
      align: field.kind === 'qrcode' ? 'center' : 'left',
      bold: false,
      wrap: false,
      visible: index * rowHeight + 8 <= Number(heightMm)
    };
  });
}

function normalizeForm(template) {
  const source = cloneTemplate(template);
  source.fields = (source.fields || []).map((field) => ({
    visible: field.visible !== false,
    x: Number(field.x) || 0,
    y: Number(field.y) || 0,
    width: Number(field.width) || 1,
    height: Number(field.height) || 1,
    fontSize: Number(field.fontSize) || 3,
    fontFamily: field.fontFamily || 'system',
    align: field.align || 'left',
    bold: Boolean(field.bold),
    wrap: Boolean(field.wrap),
    id: field.id,
    ...(isCustomField(field) ? { text: field.text || '' } : {})
  }));
  return source;
}

async function loadTemplates() {
  loading.value = true;
  try {
    const data = await getPrintTemplates({
      all: '1',
      ...(typeFilter.value ? { type: typeFilter.value } : {}),
      ...(storeId.value ? { storeId: storeId.value } : {})
    });
    templates.value = data?.templates || [];
    templateTypes.value = data?.types || [];
    fieldDefinitions.value = data?.fields || {};
    fontOptions.value = data?.fonts?.length ? data.fonts : [...PRINT_FONT_OPTIONS];
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  const type = templateTypes.value[0]?.value || DEFAULT_PICKUP_TEMPLATE.templateType;
  const localDefault =
    [
      DEFAULT_PICKUP_TEMPLATE,
      DEFAULT_PROCESSING_TEMPLATE,
      DEFAULT_PACKAGING_TEMPLATE,
      DEFAULT_EQUIPMENT_TEMPLATE
    ].find((template) => template.templateType === type) || DEFAULT_PICKUP_TEMPLATE;
  const width = localDefault.widthMm;
  const height = localDefault.heightMm;
  form.value = {
    templateType: type,
    storeId: userStore.isSuperAdmin ? storeId.value || null : undefined,
    name: `${typeLabel(type)}新模板`,
    widthMm: width,
    heightMm: height,
    enabled: true,
    isDefault: false,
    fields: defaultFields(type, width, height)
  };
  editingId.value = null;
  selectedFieldId.value = '';
  editorVisible.value = true;
  nextTick(() => formRef.value?.clearValidate());
}
function openEdit(row) {
  form.value = normalizeForm(row);
  editingId.value = row.id;
  selectedFieldId.value = '';
  editorVisible.value = true;
  nextTick(() => formRef.value?.clearValidate());
}
function changeTemplateType(type) {
  if (!form.value || editingId.value) return;
  const localDefault = [
    DEFAULT_PICKUP_TEMPLATE,
    DEFAULT_PROCESSING_TEMPLATE,
    DEFAULT_PACKAGING_TEMPLATE,
    DEFAULT_EQUIPMENT_TEMPLATE
  ].find((template) => template.templateType === type);
  if (localDefault) {
    form.value.widthMm = localDefault.widthMm;
    form.value.heightMm = localDefault.heightMm;
  }
  form.value.fields = defaultFields(type, form.value.widthMm, form.value.heightMm);
  form.value.name = `${typeLabel(type)}新模板`;
}
function resetFields() {
  if (form.value)
    form.value.fields = defaultFields(
      form.value.templateType,
      form.value.widthMm,
      form.value.heightMm
    );
}

async function saveForm() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid || !form.value) return;
  if (form.value.fields.some((field) => isCustomField(field) && !field.text?.trim())) {
    ElMessage.warning('自定义文本不能为空');
    return;
  }
  saving.value = true;
  try {
    if (editingId.value) await updatePrintTemplate(editingId.value, form.value);
    else await createPrintTemplate(form.value);
    ElMessage.success(editingId.value ? '打印模板已更新' : '打印模板已创建');
    editorVisible.value = false;
    await loadTemplates();
  } finally {
    saving.value = false;
  }
}
async function setDefault(row) {
  await updatePrintTemplate(row.id, { ...row, isDefault: true });
  ElMessage.success('默认模板已更新');
  await loadTemplates();
}
async function toggleEnabled(row) {
  await updatePrintTemplate(row.id, { ...row, enabled: !row.enabled });
  ElMessage.success(row.enabled ? '模板已停用' : '模板已启用');
  await loadTemplates();
}
async function removeTemplate(row) {
  await ElMessageBox.confirm(`确认删除模板“${row.name}”吗？删除后不可恢复。`, '删除打印模板', {
    type: 'warning'
  });
  await deletePrintTemplate(row.id);
  ElMessage.success('打印模板已删除');
  await loadTemplates();
}

onMounted(async () => {
  if (userStore.isSuperAdmin)
    stores.value = (await getStores({ page: 1, pageSize: 100 }))?.list || [];
  await loadTemplates();
  qrDataUrl.value = await createQRCodeDataUrl(previewPackage.pickupCode, { width: 320 });
});
onBeforeUnmount(stopLayoutInteraction);
</script>

<style scoped>
.page-actions,
.toolbar,
.switch-row,
.table-actions,
.drawer-footer,
.section-title,
.field-name,
.field-actions,
.custom-field-editor {
  display: flex;
  align-items: center;
}
.page-actions,
.toolbar,
.switch-row,
.table-actions {
  gap: 10px;
}
.toolbar-tip,
.section-title span {
  color: var(--app-muted);
  font-size: 12px;
}
.toolbar-tip {
  margin-left: 4px;
}
.table-actions {
  justify-content: center;
  flex-wrap: wrap;
  gap: 5px;
}
.table-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.editor-layout {
  display: grid;
  grid-template-areas:
    'form form'
    'fields preview';
  grid-template-columns: minmax(0, 5fr) minmax(320px, 1fr);
  gap: 24px;
  min-height: 0;
}
.editor-top {
  display: contents;
}
.editor-form {
  grid-area: form;
  min-width: 0;
}
.editor-form-title {
  margin-top: 0;
}
.basic-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 0 16px;
}
.switch-row {
  flex-wrap: wrap;
}
.section-title {
  justify-content: space-between;
  gap: 12px;
  margin: 6px 0 12px;
}
.section-title > div {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.section-title > .field-actions {
  align-items: center;
}
.field-actions,
.custom-field-editor {
  gap: 6px;
}
.custom-field-editor {
  justify-content: center;
}
.custom-field-editor :deep(.el-input),
.custom-field-editor :deep(.el-textarea) {
  flex: 0 0 8em;
  width: 8em;
}
.custom-field-editor :deep(.el-button) {
  flex: 0 0 auto;
}
.field-editor-wrap {
  width: 100%;
  overflow-x: auto;
}
.fields-panel {
  grid-area: fields;
  min-width: 0;
  padding-top: 18px;
  border-top: 1px solid var(--app-border);
}
.field-layout-table :deep(.el-table__cell) {
  vertical-align: middle;
}
.field-layout-table :deep(.el-table__header .cell),
.field-layout-table :deep(.el-table__body .cell) {
  display: flex;
  min-height: 32px;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
}
.field-layout-table :deep(.layout-number-input) {
  width: 84px;
}
.field-layout-table :deep(.layout-font-select) {
  width: 110px;
}
.field-layout-table :deep(.layout-align-select) {
  width: 96px;
}
.field-name {
  justify-content: center;
  gap: 6px;
  white-space: nowrap;
}
.preview-panel {
  grid-area: preview;
  min-width: 0;
  padding-top: 18px;
  padding-left: 24px;
  border-top: 1px solid var(--app-border);
  border-left: 1px solid var(--app-border);
}
.preview-title {
  margin-top: 0;
}
.preview-stage {
  display: flex;
  min-height: 370px;
  align-items: center;
  justify-content: center;
  padding: 20px;
  overflow: auto;
  background: #f3f4f6;
  border: 1px solid var(--app-border);
  border-radius: 8px;
}
.layout-preview-canvas {
  position: relative;
  flex: 0 0 auto;
  touch-action: none;
}
.layout-preview-canvas :deep(.print-label) {
  pointer-events: none;
  box-shadow: 0 4px 18px rgb(15 23 42 / 12%);
}
.layout-field-overlay {
  position: absolute;
  z-index: 2;
  box-sizing: border-box;
  cursor: move;
  outline: 1px dashed transparent;
  touch-action: none;
}
.layout-field-overlay:hover {
  outline-color: rgb(64 158 255 / 65%);
  background: rgb(64 158 255 / 5%);
}
.layout-field-overlay.selected {
  z-index: 3;
  outline: 1.5px solid var(--el-color-primary);
  background: rgb(64 158 255 / 8%);
}
.layout-field-overlay:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 1px;
}
.layout-field-name {
  position: absolute;
  top: -20px;
  left: -1px;
  max-width: 120px;
  padding: 2px 5px;
  overflow: hidden;
  color: #fff;
  background: var(--el-color-primary);
  border-radius: 3px;
  font-size: 11px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.layout-resize-handle {
  position: absolute;
  right: -5px;
  bottom: -5px;
  width: 10px;
  height: 10px;
  box-sizing: border-box;
  cursor: nwse-resize;
  background: #fff;
  border: 2px solid var(--el-color-primary);
  border-radius: 2px;
}
:global(body.print-layout-interacting) {
  cursor: grabbing;
  user-select: none;
}
.preview-panel :deep(.el-alert) {
  margin-top: 14px;
}
.drawer-footer {
  justify-content: flex-end;
  gap: 10px;
}
@media (max-width: 1000px) {
  .editor-layout {
    grid-template-areas:
      'form'
      'preview'
      'fields';
    grid-template-columns: 1fr;
  }
  .preview-panel {
    padding: 18px 0 0;
    border-top: 1px solid var(--app-border);
    border-left: 0;
  }
}
@media (max-width: 680px) {
  .page-header,
  .toolbar,
  .section-title > div {
    align-items: stretch;
    flex-direction: column;
  }
  .basic-grid {
    grid-template-columns: 1fr;
  }
  .toolbar-tip {
    margin: 0;
  }
}
</style>
