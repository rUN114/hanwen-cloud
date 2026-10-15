import type {Course} from './models';
export function canTeach(course:Course|null|undefined){return course?.accessRole==='OWNER'||course?.accessRole==='CO_TEACHER';}
export const courseRoles:Record<string,string>={OWNER:'主讲',CO_TEACHER:'协同教师',ASSISTANT:'助教',NONE:'未加入团队'};
