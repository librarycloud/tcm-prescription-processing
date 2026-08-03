<template>
  <div :class="embedded ? 'embedded-edit' : 'page'">
    <div v-if="!embedded" class="page-header">
      <div>
        <h1 class="page-title">编辑包裹</h1>
        <p class="page-subtitle">仅允许修改待取包裹的基础信息</p>
      </div>
      <StatusTag v-if="detail" :status="detail.status" />
    </div>

    <el-card v-loading="loading" class="form-card" shadow="never">
      <el-alert
        v-if="detail && isPicked(detail.status)"
        class="form-alert"
        title="已取包裹不能修改"
        type="warning"
        show-icon
        :closable="false"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" @submit.prevent>
        <el-form-item label="物品名称" prop="itemName">
          <el-input
            v-model.trim="form.itemName"
            maxlength="50"
            show-word-limit
            :disabled="disabled"
          />
        </el-form-item>
        <el-form-item label="备注" prop="itemInfo">
          <el-input
            v-model.trim="form.itemInfo"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            :disabled="disabled"
          />
        </el-form-item>
        <el-form-item label="收件人" prop="receiverName">
          <el-input
            v-model.trim="form.receiverName"
            maxlength="30"
            show-word-limit
            :disabled="disabled"
          />
        </el-form-item>
        <el-form-item label="手机号" prop="receiverPhone">
          <el-input
            v-model.trim="form.receiverPhone"
            maxlength="11"
            placeholder="选填，留空不关联用户"
            :disabled="disabled"
          />
        </el-form-item>
        <el-form-item label="取货方式" prop="pickupMethod">
          <el-select v-model="form.pickupMethod" placeholder="请选择取货方式" :disabled="disabled">
            <el-option
              v-for="item in PICKUP_METHOD_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <template v-if="[1, 2].includes(Number(form.pickupMethod))">
          <el-form-item label="地址">
            <el-input
              v-model.trim="form.expressAddress"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              :disabled="disabled"
              placeholder="选填"
            />
          </el-form-item>
          <el-form-item v-if="Number(form.pickupMethod) === 2" label="快递单号">
            <TrackingNumberInput v-model="form.expressTrackingNo" :disabled="disabled" />
          </el-form-item>
        </template>
        <el-form-item>
          <el-button type="primary" :loading="saving" :disabled="disabled" @click="handleSubmit">
            保存
          </el-button>
          <el-button @click="closeEditor">返回</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import StatusTag from '@/components/StatusTag.vue';
import { getAdminPackageDetail, updatePackage } from '@/api/package';
import { isValidPhone } from '@/utils/phone';
import { isPicked, PICKUP_METHOD_OPTIONS } from '@/utils/status';
import TrackingNumberInput from '@/components/TrackingNumberInput.vue';

const route = useRoute();
const router = useRouter();
const props = defineProps({
  id: {
    type: [Number, String],
    default: null
  },
  embedded: {
    type: Boolean,
    default: false
  }
});
const emit = defineEmits(['saved', 'cancel']);
const formRef = ref(null);
const loading = ref(false);
const saving = ref(false);
const detail = ref(null);

const form = reactive({
  itemName: '',
  itemInfo: '',
  receiverName: '',
  receiverPhone: '',
  pickupMethod: null,
  expressTrackingNo: '',
  expressAddress: ''
});

const disabled = computed(() => detail.value && isPicked(detail.value.status));

const rules = {
  itemName: [{ required: true, message: '请输入物品名称', trigger: 'blur' }],
  receiverName: [{ required: true, message: '请输入收件人', trigger: 'blur' }],
  receiverPhone: [
    {
      validator: (_rule, value, callback) => {
        if (value && !isValidPhone(value)) callback(new Error('请输入正确的手机号'));
        else callback();
      },
      trigger: 'blur'
    }
  ],
  pickupMethod: [{ required: true, message: '请选择取货方式', trigger: 'change' }]
};

async function loadDetail() {
  loading.value = true;
  try {
    const id = props.id ?? route.params.id;
    const data = await getAdminPackageDetail(id);
    detail.value = data;
    form.itemName = data.itemName || '';
    form.itemInfo = data.itemInfo || '';
    form.receiverName = data.receiverName || '';
    form.receiverPhone = data.receiverPhone || '';
    form.pickupMethod = data.pickupMethod ?? null;
    form.expressTrackingNo = data.expressTrackingNo || '';
    form.expressAddress = data.expressAddress || '';
  } finally {
    loading.value = false;
  }
}

async function handleSubmit() {
  await formRef.value.validate();
  saving.value = true;
  try {
    const id = props.id ?? route.params.id;
    await updatePackage(id, form);
    ElMessage.success('保存成功');
    if (props.embedded) emit('saved', detail.value);
    else router.replace(`/admin/packages/${id}`);
  } finally {
    saving.value = false;
  }
}

function closeEditor() {
  if (props.embedded) emit('cancel');
  else router.back();
}

onMounted(loadDetail);
</script>

<style scoped>
.form-alert {
  margin-bottom: 18px;
}
</style>
