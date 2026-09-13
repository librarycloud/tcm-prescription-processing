import { defineStore } from 'pinia';
import { getDoctors } from '@/api/processing';
import { getStores } from '@/api/store';
import { getDictionaries } from '@/api/processing';

export const useDictionaryStore = defineStore('dictionary', {
  state: () => ({
    doctors: [],
    stores: [],
    processTypes: [],
    loaded: {
      doctors: false,
      stores: false,
      processTypes: false
    }
  }),
  actions: {
    async fetchDoctors(force = false) {
      if (this.loaded.doctors && !force) return this.doctors;
      try {
        const res = await getDoctors();
        this.doctors = res.list || res || [];
        this.loaded.doctors = true;
        return this.doctors;
      } catch (err) {
        console.error('Failed to fetch doctors', err);
        return [];
      }
    },
    async fetchStores(force = false) {
      if (this.loaded.stores && !force) return this.stores;
      try {
        const res = await getStores({ page: 1, pageSize: 999 });
        this.stores = res.list || res || [];
        this.loaded.stores = true;
        return this.stores;
      } catch (err) {
        console.error('Failed to fetch stores', err);
        return [];
      }
    },
    async fetchProcessTypes(force = false) {
      if (this.loaded.processTypes && !force) return this.processTypes;
      try {
        const res = await getDictionaries({ type: 'PROCESS_TYPE' });
        this.processTypes = res.list || res || [];
        this.loaded.processTypes = true;
        return this.processTypes;
      } catch (err) {
        console.error('Failed to fetch processTypes', err);
        return [];
      }
    }
  }
});
