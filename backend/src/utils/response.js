export function ok(reply, data = {}, message = '') {
  return reply.send({ code: 0, message, data });
}

export function fail(reply, message = '服务器错误', statusCode = 500) {
  return reply.status(statusCode).send({ code: -1, message });
}
