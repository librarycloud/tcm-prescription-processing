<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="clearfix">
          <span>安全与验证码配置</span>
        </div>
      </template>

      <el-form ref="formRef" :model="form" label-width="180px" style="max-width: 600px;">
        <el-form-item label="开启验证码登录">
          <el-switch v-model="form.enable_captcha" active-value="1" inactive-value="0" />
          <div class="form-tip">开启后，管理后台和店员 App 登录将强制要求完成验证码校验。</div>
        </el-form-item>
        
        <template v-if="form.enable_captcha === '1'">
          <el-form-item label="极验 4.0 Captcha ID">
            <el-input v-model="form.geetest_captcha_id" placeholder="请输入申请到的极验 Captcha ID" />
          </el-form-item>
          
          <el-form-item label="极验 4.0 Captcha Key">
            <el-input v-model="form.geetest_captcha_key" type="password" show-password placeholder="请输入极验 Captcha Key" />
          </el-form-item>
        </template>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSave">保存配置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { getSystemConfigs, updateSystemConfigs } from '@/api/systemConfig';

const loading = ref(false);
const formRef = ref(null);
const form = ref({
  enable_captcha: '0',
  geetest_captcha_id: '',
  geetest_captcha_key: ''
});

async function fetchConfigs() {
  try {
    const res = await getSystemConfigs();
    if (res) {
      form.value.enable_captcha = res.enable_captcha || '0';
      form.value.geetest_captcha_id = res.geetest_captcha_id || '';
      form.value.geetest_captcha_key = res.geetest_captcha_key || '';
    }
  } catch (error) {
    console.error(error);
  }
}

async function handleSave() {
  try {
    loading.value = true;
    await updateSystemConfigs(form.value);
    ElMessage.success('系统配置保存成功！');
  } catch (error) {
    console.error(error);
    ElMessage.error('保存失败');
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  fetchConfigs();
});
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}
</style>
