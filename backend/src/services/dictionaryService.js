export { listDictionaries } from './prescriptionReferenceService.js';
import { saveDictionary, deleteDictionary as remove } from './prescriptionReferenceService.js';
export function createDictionary(prisma, payload, actor) { return saveDictionary(prisma, null, payload, actor); }
export function updateDictionary(prisma, id, payload, actor) { return saveDictionary(prisma, id, payload, actor); }
export function deleteDictionary(prisma, id, actor) { return remove(prisma, id, actor); }
