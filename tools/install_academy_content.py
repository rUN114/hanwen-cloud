"""Install the reading curriculum into an empty Hanwen teaching environment.

This tool never deletes existing teaching content or creates student activity.
Use --validate to check the catalog, or --sql-out to review the import SQL.
Database credentials must be supplied in a private MySQL client option file.
"""

import argparse
import hashlib
import json
import os
import secrets
import subprocess
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / 'database/content'
RUBRIC = [
    {'name': '文本依据与引用准确', 'maxScore': '35'},
    {'name': '分析与论证', 'maxScore': '35'},
    {'name': '结构与表达', 'maxScore': '20'},
    {'name': '来源与版本说明', 'maxScore': '10'},
]


def load_catalog():
    works = json.loads((CONTENT / 'works.json').read_text(encoding='utf8'))
    courses = json.loads((CONTENT / 'courses.json').read_text(encoding='utf8'))
    keys = [work['key'] for work in works]
    if len(keys) != len(set(keys)) or len({c['key'] for c in courses}) != len(courses):
        raise ValueError('Catalog keys must be unique')
    for work in works:
        if urlparse(work['source']).hostname != 'zh.wikisource.org':
            raise ValueError('Each work requires its original text source')
        for field in ('title', 'author', 'text', 'guide', 'edition', 'translation', 'annotations'):
            if not work[field].strip():
                raise ValueError(f"Missing {field}: {work['key']}")
        if len('ACADEMY-' + work['key']) > 30:
            raise ValueError('Literature seed key exceeds the schema limit')
    for course in courses:
        if course['teacher'] not in ('lincheng', 'shenyan'):
            raise ValueError('Catalog teacher must identify an existing teaching account')
        if len(course['title']) > 100 or len(course['notice']) > 2000:
            raise ValueError('Course content exceeds its field limit')
        for chapter in course['chapters']:
            if not chapter['works'] or any(key not in keys for key in chapter['works']):
                raise ValueError('A chapter references an unknown work')
            choice = chapter['choice']
            if len(choice['options']) != 4 or choice['answer'] not in {'A', 'B', 'C', 'D'}:
                raise ValueError('A choice question requires four options and one answer')
            if len(set(choice['options'])) != 4:
                raise ValueError('Choice options must be distinct')
            if len(chapter['guide']) < 180 or not chapter['question'] or not chapter['answer']:
                raise ValueError('A chapter needs a substantive guide and an open question')
    return works, courses


def text(value):
    """SQL strings are quoted under the explicit NO_BACKSLASH_ESCAPES session mode."""
    return "'" + str(value).replace("'", "''") + "'"


def work_text(work):
    return (f"《{work['title']}》\n{work['dynasty']}·{work['author']}\n\n{work['text']}\n\n"
            f"词义提示\n{work['annotations']}\n\n参考译意（书院编写）\n{work['translation']}\n\n"
            f"原文来源：{work['source']}\n版本说明：{work['edition']}\n")


def chapter_text(chapter, works):
    selections = '\n\n'.join(work_text(works[key]) for key in chapter['works'])
    return (f"学习目标\n{chapter['goal']}\n\n一、原文与词义\n{selections}\n\n"
            f"二、阅读导引\n{chapter['guide']}\n\n三、动笔练习\n{chapter['practice']}\n\n"
            "四、完成本章\n先完成章节练习，再记录自己的疑问。简答题由教师批阅；"
            "完成阅读后可手动标记章节。译意、导读与练习由书院编写，古文版本见各篇来源说明。")


def resource(course, works):
    chapters = '\n\n'.join(
        f"第{i}章 {chapter['title']}\n\n{chapter_text(chapter, works)}"
        for i, chapter in enumerate(course['chapters'], 1)
    )
    data = (course['title'] + '\n学习导引\n\n' + course['summary'] + '\n\n' + chapters).encode('utf8')
    sha = hashlib.sha256(data).hexdigest()
    return {'data': data, 'sha256': sha, 'storage': 'academy-' + sha[:40] + '.txt',
            'name': course['title'].replace('：', '_') + '_学习导引.txt'}


def chapter_sql(course_id, teacher, order, chapter, works):
    lines = []
    lines.append('INSERT INTO hw_course.course_chapter(course_id,title,content,sort_no,created_at,updated_at) '
                 f'VALUES({course_id},{text(chapter["title"])},{text(chapter_text(chapter, works))},{order},NOW(3),NOW(3));')
    lines.append('SET @chapter=LAST_INSERT_ID();')
    lines.append('INSERT INTO hw_course.chapter_quiz(course_id,chapter_id,title,instructions,status,version,created_at,updated_at,time_limit_minutes,max_attempts) '
                 f"VALUES({course_id},@chapter,{text(chapter['title'] + ' · 阅读练习')},"
                 "'先完成一道文本观察题，再写简答。简答由教师按依据、分析与表达批阅，成绩在批阅后确定。',"
                 "'PUBLISHED',0,NOW(3),NOW(3),0,3);")
    lines.append('SET @quiz=LAST_INSERT_ID();')
    choice = chapter['choice']
    questions = [
        {'type': 'SINGLE', 'prompt': choice['prompt'], 'options': choice['options'],
         'answer': choice['answer'], 'explanation': choice['explanation'], 'points': 40},
        {'type': 'SHORT_ANSWER', 'prompt': chapter['question'], 'options': ['', '', '', ''],
         'answer': chapter['answer'], 'explanation': '按文本依据、解释连贯和表达清楚程度评分；允许不同且有依据的解释。', 'points': 60},
    ]
    for sort, question in enumerate(questions, 1):
        values = ','.join(text(question[field]) for field in ('type', 'prompt'))
        options = ','.join(text(option) for option in question['options'])
        suffix = (f"{options},{text(question['answer'])},{text(question['explanation'])},"
                  f"{question['points']},{text(chapter['goal'][:60])}")
        lines.append('INSERT INTO hw_course.question_bank(teacher_id,question_type,prompt,option_a,option_b,option_c,option_d,correct_option,explanation,points,topic,status,version,created_at,updated_at) '
                     f"VALUES(@{teacher},{values},{suffix},'ACTIVE',0,NOW(3),NOW(3));")
        lines.append('SET @bank=LAST_INSERT_ID();')
        lines.append('INSERT INTO hw_course.quiz_question(quiz_id,question_type,prompt,option_a,option_b,option_c,option_d,correct_option,explanation,points,knowledge_point,sort_no,bank_id,bank_version) '
                     f'VALUES(@quiz,{values},{suffix},{sort},@bank,0);')
    return lines


def render_sql(works, courses):
    lookup = {work['key']: work for work in works}
    lines = ["SET NAMES utf8mb4;",
             "SET SESSION sql_mode=CONCAT_WS(',',NULLIF(@@SESSION.sql_mode,''),'NO_BACKSLASH_ESCAPES');",
             'START TRANSACTION;']
    for owner in sorted({c['teacher'] for c in courses}):
        lines.append(f"SET @{owner}=(SELECT id FROM hw_user.sys_user WHERE username={text(owner)} AND role='TEACHER' AND status='ENABLED');")
    for work in works:
        # Only the four shipped public-domain classics are updated by their stable seed key.
        old_keys = {'quiet-night': 'HW-CLASSIC-1', 'spring-dawn': 'HW-CLASSIC-2',
                    'humble-room': 'HW-CLASSIC-3', 'lotus-love': 'HW-CLASSIC-4'}
        key = old_keys.get(work['key'], 'ACADEMY-' + work['key'])
        fields = {'teacher_id': '@lincheng', 'title': text(work['title']), 'author': text(work['author']),
                  'dynasty': text(work['dynasty']), 'genre': text(work['genre']), 'content': text(work['text']),
                  'reading_guide': text(work['guide']), 'source_url': text(work['source']),
                  'source_note': text(work['edition'] + ' 原文繁体转简体，标点按阅读需要整理；译意、词义提示与导读由书院编写。'),
                  'translation': text(work['translation']), 'annotations_text': text(work['annotations']),
                  'allusions_json': text('[]'), 'status': text('PUBLISHED')}
        assignments = ','.join(f'{column}={value}' for column, value in fields.items())
        lines.append(f"UPDATE hw_course.literature_work SET {assignments},updated_at=NOW(3) WHERE seed_key={text(key)};")
        columns = ','.join(fields)
        values = ','.join(fields.values())
        lines.append(f"INSERT INTO hw_course.literature_work(seed_key,{columns},version,created_at,updated_at) "
                     f"SELECT {text(key)},{values},0,NOW(3),NOW(3) WHERE NOT EXISTS "
                     f"(SELECT 1 FROM hw_course.literature_work WHERE seed_key={text(key)});")
    for index, course in enumerate(courses):
        cid = f'@course{index}'
        lines.append('INSERT INTO hw_course.course(teacher_id,name,summary,category,status,created_at,updated_at) '
                     f"VALUES(@{course['teacher']},{text(course['title'])},{text(course['summary'])},{text(course['category'])},'PUBLISHED',NOW(3),NOW(3));")
        lines.append(f'SET {cid}=LAST_INSERT_ID();')
        lines.append('INSERT INTO hw_course.course_team_state(id,version) ' + f'VALUES({cid},0);')
        for order, chapter in enumerate(course['chapters'], 1):
            lines.extend(chapter_sql(cid, course['teacher'], order, chapter, lookup))
        lines.append('INSERT INTO hw_course.course_notice(course_id,author_id,title,content,pinned,version,created_at,updated_at) '
                     f"VALUES({cid},@{course['teacher']},'阅读路线与提交说明',{text(course['notice'])},1,0,NOW(3),NOW(3));")
        guide = resource(course, lookup)
        lines.append('INSERT INTO hw_course.course_resource(course_id,title,description,original_name,storage_name,mime_type,bytes,sha256,status,created_at) '
                     f"VALUES({cid},'全课学习导引','包含原文出处、词义提示、章节导读和动笔任务，可下载离线阅读。',"
                     f"{text(guide['name'])},{text(guide['storage'])},'text/plain',{len(guide['data'])},{text(guide['sha256'])},'ACTIVE',NOW(3));")
        for assignment in course['assignments']:
            lines.append('INSERT INTO hw_assignment.assignment_task(course_id,teacher_id,title,description,max_score,deadline_at,status,created_at,updated_at,rubric_json) '
                         f"VALUES({cid},@{course['teacher']},{text(assignment['title'])},{text(assignment['description'])},100,NULL,'PUBLISHED',NOW(3),NOW(3),{text(json.dumps(RUBRIC, ensure_ascii=False))});")
        # No class members, enrolments, grades or learner progress are fabricated.
        join_code = secrets.token_hex(6).upper()
        lines.append('INSERT INTO hw_learning.teaching_class(course_id,teacher_id,name,semester,description,join_code,status,version,created_at,updated_at) '
                     f"VALUES({cid},@{course['teacher']},{text(course['title'].split('：')[0] + ' · 自主研读班')},'自主学习',"
                     f"'按个人进度阅读；加入课程后可进入学舍，完成练习与习作。',{text(join_code)},'ACTIVE',0,NOW(3),NOW(3));")
    lines.append('COMMIT;')
    return '\n'.join(lines) + '\n'


def mysql(client, option_file, sql):
    result = subprocess.run(
        [str(client), '--defaults-file=' + str(option_file.resolve()), '--batch', '--raw', '--skip-column-names'],
        input=sql.encode('utf8'), capture_output=True,
    )
    if result.returncode:
        # Errors can contain SQL values; keep them in the local terminal, never in a public report.
        raise RuntimeError(result.stderr.decode('utf8', errors='replace').strip())
    return result.stdout.decode('utf8').strip()


def install(client, option_file, storage, works, courses):
    counts = mysql(client, option_file,
                   'SELECT (SELECT COUNT(*) FROM hw_course.course),'
                   '(SELECT COUNT(*) FROM hw_course.question_bank),'
                   '(SELECT COUNT(*) FROM hw_assignment.assignment_task),'
                   "(SELECT COUNT(*) FROM hw_course.literature_work WHERE seed_key NOT IN ('HW-CLASSIC-1','HW-CLASSIC-2','HW-CLASSIC-3','HW-CLASSIC-4') OR seed_key IS NULL);")
    if counts != '0\t0\t0\t0':
        raise ValueError('Existing teaching content found; import refused. Audit and preserve it before installation.')
    for owner in sorted({c['teacher'] for c in courses}):
        count = mysql(client, option_file,
                      f"SELECT COUNT(*) FROM hw_user.sys_user WHERE username={text(owner)} AND role='TEACHER' AND status='ENABLED';")
        if count != '1':
            raise ValueError(f'An enabled teacher account is required: {owner}')
    if not storage.is_dir() or storage.is_symlink():
        raise ValueError('An existing private upload directory is required')
    written = []
    lookup = {work['key']: work for work in works}
    try:
        for course in courses:
            guide = resource(course, lookup)
            path = storage / guide['storage']
            if path.exists():
                if path.is_symlink() or path.read_bytes() != guide['data']:
                    raise ValueError('An existing immutable upload differs from the catalog')
            else:
                with path.open('xb') as stream:
                    stream.write(guide['data'])
                written.append(path)
        mysql(client, option_file, render_sql(works, courses))
    except Exception:
        for path in written:
            path.unlink()
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--validate', action='store_true')
    parser.add_argument('--sql-out', type=Path)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--client-file', type=Path)
    parser.add_argument('--mysql-dir', type=Path, default=Path('.'))
    parser.add_argument('--storage', type=Path)
    args = parser.parse_args()
    works, courses = load_catalog()
    if args.sql_out:
        args.sql_out.write_text(render_sql(works, courses), encoding='utf8')
    if args.apply:
        if not args.client_file or not args.client_file.is_file() or not args.storage:
            parser.error('--apply requires a private --client-file and existing --storage directory')
        client = args.mysql_dir / ('mysql.exe' if os.name == 'nt' else 'mysql')
        install(client, args.client_file, args.storage, works, courses)
        marker = ROOT / '.local/academy-content.json'
        marker.parent.mkdir(exist_ok=True)
        marker.write_text(json.dumps({'profile': 'academy-reading-2026-10',
                                     'courses': len(courses), 'works': len(works),
                                     'learnerActivityFabricated': False}, indent=2), encoding='utf8')
    if not (args.validate or args.sql_out or args.apply):
        parser.error('Choose --validate, --sql-out or --apply')
    print(json.dumps({'result': 'PASS', 'applied': args.apply, 'courses': len(courses),
                      'chapters': sum(len(c['chapters']) for c in courses), 'works': len(works),
                      'questions': sum(len(c['chapters']) * 2 for c in courses),
                      'assignments': sum(len(c['assignments']) for c in courses)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
