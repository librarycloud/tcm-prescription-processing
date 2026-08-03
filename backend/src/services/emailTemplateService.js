export function renderEmailTemplate(template, values) {
  const replace = (source) =>
    String(source || '').replace(/{{\s*([A-Za-z0-9_]+)\s*}}/g, (_match, key) => {
      return values[key] === undefined || values[key] === null ? '' : String(values[key]);
    });

  return {
    subject: replace(template.subject),
    content: replace(template.content)
  };
}
