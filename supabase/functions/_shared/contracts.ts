import { ApiError } from './http.ts';
export const tasks = ['summary', 'mind_map', 'vision', 'chat', 'followup_email'] as const;
export type Task = typeof tasks[number];
export type Segment = { seq: number; startMs: number; endMs: number; speaker?: string; text: string; recordingId?: string };
export function uuid(value: unknown): value is string {
  return typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}
export function validateRequest(value: Record<string, unknown>): asserts value is Record<string, unknown> & { task: Task; language: 'es' | 'en' } {
  if (!tasks.includes(value.task as Task) || !['es', 'en'].includes(value.language as string)) throw new ApiError(400, 'invalid_task_or_language');
  for (const field of ['recordingId', 'templateId']) {
    if (value[field] != null && !uuid(value[field])) throw new ApiError(400, 'invalid_' + field);
  }
  if (value.task === 'chat' && (typeof value.question !== 'string' || value.question.trim().length === 0 || value.question.length > 8000)) {
    throw new ApiError(400, 'invalid_question');
  }
  if (value.transcript != null) validateSegments(value.transcript);
  if (value.contextSegments != null) validateSegments(value.contextSegments);
  if (value.task === 'vision' && (typeof value.imagePath !== 'string' || value.imagePath.length > 512 || value.imagePath.includes('..'))) throw new ApiError(400, 'invalid_image_path');
  if (value.history != null && (!Array.isArray(value.history) || value.history.length > 20 || value.history.some(m =>
    !m || !['user','assistant'].includes(m.role) || typeof m.content !== 'string' || m.content.length > 8000))) throw new ApiError(400, 'invalid_history');
}
export function validateSegments(value: unknown): asserts value is Segment[] {
  if (!Array.isArray(value) || value.length > 10000) throw new ApiError(400, 'invalid_segments');
  for (const s of value) {
    if (!s || typeof s.text !== 'string' || s.text.length > 20000 || !Number.isInteger(s.seq) || s.seq < 0 ||
        !Number.isSafeInteger(s.startMs) || !Number.isSafeInteger(s.endMs) || s.startMs < 0 || s.endMs < s.startMs) throw new ApiError(400, 'invalid_segment');
  }
}
const strings = (v: unknown): v is string[] => Array.isArray(v) && v.every(x => typeof x === 'string');
const text = (v: unknown): v is string => typeof v === 'string';
function validDate(v: unknown): boolean {
  if (!text(v) || !/^\d{4}-\d{2}-\d{2}$/.test(v)) return false;
  const parsed = new Date(v + 'T00:00:00Z');
  return !isNaN(parsed.getTime()) && parsed.toISOString().slice(0,10) === v;
}
export function validateOutput(task: Task, value: unknown): value is Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const v = value as Record<string, unknown>;
  switch (task) {
    case 'summary': return text(v.executive) && text(v.detailed) && strings(v.keyTopics) && strings(v.decisions) && strings(v.openQuestions) &&
      Array.isArray(v.actionItems) && v.actionItems.every(a => a && text(a.description) && (a.assignee == null || text(a.assignee)) &&
        (a.dueDate == null || validDate(a.dueDate)) &&
        (a.sourceMs == null || (Number.isSafeInteger(a.sourceMs) && a.sourceMs >= 0)));
    case 'mind_map': return validTree(v.tree) && text(v.markdown);
    case 'vision': return text(v.description);
    case 'chat': return text(v.answer) && Array.isArray(v.citations) && v.citations.every(c => c && Number.isSafeInteger(c.ms) && c.ms >= 0 && text(c.text));
    case 'followup_email': return text(v.subject) && text(v.body);
  }
}
function validTree(v: unknown, depth = 0): boolean {
  if (!v || typeof v !== 'object' || depth > 12) return false;
  const node = v as Record<string, unknown>;
  return text(node.title) && Array.isArray(node.children) && node.children.length <= 100 && node.children.every(c => validTree(c, depth + 1));
}
export const outputInstructions: Record<Task, string> = {
  summary: 'Return {executive:string,detailed:string,keyTopics:string[],decisions:string[],openQuestions:string[],actionItems:[{description:string,assignee:string|null,dueDate:YYYY-MM-DD|null,sourceMs:number|null}]}. Never invent names or dates.',
  mind_map: 'Return {tree:{title:string,children:[recursive nodes with title and children]},markdown:string}.',
  vision: 'Return {description:string}. Describe only what is visible.',
  chat: 'Return {answer:string,citations:[{ms:number,text:string}]}. Cite only supplied transcript passages and timestamps. Say when evidence is insufficient.',
  followup_email: 'Return {subject:string,body:string}. Draft only; do not send email. Never invent commitments.',
};
