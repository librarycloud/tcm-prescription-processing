import { ok } from "../utils/response.js";
import { listE6PharmacyProducts } from "../services/e6PharmacyService.js";

export async function listE6PharmacyProductsController(request, reply) {
  return ok(
    reply,
    await listE6PharmacyProducts(
      request.server.prisma,
      request.user,
      request.query || {},
    ),
  );
}
