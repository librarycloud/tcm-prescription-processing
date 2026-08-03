import { listDoctors, saveDoctor, deleteDoctor as remove } from './prescriptionReferenceService.js';
export { listDoctors };
export function createDoctor(prisma, payload, actor) { return saveDoctor(prisma, null, payload, actor); }
export function updateDoctor(prisma, id, payload, actor) { return saveDoctor(prisma, id, payload, actor); }
export function deleteDoctor(prisma, id, actor) { return remove(prisma, id, actor); }
