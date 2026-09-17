export class RobotProviderError extends Error {
  constructor(message, code = 'ROBOT_SEND_FAILED', response = null) {
    super(message);
    this.name = 'RobotProviderError';
    this.code = code;
    this.response = response;
  }
}
