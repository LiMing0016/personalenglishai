# Ability + Context Prompt 妤犲本鏁瑰銉╊€冮敍鍫熸拱閸﹀府绱?

閺堫剚鏋冨锝呮値楠炴湹琚遍柈銊ュ瀻閸愬懎顔愰敍?- Ability Prompt閿涘牐鍏橀崝娑氭暰閸嶅繑鏁為崗銉礆妤犲本鏁?
- Context v1.2閿涘牅绗傛稉瀣瀮濞夈劌鍙?閹稿洣鍞拠鍡楀焼/濞撳懏绀傞敍澶愮崣閺€?
## 0) 瀵偓閸氼垶鐛欓弨鑸垫）韫囨绱欐妯款吇閸忔娊妫撮敍?
閸?`backend/.env` 閹存牞绻嶇悰宀€骞嗘晶鍐ц厬鐠佸墽鐤嗛敍?
```properties
AI_PROMPT_DEBUG=true
```

鐠囧瓨妲戦敍?- 閸忔娊妫撮弮璁圭礄姒涙顓?`false`閿涘绗夋导姘崇翻閸?`[ABILITY_PROFILE]` / `[ABILITY_PROMPT]` / `[PROMPT_BUILD]`
- 瀵偓閸氼垰鎮楅悽銊ょ艾妤犲矁鐦?Ability 娑?Context 闁炬崘鐭鹃弰顖氭儊閻㈢喐鏅?

## 1) Ability 妤犲本鏁归敍鍧瞫er_ability_profile閿?
### 1.1 閹绘帒鍙嗗ù瀣槸閼宠棄濮忛悽璇插剼閿涘澆ser_id=9999閿涘苯娲撶痪褝绱濈拠顓熺《瀵亶銆嶉敍?
```sql
INSERT INTO user_ability_profile (
  user_id, stage,
  task_score, coherence_score, grammar_score, vocabulary_score, structure_score, variety_score,
  assessed_score, sample_count, model_version, rubric_version
) VALUES (
  9999, 2,
  68.00, 62.00, 35.00, 64.00, 66.00, 61.00,
  59.00, 5, 'v1', 'v1'
)
ON DUPLICATE KEY UPDATE
  stage = VALUES(stage),
  task_score = VALUES(task_score),
  coherence_score = VALUES(coherence_score),
  grammar_score = VALUES(grammar_score),
  vocabulary_score = VALUES(vocabulary_score),
  structure_score = VALUES(structure_score),
  variety_score = VALUES(variety_score),
  assessed_score = VALUES(assessed_score),
  sample_count = VALUES(sample_count),
  model_version = VALUES(model_version),
  rubric_version = VALUES(rubric_version);
```

### 1.2 鐠嬪啩绔村▎?Chat 閹恒儱褰涢敍鍫濈箑妞ゆ槒顔€閸氬海顏憴锝嗙€介崚?userId=9999閿?
閸撳秵褰侀敍?- 娴ｈ法鏁ら懗鑺ユЁ鐏忓嫬鍩?`userId=9999` 閻?JWT閿涘牊甯归懡鎰剁礆
- 閹存牕婀張顒€婀村鈧崣鎴︽懠鐠侯垶鍣锋担璺ㄦ暏閸ュ搫鐣?mock 閻劍鍩涢敍鍫ｅ瑜版挸澧犵€圭偟骞囬弨顖涘瘮閿?
鐠囬攱鐪扮粈杞扮伐閿涘潉POST /api/ai/command`閿涘绱?

```json
{
  "apiVersion": 1,
  "intent": "chat",
  "instruction": "鐢喗鍨滃☉锕佸鏉╂瑥褰炵拠婵嗚嫙鐟欙綁鍣存稉杞扮矆娑斿牐绻栭弽閿嬫暭",
  "constraints": {
    "selectedText": "The chart show many people's choose restaurant habit."
  }
}
```

閺堢喐婀滈弮銉ョ箶閿涘牆绱戦崥?`AI_PROMPT_DEBUG=true`閿涘绱?

```text
[ABILITY_PROFILE] userId=9999 stage=2 sampleCount=5 loaded=true
[ABILITY_PROMPT] userId=9999 stage=閸ユ稓楠?gated=false weaknessTop=鐠囶厽纭?鏉╃偠鐤?
[PROMPT_BUILD] userId=9999 abilityInjected=true ... policy=CHAT_V1_0218
```

### 1.3 鐏?sample_count 閺€閫涜礋 0閿涘牐袝閸?Ability 闂勫秶楠囩粵鏍殣閿?
```sql
UPDATE user_ability_profile
SET sample_count = 0
WHERE user_id = 9999;
```

閸愬秵顐肩拫鍐暏閸氬本鐗遍惃?Chat 閹恒儱褰涢妴?
閺堢喐婀滈弮銉ョ箶閿?
```text
[ABILITY_PROFILE] userId=9999 stage=2 sampleCount=0 loaded=true
[ABILITY_PROMPT] userId=9999 stage=閸ユ稓楠?gated=true weaknessTop=
[PROMPT_BUILD] userId=9999 abilityInjected=true ... policy=CHAT_V1_0218
```

瑜版挸澧犻崶鍝勭暰缁涙牜鏆愰敍鍫濆嚒鐎圭偟骞囬敍澶涚窗
- `sample_count <= 0` 閺冩湹绮涘▔銊ュ弳 Ability閿涘潉abilityInjected=true`閿?- 鏉╂稑鍙嗛垾婊€鑵戦幀褔妾风痪褏澧楅垾婵撶窗娑撳秷绶崙鍝勬€ユい?閸忣厾娣拠锔藉剰閿涘奔绮庢潏鎾冲毉閺嶉攱婀版稉宥堝喕閹绘劗銇?+ 鐎涳附顔岄幐鍥ь嚤 + 閹貉冨煑鐟欏嫬鍨?

### 1.4 鐠嬪啯鏆ｅ閬嶃€嶇紒鏉戝楠炲爼鐛欑拠?weaknessTop 鐠虹喖娈㈤崣妯哄

```sql
UPDATE user_ability_profile
SET sample_count = 5,
    grammar_score = 30.00,
    coherence_score = 28.00,
    task_score = 70.00,
    vocabulary_score = 66.00,
    structure_score = 68.00,
    variety_score = 65.00
WHERE user_id = 9999;
```

閸愬秵顐肩拫鍐暏 Chat 閹恒儱褰涢敍灞炬埂閺堟稒妫╄箛妤€褰夐崠鏍﹁礋閿?
```text
[ABILITY_PROMPT] userId=9999 stage=閸ユ稓楠?gated=false weaknessTop=鏉╃偠鐤?鐠囶厽纭?
```

### 1.5 閺冪姾顔囪ぐ鏇炴簚閺咁垽绱欒箛鍛淬€忔稉宥嗗Г闁挎瑱绱?

```sql
DELETE FROM user_ability_profile WHERE user_id = 9999;
```

閸愬秵顐肩拫鍐暏 Chat 閹恒儱褰涢敍灞炬埂閺堟稒妫╄箛妤嬬窗

```text
[ABILITY_PROFILE] userId=9999 stage=null sampleCount=null loaded=false
[PROMPT_BUILD] userId=9999 abilityInjected=false ... policy=CHAT_V1_0218
```

楠炴湹绗栭幒銉ュ經濮濓絽鐖舵潻鏂挎礀閿涘奔绗夐幎銉╂晩閵?
---

## 2) Context v1.2 妤犲本鏁归敍鍫熷瘹娴狅綀鐦戦崚?+ 濞撳懏绀?+ 娑撳绱戦崗绛圭礆

### 2.1 瑜版挸澧犵€圭偟骞囩憰浣哄仯閿涘牅绌舵禍搴ㄧ崣閺€璺侯嚠閻撗嶇礆

瀹告彃鐤勯悳甯窗
- `ContextDecision` 娑撳绱戦崗绛圭窗
  - `injectSelectedContext`
  - `injectConversationContext`
  - `injectDraftContext`
- `ReferenceResolver` 閹稿洣鍞拠鍡楀焼閿涘牐顫夐崚娆戝閿涘绱?
  - `ABOVE`
  - `LAST_ASSISTANT`
  - `PARAGRAPH_N`
  - `SENTENCE_N`
  - `THIS_WORD`
- `recentMessages` 濞撳懏绀傞敍?  - 鏉╁洦鎶ゆ担搴濅繆閹垱绉烽幁顖ょ礄婵?`ok/thanks/婵傜晫娈?鐠嬨垼闃?閸?閺€璺哄煂`閿?  - 鏉╁洦鎶ゆ潻鐐电敾闁插秴顦插☉鍫熶紖閿涘牆鎮?role + 閸氬苯鍞寸€圭櫢绱?
- Context 閺夈儲绨导妯哄帥缁狙勫絹缁€鐚寸窗
  - `瑜版挸澧犳潪顔炬暏閹寸柉绶崗?> selectedText > recentMessages > draftText`
- Context/Task 閹垮秳缍旀潏鍦櫕閹绘劗銇氶敍鍧則arget=selectedText` 閺冭绱?

### 2.2 Context 閻╃鍙ч柊宥囩枂妞ょ櫢绱欐妯款吇閸婄》绱?

```properties
AI_PROMPT_CONTEXT_SELECTED_TEXT_MAX=1200
AI_PROMPT_CONTEXT_RECENT_EACH_MAX=200
AI_PROMPT_CONTEXT_RECENT_TURNS=8
AI_PROMPT_CONTEXT_DRAFT_MAX=1600
```

### 2.3 閺冦儱绻旂€涙顔岄敍鍧刐PROMPT_BUILD]`閿?
瀵偓閸?`AI_PROMPT_DEBUG=true` 閸氬函绱濋柌宥囧仯閻绻栨禍娑樼摟濞堢绱?
- `contextInjected`
- `injectSelectedContext`
- `injectConversationContext`
- `injectDraftContext`
- `contextLen`
- `taskIntent`
- `target`
- `referenceType`
- `referenceConfidence`
- `policy`

---

## 3) Context v1.2 妤犲本鏁归悽銊ょ伐閿涘湧ostman閿?
### 3.1 閻劋绶閿涙矮绗傞弬鍥ㄥ瘹娴狅綇绱欐惔鏃€鏁為崗?recentMessages閿?
鐠囬攱鐪伴敍?
```json
{
  "apiVersion": 1,
  "intent": "chat",
  "instruction": "缂堟槒鐦ф稉鈧稉瀣╃瑐闂堛垻娈戦崘鍛啇",
  "constraints": {
    "recentMessages": [
      {"role": "user", "content": "鐠囩柉袙闁插﹨绻栧▓浣冪樈"},
      {"role": "assistant", "content": "This paragraph mainly describes how consumer preferences changed over time."}
    ]
  }
}
```

閺堢喐婀滈弮銉ョ箶閿涘牏銇氭笟瀣剁礆閿?
```text
[PROMPT_BUILD] ... contextInjected=true injectConversationContext=true injectSelectedContext=false injectDraftContext=false target=lastAssistantAnswer referenceType=ABOVE referenceConfidence=0.70 ...
```

### 3.2 閻劋绶閿涙碍妫ら崗鎶芥６妫版﹫绱欐稉宥呯安瀵缚顢戝鏇犳暏娑撳﹣绗呴弬鍥风礆

鐠囬攱鐪伴敍?
```json
{
  "apiVersion": 1,
  "intent": "chat",
  "instruction": "閺傛澘鍕鹃惃鍕瘜妫版鐦?閼昏鲸鏋?,
  "constraints": {
    "recentMessages": [
      {"role": "assistant", "content": "This is a previous essay analysis."}
    ],
    "draftText": "The pie chart illustrates ..."
  }
}
```

閺堢喐婀滈弮銉ョ箶閿涘牏銇氭笟瀣剁礆閿?
```text
[PROMPT_BUILD] ... contextInjected=false injectConversationContext=false injectDraftContext=false ... referenceType=NONE referenceConfidence=0.00 ...
```

### 3.3 閻劋绶閿涙岸鈧灏弨鐟板晸閿涘牅绱崗?selectedText閿?
鐠囬攱鐪伴敍?
```json
{
  "apiVersion": 1,
  "intent": "chat",
  "instruction": "閺€鐟板晸鏉╂瑦顔岄敍灞炬纯鐎涳附婀?,
  "constraints": {
    "selectedText": "The chart show many people's choose restaurant habit.",
    "draftText": "The chart show many people's choose restaurant habit. It also ..."
  }
}
```

閺堢喐婀滈弮銉ョ箶閿涘牏銇氭笟瀣剁礆閿?
```text
[PROMPT_BUILD] ... contextInjected=true injectSelectedContext=true injectConversationContext=false injectDraftContext=false taskIntent=rewrite target=selectedText ...
```

楠炴湹绗?Prompt 娑擃叏绱欓弮鐘绘付閹垫挸宓冮崗銊︽瀮閿涘绨查崠鍛儓閹垮秳缍旀潏鍦櫕閹绘劗銇氶敍?- `閼?target=selectedText閿涘奔绮庢径鍕倞闁鑵戦弬鍥ㄦ拱閿涘奔绗夐幍鈺佸晸閸忋劍鏋冮妴淇?

### 3.4 閻劋绶閿涙ewrite 閺冪娀鈧灏敍鍫濈安濞夈劌鍙?draft閿?
鐠囬攱鐪伴敍?
```json
{
  "apiVersion": 1,
  "intent": "rewrite",
  "instruction": "鐢喗鍨滈弫缈犵秼閺€鐟板晸瀵版娲块懛顏嗗姧",
  "constraints": {
    "draftText": "The pie chart illustrates ..."
  }
}
```

閺堢喐婀滈弮銉ョ箶閿涘牏銇氭笟瀣剁礆閿?
```text
[PROMPT_BUILD] ... contextInjected=true injectSelectedContext=false injectConversationContext=false injectDraftContext=true taskIntent=rewrite target=fullDraft ...
```

### 3.5 閻劋绶閿涙俺袙闁插﹣绗傛稉鈧弶鈥虫礀婢跺稄绱欓崨鎴掕厬 LAST_ASSISTANT閿?
鐠囬攱鐪伴敍?
```json
{
  "apiVersion": 1,
  "intent": "chat",
  "instruction": "鐟欙綁鍣存稉濠佺閺夆€虫礀婢?,
  "constraints": {
    "recentMessages": [
      {"role": "assistant", "content": "Use a more formal connector such as 'Moreover'."},
      {"role": "assistant", "content": "Use a more formal connector such as 'Moreover'."},
      {"role": "user", "content": "婵傜晫娈?}
    ]
  }
}
```

閺堢喐婀滈弮銉ョ箶閿涘牏銇氭笟瀣剁礆閿?
```text
[PROMPT_BUILD] ... injectConversationContext=true target=lastAssistantAnswer referenceType=LAST_ASSISTANT ...
```

鐠囧瓨妲戦敍?- `婵傜晫娈慲 娴兼俺顫︾憴鍡曡礋娴ｅ簼淇婇幁顖涚Х閹垵绻冨?- 鏉╃偟鐢婚柌宥咁槻 assistant 濞戝牊浼呮导姘箵闁插稄绱欐禒鍛箽閻ｆ瑤绔撮弶鈽呯礆

---

## 鐠佹崘顓搁崢鐔峰灟閿涘牆缍嬮崜宥夋▉濞堢绱?

- AI Chat 閼奉亞鏁遍崶鐐电摕閿涙稐绗傛稉瀣瀮閼煎啫娲块悽鍗炲缁旑垱妯夊蹇庝繆閸欏嚖绱檆ontextScope/actionOrigin閿? 閸氬海顏崘宕囩摜閹貉冨煑閿涙硜ecentMessages 婢跺嫮鎮婂鍙夊复閸欙絽瀵查敍灞界秼閸撳秹绮拋?rule閿涘苯鎮楃紒顓炲讲閺囨寧宕?LangChain 鐎圭偟骞囬妴?
---

## 4) 閻楀牊婀扮拋鏉跨秿閿涘牆缂撶拋顔炬樊閹躲倧绱?

- Ability Prompt 妤犲本鏁归敍姘嚒楠炶泛鍙嗛張顒佹瀮娴?- Context v1.2閿涙艾鍑￠獮璺哄弳閺堫剚鏋冩禒璁圭礄鐟欏嫬鍨崠鏍ㄥ瘹娴狅綀鐦戦崚顐犫偓涔篹cent 濞撳懏绀傞妴浣规降濠ф劒绱崗鍫㈤獓閹绘劗銇氶妴浣规）韫囨鐡у▓纰夌礆
- Conversation Memory閿涘湧hase 2B閿涘绱?
  - `redis + context-sidecar`閿涘湒ocker閿涘鍑￠崥顖氬З
  - backend 瀹告彃褰查崚?`hybrid` 濡€崇础閹恒儱鍙?conversation memory閿涘湧ython sidecar + rule fallback閿?  - 瀹歌尙鈥樼拋?`[CTX_PROCESS] ... processor=python action=append ok=true`閿涘潏onversation memory append 閹垫捇鈧熬绱?
  - 瀹歌弓鎱ㄦ径?`append 422 body missing`閿涘湞ava -> sidecar 鐠嬪啰鏁ら弨閫涜礋 HTTP/1.1 + `application/json; charset=utf-8` + byte[] JSON body閿?- 闁炬崘鐭鹃崣顖濐潎濞村鈧嶇窗
  - 瀹歌尙鈥樼拋銈呭讲閻鍩?`[AI_TRACE] ai.prompt.debug = true ... component=PromptAssembler`

閸氬海鐢婚懟銉ヤ粵 `Context v1.5 / v2.0`閿涘苯缂撶拋顔炬埛缂侇厼婀張顒佹瀮娴犳儼鎷烽崝鐘电彿閼哄偊绱欓柆鍨帳閺傚洦銆傞崚鍡樻殠閿涘鈧?

## Rubric Dynamic Prompt Injection Acceptance (2026-02-28)

### Goal
Validate that evaluate prompt uses rubric dynamically from MySQL (not hardcoded), and free/exam dimension contracts are correct.

### 1) Initialize / Verify Rubric Data
```bash
mysql -u root -p personalenglishai < backend/src/main/resources/db/create_rubric_tables.sql
```

```sql
USE personalenglishai;

SELECT id, rubric_key, stage, is_active
FROM rubric_version
WHERE rubric_key='highschool-v1' AND stage='highschool' AND is_active=1;

SELECT mode, COUNT(*) dim_count
FROM rubric_dimension
WHERE rubric_version_id = (
  SELECT id FROM rubric_version
  WHERE rubric_key='highschool-v1' AND stage='highschool' AND is_active=1
  ORDER BY id DESC LIMIT 1
)
GROUP BY mode;

SELECT mode, dimension_key, COUNT(*) level_count
FROM rubric_level
WHERE rubric_version_id = (
  SELECT id FROM rubric_version
  WHERE rubric_key='highschool-v1' AND stage='highschool' AND is_active=1
  ORDER BY id DESC LIMIT 1
)
GROUP BY mode, dimension_key;
```

Expected:
- free dimension count = 5
- exam dimension count = 6
- each dimension has 5 levels (A-E)

### 2) Verify Active Rubric API
```bash
curl "http://localhost:8080/api/v1/rubric/active?stage=highschool&mode=free"
curl "http://localhost:8080/api/v1/rubric/active?stage=highschool&mode=exam"
```

Expected:
- free does NOT contain `task_achievement`
- exam DOES contain `task_achievement`

### 3) Evaluate Request (free)
```powershell
$body = @{
  essay = "Technology helps students learn English, but many students use it only for entertainment..."
  mode = "free"
  lang = "en"
  aiHint = "Please strictly follow rubric and provide strength/weakness/suggestion for each dimension"
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/writing/evaluate" `
  -ContentType "application/json" `
  -Body $body | ConvertTo-Json -Depth 20
```

Expected response fields:
- `mode`
- `grades` (5 dimensions in free)
- `analysis` (strength/weakness/suggestion per dimension)
- `priority_focus`

### 4) Update DB Criteria and Re-evaluate
```sql
USE personalenglishai;

UPDATE rubric_level
SET criteria='[TEST_MARKER_STRICT] Grammar A requires near-perfect grammar. If obvious errors exist, max grade is C.'
WHERE rubric_version_id = (
  SELECT id FROM rubric_version
  WHERE rubric_key='highschool-v1' AND stage='highschool' AND is_active=1
  ORDER BY id DESC LIMIT 1
)
AND mode='free'
AND dimension_key='grammar'
AND level='A';
```

Re-run step 3 with same essay.

Expected:
- grammar-related judgment/analysis changes observably
- confirms rubric criteria are read dynamically from DB

### 5) Evaluate Request (exam)
```powershell
$body = @{
  essay = "..."
  mode = "exam"
  taskPrompt = "Write an argumentative essay about online learning."
  lang = "en"
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/writing/evaluate" `
  -ContentType "application/json" `
  -Body $body | ConvertTo-Json -Depth 20
```

Expected:
- `grades` and `analysis` include `task_achievement`

### Final Pass Criteria
- free mode returns exactly 5 dimensions
- exam mode returns exactly 6 dimensions including task_achievement
- each dimension has `strength/weakness/suggestion`
- changing DB criteria affects evaluation output without code changes
## Evaluate Async Flow (Submit + Poll) (2026-02-28)

### Why
The old synchronous `POST /api/writing/evaluate` can exceed frontend 10s timeout and be canceled by browser.

### New API
1. Submit task
```http
POST /api/writing/evaluate/submit
Content-Type: application/json

{
  "essay": "...",
  "mode": "free",
  "lang": "en",
  "aiHint": "...",
  "taskPrompt": "..."
}
```

Response (202 Accepted):
```json
{
  "requestId": "eval-task-xxxxxxxxxxxxxxxx",
  "status": "processing",
  "message": "accepted"
}
```

2. Query task status
```http
GET /api/writing/evaluate/tasks/{requestId}
```

Response:
```json
{
  "requestId": "eval-task-xxxxxxxxxxxxxxxx",
  "status": "processing|succeeded|failed",
  "error": "optional",
  "submittedAt": 1700000000000,
  "completedAt": 1700000005000,
  "result": { "...WritingEvaluateResponse when succeeded..." }
}
```

### Frontend Behavior
- On submit:
  - call `POST /writing/evaluate/submit`
  - poll `GET /writing/evaluate/tasks/{requestId}` every 1200ms
  - max poll timeout: 120s
- Terminal states:
  - `succeeded`: render `result`
  - `failed`: show error toast
  - timeout: show "evaluate timeout"

### Acceptance
1. Network sequence is visible:
   - `POST /writing/evaluate/submit` (202)
   - multiple `GET /writing/evaluate/tasks/{requestId}`
2. No more single 10s canceled XHR for evaluate.
3. Final UI shows score panel when task status becomes `succeeded`.