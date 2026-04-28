-- Migration: backfill postgrad English I task2 prompt images and descriptions
-- Source PDFs: F:\最后一次了\英语\英语真题\真题及答案速查（2004-2023）

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the following drawing. In your essay, you should first describe the drawing, then interpret its meaning, and give your comment on it.',
    image_url = '/uploads/past-prompts/postgrad/en1/2005-task2.png',
    image_description = '养老"足球赛"。图中有四个孩子（大儿子、二儿子、三儿子、小女儿）各自用足球门框承担着老父亲的身体，隐喻子女推诿养老责任的社会现象。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2005-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Study the following photos carefully and write an essay in which you should describe the photos briefly, interpret the social phenomenon reflected by them, and give your point of view.',
    image_url = '/uploads/past-prompts/postgrad/en1/2006-task2.png',
    image_description = '左图：把崇拜写在脸上；右图：花300元做个"小贝头"（Beckham贝克汉姆——英国足球名星）。两张照片展示中国青少年崇拜贝克汉姆的行为：一人将BECKHAM字样写在脸上，另一人花高价做贝克汉姆同款发型，反映盲目崇拜明星的社会现象。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2006-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the following drawing. In your essay, you should: 1) describe the drawing briefly, 2) explain its intended meaning, and then 3) support your view with an example/examples.',
    image_url = '/uploads/past-prompts/postgrad/en1/2007-task2.png',
    image_description = '（猫做梦踢足球）。一只小猫趴在书上睡觉，梦中幻想自己在踢足球进球得分，寓意脱离实际的空想，或对比现实与理想之间的差距。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2007-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the following drawing. In your essay, you should: 1) describe the drawing briefly, 2) explain its intended meaning, and then 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2008-task2.png',
    image_description = '你一条腿，我一条腿；你我一起，走南闯北。两人各只有一条腿，但合力用双腿共同支撑，协作前行，寓意合作共赢、互助的重要性。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2008-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the following drawing. In your essay, you should: 1) describe the drawing briefly, 2) explain its intended meaning, and then 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2009-task2.png',
    image_description = '网络的"近"与"远"。图中一张蜘蛛网形状的图，网上坐满了各种人（玩电脑的、聊天的等），寓意网络让地理上遥远的人变近，却让身边的人变远。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2009-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the following drawing. In your essay, you should: 1) describe the drawing briefly, 2) explain its intended meaning, and 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2010-task2.png',
    image_description = '文化"火锅"，既美味又营养。一个火锅中装有写着各种文化词汇的食材（佛教、舞蹈、音乐、名著、围棋、京剧等），寓意文化多元融合，兼容并蓄。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2010-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the following drawing. In your essay, you should: 1) describe the drawing briefly, 2) explain its intended meaning, and 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2011-task2.png',
    image_description = '旅程之"余"。一艘小船在河上行驶，船上坐着一个人，船后方河面漂满了垃圾废物，寓意旅游/发展过程中对环境的破坏。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2011-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the following drawing. In your essay, you should: 1) describe the drawing briefly, 2) explain its intended meaning, and 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2012-task2.png',
    image_description = '左图人物说：全完了！；右图人物说：幸好还剩点儿。同一件事（桶里的东西洒了大半），两个人态度截然不同：一人绝望，一人乐观，寓意面对挫折心态的重要性。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2012-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the following drawing. In your essay, you should: 1) describe the drawing briefly, 2) interpret its intended meaning, and 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2013-task2.png',
    image_description = '选择。图中一排毕业生站在高处，面前是一条向上的陡坡，坡上写着：就业、出国、创业、升学等选项，寓意毕业生面临人生道路的多元选择。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2013-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the following drawing. In your essay, you should: 1) describe the drawing briefly, 2) interpret its intended meaning, and 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2014-task2.png',
    image_description = '相携。对比图：三十年前，高个子妈妈牵着矮小孩子的手；现在，高个子孩子牵着矮小老人（妈妈）的手，寓意亲情传承与反哺，儿女长大后反过来照顾父母。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2014-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the following drawing. In your essay, you should: 1) describe the picture briefly, 2) interpret its intended meaning, and 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2015-task2.png',
    image_description = '手机时代的聚会。四个人坐在餐桌前聚会，桌上摆满菜肴，但四人全都低头玩手机，无人互动交流，讽刺手机对人际关系的侵蚀。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2015-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the following pictures. In your essay, you should: 1) describe the pictures briefly, 2) interpret the meaning, and 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2016-task2.png',
    image_description = '与其只提要求，不如做个榜样。左图：父亲指着儿子说"你给我好好学习！"，儿子愁眉苦脸；右图：父亲自己伏案认真工作/学习，儿子也在旁边勤奋学习，寓意以身作则的重要性。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2016-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the following pictures. In your essay, you should: 1) describe the picture briefly, 2) interpret the meaning, and 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2017-task2.png',
    image_description = '"有书"与"读书"。左图：一人坐在堆满书的书房里，满足地说"我有这么多书！"；右图：一人坐在书桌前认真阅读，说"我争取今年读完20本书。"对比拥有书与真正读书的区别，寓意行动力比占有更重要。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2017-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the picture below. In your essay, you should: 1) describe the picture briefly, 2) interpret the meaning, and 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2018-task2.png',
    image_description = '选课进行时。一名学生坐在电脑前面对选课系统，左边气泡写着"知识新、重创新、有难度"，右边气泡写着"给分高、易通过、作业少"，揭示大学生选课时重功利轻学习的心态。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2018-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the picture below. In your essay, you should: 1) describe the picture briefly, 2) interpret the implied meaning, and 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2019-task2.png',
    image_description = '途中。两人爬山途中坐在山坡上休息，一人说"累了，我不爬了。"另一人劝说"别呀！休息一下再接着爬。"寓意坚持与放弃，在追求目标途中遇到困难时需要坚持不懈。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2019-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the pictures below. In your essay, you should: 1) describe the pictures briefly, 2) interpret the implied meaning, and 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2020-task2.png',
    image_description = '习惯。左图：一名女生伏案工作，气泡说"尽早完成才放心"；右图：一名男生懒散地躺着，气泡说"不到最后不动手"。对比两种截然不同的工作/学习习惯，寓意及时行动的重要性。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2020-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the picture below. In your essay, you should: 1) describe the picture briefly, 2) interpret the implied meaning, and 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2021-task2.png',
    image_description = '（学戏与兴趣）。小女孩对父亲说："爸爸，很多同学觉得学唱戏不好玩。"父亲回答："你自己不是喜欢吗？那就足够了。"寓意不随波逐流，应坚持自己的兴趣和热爱。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2021-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the picture below. In your essay, you should: 1) describe the picture briefly, 2) interpret the implied meaning, and 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2022-task2.png',
    image_description = '校园讲座。两名学生站在校园讲座海报前，一人说"不是我们专业的，听了也没多大用。"另一人说"去听肯定有好处。"寓意跨学科学习与开放心态的重要性。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2022-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay based on the picture below. In your essay, you should: 1) describe the picture briefly, 2) interpret the implied meaning, and 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2023-task2.png',
    image_description = '（龙舟赛）。河边龙舟竞渡，岸上观众人山人海，两位老人感叹说："真好啊，咱们村的龙舟赛越来越热闹了！"寓意传统文化的传承与复兴，民俗活动焕发生机。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2023-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay based on the picture and the chart below. In your essay, you should: 1) describe the picture and the chart briefly, 2) interpret the implied meaning, and 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2024-task2.png',
    image_description = '市民健身区 + 某市近三年公园数量（单位：座）。左图：市民在新建的小公园里跑步健身，人物说"家门口新建的小公园，真不错！"；右图：柱状图显示某市公园数量逐年增加：2020年406座、2021年532座、2022年670座。寓意城市绿色基础设施建设的发展成果惠及市民。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2024-postgrad-en1' AND task = 'task2';

UPDATE essay_prompt
SET
    prompt_text = 'Write an essay of 160-200 words based on the following table. In your essay, you should: 1) describe the table briefly, 2) explain its intended meaning, and 3) give your comments.',
    image_url = '/uploads/past-prompts/postgrad/en1/2025-task2.png',
    image_description = '近年来全国居民平均每百户年末主要耐用消费品拥有量。表格数据：2014/2017/2020/2023年，全国居民每百户空调（75.2→96.1→117.7→145.9台）、洗衣机（83.7→91.7→96.7→98.2台）、电冰箱（85.5→95.3→101.8→103.4台）拥有量逐年增长，反映居民生活水平持续提升。',
    source = '考研英语作文真题合集（2005-2025）',
    is_active = 1
WHERE stage_id = 4 AND paper = '2025-postgrad-en1' AND task = 'task2';
