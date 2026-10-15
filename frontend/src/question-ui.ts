import type { QuizQuestion } from './models';
export const typeNames = { SINGLE: '单选', MULTIPLE: '多选', TRUE_FALSE: '判断', FILL_BLANK: '填空', SHORT_ANSWER: '简答' };
export function manual(type:string){return type==='FILL_BLANK'||type==='SHORT_ANSWER';}
export interface QuestionDraft {
  questionType: QuizQuestion['questionType'];
  prompt: string; optionA: string; optionB: string; optionC: string; optionD: string;
  correctOption: string; explanation: string; points: number; knowledgePoint:string;
}
export function emptyQuestion(): QuestionDraft {
  return { questionType: 'SINGLE', prompt: '', optionA: '', optionB: '', optionC: '',
    optionD: '', correctOption: 'A', explanation: '', points: 1, knowledgePoint:'' };
}
export function questionDraft(q: QuizQuestion): QuestionDraft {
  return {questionType:q.questionType,prompt:q.prompt,optionA:q.optionA,optionB:q.optionB,
    optionC:q.optionC,optionD:q.optionD,correctOption:q.correctOption||'A',explanation:q.explanation||'',points:q.points,knowledgePoint:q.knowledgePoint||''};
}
export function options(q: Pick<QuestionDraft,'questionType'|'optionA'|'optionB'|'optionC'|'optionD'>) {
  if(manual(q.questionType))return [];
  return (q.questionType === 'TRUE_FALSE' ? ['A','B'] : ['A','B','C','D'])
    .map(key => ({key, text: q[('option' + key) as 'optionA']}));
}
export function toggleChoice(current: string, key: string, checked: boolean) {
  return [...new Set((checked ? current + key : current.replace(key,'')))].sort().join('');
}
export function localInput(value: string | null) {
  if (!value) return '';
  const d = new Date(value);
  const local = new Date(d.getTime() - d.getTimezoneOffset()*60000);
  return local.toISOString().slice(0,16);
}
export function offsetInput(value: string) { return value ? new Date(value).toISOString() : null; }
