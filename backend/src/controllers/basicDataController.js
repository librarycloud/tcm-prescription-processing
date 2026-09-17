import { ok } from '../utils/response.js';
import { createDictionary, deleteDictionary, listDictionaries, updateDictionary } from '../services/dictionaryService.js';
import { createDoctor, deleteDoctor, listDoctors, updateDoctor } from '../services/doctorService.js';

export async function listDictionariesController(request, reply) { return ok(reply, await listDictionaries(request.server.prisma, request.query?.type, request.query?.includeDisabled === '1')); }
export async function createDictionaryController(request, reply) { return ok(reply, await createDictionary(request.server.prisma, request.body || {}, request.user), '创建成功'); }
export async function updateDictionaryController(request, reply) { return ok(reply, await updateDictionary(request.server.prisma, request.params.id, request.body || {}, request.user), '更新成功'); }
export async function deleteDictionaryController(request, reply) { return ok(reply, await deleteDictionary(request.server.prisma, request.params.id, request.user), '删除成功'); }
export async function listDoctorsController(request, reply) { return ok(reply, await listDoctors(request.server.prisma, request.query?.includeDisabled === '1')); }
export async function createDoctorController(request, reply) { return ok(reply, await createDoctor(request.server.prisma, request.body || {}, request.user), '创建成功'); }
export async function updateDoctorController(request, reply) { return ok(reply, await updateDoctor(request.server.prisma, request.params.id, request.body || {}, request.user), '更新成功'); }
export async function deleteDoctorController(request, reply) { return ok(reply, await deleteDoctor(request.server.prisma, request.params.id, request.user), '删除成功'); }
