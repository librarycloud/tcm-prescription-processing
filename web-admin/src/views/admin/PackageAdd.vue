<template>
  <div :class="embedded ? 'embedded-add' : 'page'">
    <div v-if="!embedded" class="page-header">
      <div>
        <h1 class="page-title">新增包裹</h1>
        <p class="page-subtitle">录入包裹信息后系统会自动生成取货码</p>
      </div>
    </div>

    <el-card class="form-card" shadow="never">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" @submit.prevent>
        <el-form-item v-if="userStore.isSuperAdmin" label="所属门店" prop="storeId">
          <el-select v-model="form.storeId" filterable placeholder="请选择所属门店">
            <el-option v-for="item in stores" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="物品名称" prop="itemName">
          <el-input v-model.trim="form.itemName" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="备注" prop="itemInfo">
          <el-input
            v-model.trim="form.itemInfo"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="收件人" prop="receiverName">
          <el-input v-model.trim="form.receiverName" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="手机号" prop="receiverPhone">
          <el-autocomplete
            v-model.trim="form.receiverPhone"
            :fetch-suggestions="searchUsers"
            value-key="phone"
            maxlength="11"
            clearable
            placeholder="选填，留空不创建用户"
            @input="handlePhoneInput"
            @select="handleUserSelect"
          >
            <template #default="{ item }">
              <div class="user-suggestion">
                <span>{{ item.phone }}</span>
                <span class="user-suggestion-meta">{{
                  item.nickname || item.name || '未填写姓名'
                }}</span>
              </div>
            </template>
          </el-autocomplete>
        </el-form-item>
        <template v-if="matchedUser">
          <el-form-item label="用户姓名">
            <el-input :model-value="matchedUser.name || '-'" disabled />
          </el-form-item>
          <el-form-item label="用户备注">
            <el-input :model-value="matchedUser.remark || '-'" disabled />
          </el-form-item>
        </template>
        <template v-else-if="isNewUser">
          <el-form-item label="新用户姓名">
            <el-input v-model.trim="form.newUserName" maxlength="64" show-word-limit />
          </el-form-item>
          <el-form-item label="新用户备注">
            <el-input
              v-model.trim="form.newUserRemark"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </template>
        <el-form-item label="取货方式" prop="pickupMethod">
          <el-select v-model="form.pickupMethod" placeholder="请选择取货方式">
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
              placeholder="选填"
            />
          </el-form-item>
          <el-form-item v-if="Number(form.pickupMethod) === 2" label="快递单号">
            <TrackingNumberInput v-model="form.expressTrackingNo" />
          </el-form-item>
        </template>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSubmit">保存</el-button>
          <el-button @click="closeCreator">返回</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { createPackage } from '@/api/package';
import { isValidPhone } from '@/utils/phone';
import { PICKUP_METHOD_OPTIONS } from '@/utils/status';
import { getStores } from '@/api/store';
import { matchAdminUsers } from '@/api/adminUser';
import { useUserStore } from '@/stores/user';
import { ROLES } from '@/utils/permission';
import TrackingNumberInput from '@/components/TrackingNumberInput.vue';

const router = useRouter();
const props = defineProps({
  embedded: {
    type: Boolean,
    default: false
  }
});
const emit = defineEmits(['saved', 'cancel']);
const formRef = ref(null);
const loading = ref(false);
const stores = ref([]);
const matchedUser = ref(null);
const userStore = useUserStore();

const form = reactive({
  storeId: null,
  itemName: '',
  itemInfo: '',
  receiverName: '',
  receiverPhone: '',
  newUserName: '',
  newUserRemark: '',
  pickupMethod: null,
  expressTrackingNo: '',
  expressAddress: ''
});

const isNewUser = computed(() => isValidPhone(form.receiverPhone) && !matchedUser.value);

const rules = {
  storeId: [{ required: true, message: '请选择所属门店', trigger: 'change' }],
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

async function handleSubmit() {
  await formRef.value.validate();
  loading.value = true;
  try {
    await createPackage(form);
    ElMessage.success('新增成功');
    if (props.embedded) emit('saved');
    else router.replace('/admin/packages');
  } finally {
    loading.value = false;
  }
}

function closeCreator() {
  if (props.embedded) emit('cancel');
  else router.back();
}

async function searchUsers(queryString, callback) {
  const phone = String(queryString || '').trim();
  if (phone.length < 7) {
    callback([]);
    return;
  }
  try {
    const users = (await matchAdminUsers(phone)) || [];
    callback(users.filter((user) => Number(user.role) === ROLES.USER));
  } catch {
    callback([]);
  }
}

function handleUserSelect(user) {
  form.receiverPhone = user.phone;
  matchedUser.value = user;
  formRef.value?.clearValidate('receiverPhone');
}

function handlePhoneInput(value) {
  if (String(value || '').trim() !== matchedUser.value?.phone) {
    matchedUser.value = null;
  }
}

async function loadStores() {
  if (!userStore.isSuperAdmin) return;
  const data = await getStores({ page: 1, pageSize: 100, status: 1 });
  stores.value = data?.list || [];
}

onMounted(loadStores);
</script>

<style scoped>
.user-suggestion {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.user-suggestion-meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
