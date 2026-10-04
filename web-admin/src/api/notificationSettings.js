import request from './request';

export function getRobotNotifications() { return request.get('/admin/notifications/robots'); }
export function createRobot(data) { return request.post('/admin/notifications/robots', data); }
export function updateRobot(id, data) { return request.put(`/admin/notifications/robots/${id}`, data); }
export function deleteRobot(id) { return request.delete(`/admin/notifications/robots/${id}`); }
export function testRobot(id, data) { return request.post(`/admin/notifications/robots/${id}/test`, data); }
export function updateRobotEvent(robotId, eventCode, data) { return request.put(`/admin/notifications/robots/${robotId}/events/${eventCode}`, data); }
export function resetRobotEvent(robotId, eventCode) { return request.post(`/admin/notifications/robots/${robotId}/events/${eventCode}/reset-template`); }
export function getRobotLogs(params) { return request.get('/admin/notifications/logs', { params }); }
export function getRobotLog(id) { return request.get(`/admin/notifications/logs/${id}`); }
export function retryRobotLog(id) { return request.post(`/admin/notifications/logs/${id}/retry`); }
