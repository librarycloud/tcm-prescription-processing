import request from './request';

export function getRobotNotifications() { return request.get('/admin/robot-notifications/robots'); }
export function createRobot(data) { return request.post('/admin/robot-notifications/robots', data); }
export function updateRobot(id, data) { return request.put(`/admin/robot-notifications/robots/${id}`, data); }
export function deleteRobot(id) { return request.delete(`/admin/robot-notifications/robots/${id}`); }
export function testRobot(id, data) { return request.post(`/admin/robot-notifications/robots/${id}/test`, data); }
export function updateRobotEvent(robotId, eventCode, data) { return request.put(`/admin/robot-notifications/robots/${robotId}/events/${eventCode}`, data); }
export function resetRobotEvent(robotId, eventCode) { return request.post(`/admin/robot-notifications/robots/${robotId}/events/${eventCode}/reset-template`); }
export function getRobotLogs(params) { return request.get('/admin/robot-notifications/logs', { params }); }
export function getRobotLog(id) { return request.get(`/admin/robot-notifications/logs/${id}`); }
export function retryRobotLog(id) { return request.post(`/admin/robot-notifications/logs/${id}/retry`); }
