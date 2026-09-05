export function ok(reply, data = {}, message = '') {
  return reply.send({ code: 0, message, data });
}

export function fail(reply, message = '服务器错误', statusCode = 500, data = undefined) {
  const body = { code: -1, message };
  if (data !== undefined && data !== null) {
    body.data = data;
  }
  return reply.status(statusCode).send(body);
}
