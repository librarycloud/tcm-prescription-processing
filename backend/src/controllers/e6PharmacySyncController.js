import { ok } from "../utils/response.js";
import {
  uploadE6PharmacyInventory,
  uploadE6PharmacyProducts,
  uploadE6PharmacyLocations,
} from "../services/e6PharmacySyncService.js";

export async function uploadProductsController(request, reply) {
  return ok(reply, await uploadE6PharmacyProducts(
    request.server.prisma,
    request.body || {},
    request.headers["x-api-key"],
  ), "商品上传成功");
}

export async function uploadInventoryController(request, reply) {
  return ok(reply, await uploadE6PharmacyInventory(
    request.server.prisma,
    request.body || {},
    request.headers["x-api-key"],
  ), "库存上传成功");
}

export async function uploadLocationsController(request, reply) {
  return ok(reply, await uploadE6PharmacyLocations(
    request.server.prisma,
    request.body || {},
    request.headers["x-api-key"],
  ), "货位上传成功");
}
