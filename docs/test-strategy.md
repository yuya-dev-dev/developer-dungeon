# Developer Dungeon テスト戦略

## 文書情報

- 状態: Chapter 1の5ステージとPhase 5改善単位1〜7Dの対象限定テストは完了。Chapter 0の3研修もApp／Runner／Docker／PostgreSQLの対象限定テスト完了
- 上位文書: [`requirements.md`](requirements.md)、[`git-mvp-stages.md`](git-mvp-stages.md)、[`threat-model.md`](threat-model.md)、[`architecture.md`](architecture.md)
- 関連文書: [`vertical-slice.md`](vertical-slice.md)、[`phase-5-experience-improvement-plan.md`](phase-5-experience-improvement-plan.md)、[`../AGENTS.md`](../AGENTS.md)

## 1. この文書が決めること

この文書は、Git編の要件、Chapter 0の3研修、Chapter 1の5ステージ、Runner隔離、永続化、Web操作をどのテストで検証するかを定める。

テストは変更内容を確認できる最小範囲から実行し、設定を確認せずに一律のfull suite、coverage、performance測定を行わない。

## 2. 基本方針

1. command parserとclear policyをDockerなしで高速に検証する。
2. Gitの実挙動が必要な箇所だけintegration testを使う。
3. Docker制限はmockではなく実container設定で確認する。
4. 正解だけでなく、近似不正解、操作途中、悪意ある入力を検証する。
5. stageごとにfixtureの再現性を確認する。
6. 自動テストで確認できない物語理解、出力の読みやすさ、操作感は手動確認する。
7. Docker、DB、E2Eを伴う実行は、対象タスクについてユーザーの明示許可を得る。

## 3. テストレベル

| レベル | Docker | DB | 主な対象 |
|---|---:|---:|---|
| Unit | 不要 | 不要 | parser、validator、star、clear policy、application use case |
| Web slice | 不要 | 不要 | Controller、form、CSRF、error mapping、Thymeleaf view model |
| Persistence integration | 必要 | PostgreSQL | Flyway、repository、transaction、constraint |
| Git fixture integration | 必要 | 不要 | 実Git、fixture、snapshot、stage clear |
| Runner security integration | 必要 | 不要 | container設定、limit、network、mount、cleanup |
| End-to-end | 必要 | 安定版のみ | Browserからclear／resetまでの代表経路 |
| Manual | 条件による | 条件による | 物語、操作感、出力、responsive表示 |

## 4. Unit test

### 4.1 command parser

| ID | 観点 |
|---|---|
| TEST-CMD-001 | stageで許可された最小commandをparseできる |
| TEST-CMD-002 | 未許可commandとoptionを拒否する |
| TEST-CMD-003 | Git global optionを拒否する |
| TEST-CMD-004 | 改行、制御文字、shell metacharacterを拒否する |
| TEST-CMD-005 | 許可されていないref、object ID、pathを拒否する |
| TEST-CMD-006 | whitespace規則が一意で、曖昧なtokenizationをしない |
| TEST-CMD-007 | Browser入力からtyped GitCommand以外をRunner contractへ渡さない |
| TEST-CMD-008 | attemptで表示済みの12桁object IDを完全IDへ正規化する |
| TEST-CMD-009 | 未表示、短すぎる、曖昧prefix、未知object、revision式を拒否する |

### 4.2 clear policy

各stageで最低限次を用意する。

- 正しいsnapshot
- 内容は正しいが履歴条件が違うsnapshot
- branchが違うsnapshot
- dirty snapshot
- Git操作途中snapshot
- expected objectまたはtreeが違うsnapshot

stage固有観点：

| ID | 観点 |
|---|---|
| TEST-STAGE-001 | STAGE-GIT-01でC2が祖先に残り、treeがC1と一致する |
| TEST-STAGE-002 | STAGE-GIT-02で2つのbranch tipとtarget treeが一致する |
| TEST-STAGE-003 | STAGE-GIT-03で意図したdirty状態、index、stash空を判定する |
| TEST-STAGE-004 | STAGE-GIT-04でHEADのparent countと順序付き直接parent、期待file、cleanを判定する |
| TEST-STAGE-005 | STAGE-GIT-05で現在branchと復旧branch tipが元のC1、mainがC0、HEAD treeがC1 tree、local branch集合が期待どおり、cleanかつ途中状態なしであることを判定する |

### 4.3 star rating

- clearでなければ0スター
- hint level 3・4使用時は最大1スター
- hint level 0〜2かつplayer resetありは2スター
- hint level 0〜2かつplayer resetなしは3スター
- system recoveryだけでは3スター上限を下げない
- 過去の最高スターを下げない

### 4.4 application use case

- attempt開始とRunner workspace作成の対応
- input rejected時にRunnerを呼ばない
- input rejected時にworkspace ID、generation、repository状態を変えない
- Git error時にGitが残した状態のまま同じworkspace IDとgenerationでattemptを継続する
- timeout時に旧workspaceをcleanupし、同じ論理attemptのsystem recoveryとしてgenerationを進める。再生成不能の場合だけFAILEDとする
- reset時に旧workspaceを破棄して新規作成する
- reset／system recoveryで旧workspaceのcleanupが失敗した場合は新workspaceを作成せず、cleanup pendingを維持する
- reset後も同じ論理attempt、最高hint level、player reset count、system recovery count、command sequenceを保持する
- attemptごとにcommand、snapshot、reset、destroyを直列化する
- 同じrequest IDの二重POSTでGit操作を1回だけ実行する
- 古いworkspace generationのrequestを拒否する
- Runner応答喪失時にGit commandを自動再実行しない
- clearとprogress保存のtransaction境界
- 同じcommand responseの二重記録を防ぐ

## 5. Web test

- loopbackを前提としたController route
- command formのlength、empty、改行validation
- CSRFなしPOSTの拒否
- Host／Origin方針
- error categoryごとのplayer向け表示
- stack trace、host path、credentialをviewへ渡さない
- Git出力、commit message、diff、editor内容、error、固定会話、clear scene内のHTML、script、event属性をplain textとしてescapeする
- ANSI escapeとcontrol characterを実行・解釈せず、安全に除去または可視化する
- CSP headerが必須directiveを持ち、会話用静的JavaScriptの追加後も外部script、`'unsafe-inline'`、`'unsafe-eval'`を許可しない
- hintの段階開示
- reset確認
- clear後のstarと振り返り表示
- clear responseの最初のviewportに成功見出しがあり、command formが描画されない
- 導入会話を進めてもskipしても同じstage、attempt、技術目標へ到達する
- 会話skip後も中央headerに発生事象、困っている関係者、守る条件、対応が必要な理由が残る
- JavaScript無効時も会話、技術目標、command form、clear結果を利用できる
- PC幅ではオフィスと中央PC、スマートフォン相当幅ではPC優先と下部会話パネルになる
- Stage 1・2でstage固有のclear scene、安全な理由、危険な代替案、成長beatが表示され、別stageの文言が混在しない
- クリア後の自己確認が非採点・非永続で、POST、DB更新、Runner呼び出しを発生させない
- active／clearの学習選択カード、独立した障害ticket、重複導入文、概念chip、main領域下部hint cardが描画されない
- active画面に`stage-header`、`stage-sidebar-state`、`stage-repository`、`stage-workspace`、`stage-clear-dialogue`が各1件あり、rootの固定stage keyと対応する
- `GET /commands`がDB、attempt、Runnerを呼ばず、番号、command、用途の3列を学習順に表示する
- command参照catalogにStage固有object ID、branch名、file名、具体的な解答順序が含まれない
- Stage 4の`mergeInProgress=true`かつ未clear時だけ限定editor formを描画し、競合前、Stage 4以外、clear後には描画しない
- hint buttonが`aria-expanded`と`aria-controls`を持ち、sidebar内hint regionだけを段階開示する
- command、hint、reset、editor formの通常POST actionに、JavaScript無効時の復帰先fragmentがある
- clear responseでは全固定regionを保ったままcommand formとeditor formがなく、成功見出しが存在する
- `incidentBoardMode`に応じてrepository状態を表示し、Stage 5のredacted情報を維持する
- workspaceに概念chipや正確な構文を常時表示せず、ヒントレベル3以降だけで正確な構文を開示する
- 全5ステージの入力拒否応答が正確な許可構文一覧を代わりに開示しない
- `GET /`が固定Git編cardだけを表示し、DB、attempt、Runnerを呼ばない
- `GET /git/stages`が固定3研修と固定5Stageを章別・学習順に表示し、番号、題名、完了状態を含む一方、最高スターを表示しない
- 入口画面のGit編card、戻る導線、各Stage行が通常linkでkeyboard操作でき、既存の固定Stage URLへ遷移する
- 既存Stage URLへの直接GETが入口2画面化後も成立する
- STAGE-GIT-04以外でeditor endpointを拒否

Thymeleafの見た目そのものはunit testへ寄せすぎず、重要な要素と条件分岐だけを確認する。

## 6. Git fixture integration test

各fixtureを新規workspaceへ展開し、固定Git versionで次を確認する。

1. manifestのexpected objectと実repositoryが一致する。
2. 初期current branch、refs、tree、index、working treeが仕様と一致する。
3. 許可された代表的な正解手順でclearになる。
4. 近似不正解手順でclearにならない。
5. reset後に同じobject IDと状態を再現できる。
6. fixtureにhook、symlink、submodule、外部URL、秘密値がない。
7. repository-local configが許可keyと期待valueの組だけである。
8. `.gitattributes`、`.git/info/attributes`、`.gitmodules`、external diff、filter、custom merge driver、fsmonitor、credential helper、signing programを含むfixtureを拒否し、system attributesを無効化する。
9. STAGE-GIT-05は、通常履歴を先に確認する経路とreflogを先に確認する経路に加え、branch作成後のswitchと`switch -c`の両方で同じclear snapshotへ到達する。
10. TRAINING-GIT-01〜03は、fixture初期状態、固定変更command、代表的な観察順序の違い、最終snapshot、reset後再現性を確認する。
11. TRAINING-GIT-02は生成reportがworkspaceに存在したままignoreされ、HEADとindexに含まれないことを確認する。
12. TRAINING-GIT-03は`main` tip不変、固定branchの直接親、期待tree、current branch、clean状態を確認する。

改善単位7DではSTAGE-GIT-01〜05の各Stageに2経路を用意し、各経路のDocker integration testを1本ずつ維持する。採点testはcommand履歴や順序ではなく最終snapshotの不変条件を確認する。

Chapter 0のApp unit testでは、固定raw構文と専用`CommandKind`の対応、近似構文と別Stage commandの拒否、研修別clear／近似不正解、hint・resetに依存しない内部stars=`1`、Chapter別progress／routeを確認する。Runner unit testでは固定argvとStage別allowlistを確認し、DB integration testでは新stage key、新旧operation kind、progress永続化と既存attempt lifecycleを確認する。

STAGE-GIT-05の状態変更を伴う最小正解経路は、`reflog`で表示された`C1`へ固定名branchを作成してswitchする経路と、固定名branchを`switch -c`で作成・移動する経路とする。観察順序は採点しない。`git show C1`は任意の内容確認であり、command history上のclear必須操作にしない。任意ref、revision式、reflog selector、別branch名を許可する別経路は増やさない。最低限、次を対象限定で確認する。

- 初期状態でlocal branchが`main`だけ、`main=C0`、`C1`は`log --all`に現れず、HEAD reflogの最大8件内に一意な12桁IDで現れる。
- Runner側固定fixture定義の`C0`／`C1`／tree／reflog entryと実repositoryが一致し、initial snapshotがrefやstdoutではなく信頼済み`recoveryTargetId=C1`を返し、reset後もobject IDとreflog順序を再現する。
- `REFLOG_HEAD`の固定出力で表示された`C1`だけを完全IDへ正規化し、`show`と`CREATE_PAYMENT_RETRY_BRANCH`に使用できる。
- exit code 0かつ非truncatedの固定`log`／`reflog`出力だけが表示済みIDを増やし、error、truncated output、`show`本文、他commandのstdoutでは増えない。
- appは表示済み集合とhint 4を管理し、Runnerは表示履歴を持たず、40桁形式、commit type、fixed allowlist、branch作成target=C1を検証する責務分離を確認する。
- `rev-parse --short=12`が正確に12桁で完全IDのprefixと一致する正常fixtureを受理し、13桁化、C0/C1の先頭12桁衝突、malformed reflog行をworkspace生成時に拒否する。
- `git show C1`を実行する経路と省略する経路の両方で、最終snapshotが同じならclearする。show実行履歴を採点へ使わない。
- branch作成後に`main`へ留まる状態、誤object、同内容の別commit、main移動、dirty、余分なbranch、途中状態ではclearしない。
- 未表示ID、`HEAD@{1}`、`C1^`、`C1~1`、任意reflog option、別branch名、別switch先をGit実行前に拒否する。
- reflog、show、log出力のHTML、ANSI、制御文字をplain textとして扱い、件数、byte上限、timeoutを超えて表示しない。

## 7. Runner security integration test

Phase 2で追加する対象、実containerの検査方法、lifecycle異常系、Docker不要launcher contract testの具体的な実装前方針は[`phase-2-hardening-plan.md`](phase-2-hardening-plan.md)を正本とする。本文書のtest IDと受け入れ条件は変更せず、Phase 2ではその対応表に従って未確認の証拠を補う。

### 7.1 Launcher contract test（Docker不要）

PowerShell 7.6.3 LTS x64だけで実行する自己完結したcontract testを用意し、外部moduleを暗黙に要求しない。process起動、preflight、token生成、artifact parse、cleanupを関数境界へ分離し、fake child processとfixture出力で次を自動確認する。

| ID | 観点 |
|---|---|
| TEST-LAUNCH-001 | PowerShell edition／version／x64、Windows 11 x86_64の不一致をbuild・child起動前に拒否する |
| TEST-LAUNCH-002 | JDK vendor／full version／architecture、WSL最低version、Docker Desktop version／Linux modeの不一致を拒否する |
| TEST-LAUNCH-003 | Wrapper 3.3.4で生成した3ファイルのraw byte SHA-256が追跡manifestと一致し、1 byte改変、Maven 3.9.16 distribution URL／SHA-256の欠落・改変時に失敗する |
| TEST-LAUNCH-004 | tokenが32 byte由来のpaddingなしbase64url 43文字で、起動ごとに異なる |
| TEST-LAUNCH-005 | tokenがlauncherのprocess環境、child引数、設定／一時file、標準出力、標準error、例外messageへ現れず、app／Runnerの子process環境だけへ渡る |
| TEST-LAUNCH-006 | Runner／app readiness timeout、app port競合／Spring context失敗、Ctrl+C相当、launcher例外、通常終了で開始済みfake childを逆順に停止する |
| TEST-LAUNCH-007 | `challenge-image.id`の欠落、空、余分な行、tag、短縮ID、非小文字hexを拒否する |
| TEST-LAUNCH-008 | 認証なし／誤tokenのhealth・shutdownを拒否し、正しいtokenのshutdownだけが対象childを停止する |
| TEST-LAUNCH-009 | build inputのpath制約、ordinal順、raw byte hash、UTF-8 BOMなし／LF canonical manifestから同じfingerprintを再現する |
| TEST-LAUNCH-010 | Dockerfile、fixture、rootfs helper、base digestの変更でfingerprintが変わり、README変更では変わらない |
| TEST-LAUNCH-011 | build script単独実行でも誤PowerShell、Windows on Arm、古いWSL、誤Docker Desktop、Windows container modeをartifact更新前に拒否する |
| TEST-LAUNCH-012 | `.dockerignore`がeffective contextをallowlist化し、`challenge-image/extra-file`などfingerprint対象外fileがあればbuild scriptもbuild前に拒否する |
| TEST-LAUNCH-013 | 異なる`core.autocrlf`設定のcloneでも`.gitattributes`によりWrapper 3ファイルとhash manifestのbyte列・検証結果が一致する |

token漏えいtestは、実tokenをfailure messageへ展開せず、出力やartifactに既知markerが存在しないことだけを報告する。

### 7.2 Runner／challenge container security integration test（Docker必要）

| ID | 観点 |
|---|---|
| TEST-SEC-001 | containerが非rootである |
| TEST-SEC-002 | root filesystemがread-onlyである |
| TEST-SEC-003 | capabilitiesがdropされ、no-new-privilegesが有効である |
| TEST-SEC-004 | networkがnoneで、外部接続できない |
| TEST-SEC-005 | Docker socket、host bind mount、device、host namespaceがない |
| TEST-SEC-006 | CPU、memory、PID、workspace limitが設定される |
| TEST-SEC-007 | Runnerがcommand timeoutを検知した時に、実containerが停止・削除され、workspaceを再利用できない |
| TEST-SEC-008 | outputが64 KiBで打ち切られる |
| TEST-SEC-009 | hooks、system/global config、credential promptを利用できない |
| TEST-SEC-010 | external protocolと未許可URLを利用できない |
| TEST-SEC-011 | 限定editorが`.git`、`..`、absolute path、symlinkを拒否する |
| TEST-SEC-012 | startup／periodic orphan cleanupが所有台帳とidentityの一致するorphanだけを削除し、active workspace、台帳外、別project、identity不一致のcontainerを削除しない |
| TEST-SEC-013 | Runner APIがtokenなし、誤token、未知operationを拒否する |
| TEST-SEC-014 | Docker default seccomp profileが有効で`unconfined`ではない |
| TEST-SEC-015 | `/workspace`と`/tmp`だけが必要なwritable tmpfsで、size、nosuid、nodev、noexecを持つ |
| TEST-SEC-016 | malicious local config／attributesをworkspace生成前に拒否する |
| TEST-SEC-017 | 同一attemptの並行requestと再送を直列化・idempotent処理する |
| TEST-SEC-018 | image ID artifactが未知・削除済み、現在のbuild-input fingerprintとlabelが異なるstale image、別platform、誤label、誤Git versionの場合にcontainer内Git起動前に拒否する |
| TEST-SEC-019 | container作成後の実image IDが指定IDと一致し、tag fallbackがない |
| TEST-SEC-020 | Runner token、app設定、credentialがDocker CLI processとchallenge containerの環境に存在しない |
| TEST-SEC-021 | graceful shutdown開始後に新規requestを拒否して所有containerを削除し、強制終了時は所有台帳に残ったcontainerを次回Runner起動時に回収する |
| TEST-SEC-022 | build scriptだけがcontract成功後にimage ID artifactを原子的更新し、失敗時は既存artifactを変更しない |
| TEST-SEC-023 | launcherとRunnerが同じ期待fingerprintを検証し、container作成後もimage IDとfingerprint labelが一致する |

security testは、単にDocker create commandの文字列を確認するだけでなく、実際のcontainer inspectと到達性で検証する。7.2はDockerを伴うため、ユーザーの明示許可を得て中谷が対象限定で実行する。

## 8. Persistence integration test

PostgreSQL Testcontainersを使用し、次を確認する。

- 空DBへFlyway migrationを適用できる
- `stage_attempt`と`command_history`のconstraint
- `(attempt_id, sequence_no)`の一意性
- `request_id`の一意性とPENDINGから終端resultへの遷移
- optimistic lockとworkspace generationの更新競合
- player reset countとsystem recovery countの分離
- app／Runner再起動後のEXECUTING、RESETTING、PENDING recovery
- response確定直前のprocess停止後にcommandを二重実行しないこと
- clear時のattemptとhistoryのtransaction
- reset、failed、expired status
- 最高スターと進捗の導出
- app roleに不要なDB権限がない
- migrationを再適用しても不整合が起きない

管理DB integration testからchallenge containerを起動しない。関心を分離する。

## 9. End-to-end test

安定版MVP完成時に、代表経路だけを対象とする。

- STAGE-GIT-01の診断からclear
- STAGE-GIT-04のconflict発生、限定編集、merge完了
- 禁止入力の拒否
- timeout後のreset
- app再起動後のprogress表示

全stage、全hint、全errorをBrowser E2Eで重複検証しない。詳細はunit／integration testで確認する。

## 10. 手動確認

### 1日縦切り版

[`vertical-slice.md`](vertical-slice.md)の手動確認手順を使用する。

### 安定版MVP

1. Chapter 0の3研修を完了し、Chapter 1の5stageを1スター以上でclearする。
2. Chapter 0の完了状態と、Chapter 1でhint level 1〜4を開示した際のstar反映を確認する。
3. 各研修と各stageをresetする。
4. 入力拒否、Git error、timeout、Runner unavailableを確認する。
5. app再起動後にprogressを確認する。
6. STAGE-GIT-04で指定外fileを編集できないことを確認する。
7. 主要画面をPC幅とスマートフォン相当幅で確認する。
8. 物語をskipしても技術条件が理解できることを確認する。
9. Git outputと演出が混同されないことを確認する。
10. 各stageのクリア後に、復旧完了の根拠を考えてから固定解説を開けることを確認する。
11. Stage 3でrepository状態を表示しない場合も、workspaceと段階hintから進行できることを確認する。
12. Stage 1・2・4のrepository状態が既存の`BASIC`相当、Stage 3が`OFF`相当で、main領域に概念chipや正確な構文を常時表示しないことを確認する。
13. Stage 5のrepository状態が`REDACTED_BRANCHES`相当で、branch消失だけを伝えてreflog entry、object ID、正解構文を先に漏らさず、hint level 3・4で段階開示されることを確認する。
14. 入力拒否と通常Gitエラーの後に、正しかった過去操作とworkspaceが維持され、明示reset時だけ初期状態へ戻ることを確認する。
15. clear直後にスクロールせず成功を認識でき、command入力がなく、人物の反応と自己確認へ進めることを確認する。
16. clear後の確認は最終snapshotの状態要約と自己確認で成立し、追加Gitコマンドを要求しないことを確認する。
17. Stage 2の目標文が`feature/profile`、`feature/notification`、最後のcheckout状態を区別していることを確認する。
18. Stage 1、2、5を外部支援なしで再確認し、常時案内だけで総当たりできないこと、ヒント3・4で行き止まりから回復できることを記録する。
19. command、hint、reset、Stage 4 editor保存後に全画面遷移せず、windowとmonitor内のscroll位置、browser zoom、入力文脈が維持されることを確認する。clear成立時だけ成功表示へ移動する。
20. 二重click中は再送できず、通信失敗または不正HTML responseでは自動retryも部分置換も行わず、再読込案内が出ることを確認する。
21. JavaScript無効時にcommand、hint、reset、Stage 4 editor保存が通常form POSTで成立し、responseが対応するfragment付近を表示することを確認する。
22. `/commands`の表をkeyboardと狭い画面で利用でき、表の閲覧だけでattemptやworkspaceが作られないことを確認する。
23. PC幅でHEAD完全IDが折り返されず、狭幅ではrepository領域内だけを横scrollできることを確認する。
24. `/`が承認済みタイトル参照画像の明るさと中央Git編cardを再現し、`/git/stages`がホワイトボード型のChapter 0研修3行とChapter 1現場5行を表示することをPC幅で確認する。
25. 入口2画面をkeyboardだけで往復し、狭幅でも文字や操作対象が画像へ埋没せず再配置されることを確認する。
26. `/`と`/git/stages`の閲覧前後でattempt、workspace、challenge containerが増えないことを確認する。

## 11. 要件traceability

| 要件 | 主な検証 |
|---|---|
| REQ-PROD-001〜005 | Chapter 0の3研修とChapter 1の5stage manual play、MVP対象者による未知fixture検証、説明確認 |
| REQ-GAME-001 | Web test、全stage manual play |
| REQ-GAME-002〜004 | TEST-CMD-001〜009、Web XSS test、Git fixture integration |
| REQ-GAME-005〜006 | star unit test、reset use case test、Web test、manual |
| REQ-GAME-007 | TEST-STAGE-001〜005 |
| REQ-GAME-008 | 各stage resource検査、clear sceneと振り返りのmanual確認 |
| REQ-GAME-009 | Persistence integration |
| REQ-GAME-010 | application use case、Web test、manual |
| REQ-GAME-011 | TEST-SEC-011、STAGE-GIT-04 integration、manual |
| REQ-GAME-012 | TEST-CMD-008〜009、STAGE-GIT-01・02・05 integration |
| REQ-GAME-013 | application concurrency test、TEST-SEC-017、persistence一意制約test |
| REQ-GAME-014 | clear後の自己確認Web test、各stage manual play、DB／Runner非呼出しtest |
| REQ-GAME-015 | repository表示Web test、Stage 3手動確認、hint level test |
| REQ-GAME-016〜018 | responsive manual、会話skip Web test、clear response Web test、accessibility manual |
| REQ-GAME-019 | application use case、error後のworkspace／generation継続test、manual |
| REQ-GAME-020 | 全stage guidance Web test、hint level test、入力拒否表示test |
| REQ-GAME-021 | clear policy unit、STAGE-GIT-05複数経路fixture integration、manual |
| REQ-GAME-022 | Stage template Web test、PC／狭幅manual |
| REQ-GAME-023 | command catalog Controller／template test、manual |
| REQ-GAME-024 | stable region／tokenのtemplate Web test、通常form Web test、JavaScript有効／無効manual |
| REQ-GAME-025 | Stage 4 template／Controller test、Stage 4 manual |
| REQ-GAME-026 | template test、PC／狭幅manual |
| REQ-GAME-027〜030 | title／stage-list Controller・template test、DB／Runner非呼出しtest、PC／狭幅・keyboard manual |
| REQ-VS-001〜006 | vertical-slice unit／Docker integration／manual checklist、文書scope確認 |
| REQ-MVP-001 | Chapter 0の3研修とChapter 1の5stage fixture integration、manual |
| REQ-MVP-002 | Web route／view test、manual |
| REQ-MVP-003 / 006 | TEST-LAUNCH-001〜013、TEST-SEC-001〜023、process境界のmanual確認 |
| REQ-MVP-004 / 005 | PostgreSQL／Flyway persistence integration |
| REQ-MVP-007 | 本文書§15のcompletion audit |
| NFR-SEC-001〜006 | TEST-CMD-003〜009、TEST-LAUNCH-001〜013、TEST-SEC-001〜023 |
| NFR-LOCAL-001 | bind address、Host／Origin／CSRFのWeb integration test |
| NFR-DATA-001 | fixture／log／screenshot data inspection |
| NFR-TEST-001 | test inventoryと実行結果のcompletion audit |
| NFR-UX-001 | Web test、manual play review |
| NFR-UX-002 | keyboard-only manual、clear見出しfocus Web test、contrast manual、`prefers-reduced-motion` manual／CSS確認、JavaScript無効manual、PC幅・狭幅responsive manual |
| NFR-UX-003 | partial update manual、aria-live／focus Web test、clear遷移manual |
| NFR-WEB-001 | Web XSS／CSP／control-character test |
| NFR-CON-001 | concurrency、request ID、generation、optimistic lock test |

## 12. 実行担当

### メインエージェント

- 実装と必要なテストコードの作成
- 実装直後の差分、コンパイル、最小確認
- 井上の指摘修正
- 中谷へ渡す対象testの特定

メインと中谷は同じテストクラス、同じテストメソッド、または同一条件のコマンドを重複して実行しない。メインは実装直後のコンパイルと最小確認を担当し、中谷はメインが未実行の層・実行環境・異常系だけを対象限定で確認する。

### メインエージェント

* 変更に必要なテストを追加または更新し、実装直後の最小確認を実行する。
* 実行済みのテストと未確認範囲を中谷へ明示する。

### 井上

- CSS・HTMLだけの編集を除く非軽微な実装の事前方針レビュー
- 実装後の差分とテスト観点レビュー
- テスト実行とファイル編集は行わない

### 中谷

* メインが実行済みのテストを再実行せず、未確認の変更層だけを実行する。
* 実行コマンド、結果、確認できた範囲だけを報告する。

- 井上の実装後レビューとメインの修正後に対象限定testを実行
- ファイル編集、実装修正、Git操作は行わない
- 失敗時は最初の関連失敗と主要例外だけを報告
- メイン修正後の再実行は原則1回

### ユーザー

- Docker、DB、E2E、外部接続を伴うtestの実行可否を判断
- 手動操作と画面確認
- stage、commit、push、PR、merge

## 13. Maven command方針

実際の`pom.xml`とmodule名に基づき、変更範囲へ対応する正確なcommandを提示する。基本形式は次のとおりとする。

```powershell
.\scripts\invoke-maven.ps1 -pl app "-Dtest=対象TestClass" test
.\scripts\invoke-maven.ps1 -pl git-runner "-Dtest=対象TestClass" test
.\scripts\invoke-maven.ps1 test
```

`clean test`、`clean verify`、coverage、performance測定を一律に実行しない。

## 14. 失敗時

1. 実行commandを確認する。
2. 最初の関連failureを確認する。
3. 主要exceptionと最初の原因箇所を確認する。
4. 今回の変更が原因か判断する。
5. メインが最小修正する。
6. 中谷またはユーザーが対象testを原則1回再実行する。
7. 解決しない場合は無関係なtestへ広げず停止・報告する。

## 15. MVPテスト完了条件

- Unit、Web、Chapter 0の3研修とChapter 1の5stage fixture、Runner security、persistence integrationの必要項目が成功している。
- 代表E2Eと手動確認が完了している。
- 実行しなかったtestと未確認範囲が明記されている。
- security受け入れ条件をmockだけでなく実containerで確認している。
- failureを無視、skip、期待値の弱体化で解消していない。
