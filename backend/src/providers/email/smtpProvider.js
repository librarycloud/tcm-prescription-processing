import nodemailer from 'nodemailer';

export async function sendSmtpEmail(config, password, { to, subject, text }) {
  const transporter = nodemailer.createTransport({
    host: config.host,
    port: Number(config.port),
    secure: config.secure === 1,
    auth: config.username ? { user: config.username, pass: password } : undefined
  });

  return transporter.sendMail({
    from: config.fromEmail
      ? `${config.fromName || config.fromEmail} <${config.fromEmail}>`
      : config.username,
    to,
    subject,
    text
  });
}
