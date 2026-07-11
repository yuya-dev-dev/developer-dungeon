# Developer Dungeon テスト戦略

## 文書情報

- 状態: Review-ready（井上P1解消済み、ユーザー確認待ち）
- 上位文書: [`requirements.md`](requirements.md)、[`git-mvp-stages.md`](git-mvp-stages.md)、[`threat-model.md`](threat-model.md)、[`architecture.md`](architecture.md)
- 関連文書: [`vertical-slice.md`](vertical-slice.md)、[`../AGENTS.md`](../AGENTS.md)

## 1. この文書が決めること

この文書は、Git編の要件、5ステージ、Runner隔離、永続化、Web操作をどのテストで検証するかを定める。

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
| TEST-STAGE-005 | STAGE-GIT-05で復旧branchが元のC1を指すことを判定する |

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
- Git error時にattemptを継続する
- timeout時にattemptをFAILEDにしてdestroyを呼ぶ
- reset時に旧workspaceを破棄して新規作成する
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
- Git出力、commit message、diff、editor内容、error内のHTML、script、event属性をplain textとしてescapeする
- ANSI escapeとcontrol characterを実行・解釈せず、安全に除去または可視化する
- CSP headerが必須directiveを持つ
- hintの段階開示
- reset確認
- clear後のstarと振り返り表示
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

複数の正しい手順を許可するstageでは、少なくとも2経路を用意する。ただしMVPのcommand allowlistが実質的に1経路しか許可しない場合は、その理由をstage testへ記録する。

## 7. Runner security integration test

| ID | 観点 |
|---|---|
| TEST-SEC-001 | containerが非rootである |
| TEST-SEC-002 | root filesystemがread-onlyである |
| TEST-SEC-003 | capabilitiesがdropされ、no-new-privilegesが有効である |
| TEST-SEC-004 | networkがnoneで、外部接続できない |
| TEST-SEC-005 | Docker socket、host bind mount、device、host namespaceがない |
| TEST-SEC-006 | CPU、memory、PID、workspace limitが設定される |
| TEST-SEC-007 | timeoutでcontainerが停止・削除される |
| TEST-SEC-008 | outputが64 KiBで打ち切られる |
| TEST-SEC-009 | hooks、system/global config、credential promptを利用できない |
| TEST-SEC-010 | external protocolと未許可URLを利用できない |
| TEST-SEC-011 | 限定editorが`.git`、`..`、absolute path、symlinkを拒否する |
| TEST-SEC-012 | orphan cleanupが別projectのcontainerを削除しない |
| TEST-SEC-013 | Runner APIがtokenなし、誤token、未知operationを拒否する |
| TEST-SEC-014 | Docker default seccomp profileが有効で`unconfined`ではない |
| TEST-SEC-015 | `/workspace`と`/tmp`だけが必要なwritable tmpfsで、size、nosuid、nodev、noexecを持つ |
| TEST-SEC-016 | malicious local config／attributesをworkspace生成前に拒否する |
| TEST-SEC-017 | 同一attemptの並行requestと再送を直列化・idempotent処理する |

security testは、単にDocker create commandの文字列を確認するだけでなく、実際のcontainer inspectと到達性で検証する。

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

1. 5stageを1スター以上でclearする。
2. hint level 1〜4を開示し、starへ反映されることを確認する。
3. 各stageをresetする。
4. 入力拒否、Git error、timeout、Runner unavailableを確認する。
5. app再起動後にprogressを確認する。
6. STAGE-GIT-04で指定外fileを編集できないことを確認する。
7. 主要画面をPC幅とスマートフォン相当幅で確認する。
8. 物語をskipしても技術条件が理解できることを確認する。
9. Git outputと演出が混同されないことを確認する。

## 11. 要件traceability

| 要件 | 主な検証 |
|---|---|
| REQ-PROD-001〜005 | 5stage manual play、MVP対象者による未知fixture検証、説明確認 |
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
| REQ-VS-001〜006 | vertical-slice unit／Docker integration／manual checklist、文書scope確認 |
| REQ-MVP-001 | 5stage fixture integration、manual |
| REQ-MVP-002 | Web route／view test、manual |
| REQ-MVP-003 / 006 | TEST-SEC-001〜017、process境界のmanual確認 |
| REQ-MVP-004 / 005 | PostgreSQL／Flyway persistence integration |
| REQ-MVP-007 | 本文書§15のcompletion audit |
| NFR-SEC-001〜006 | TEST-CMD-003〜009、TEST-SEC-001〜017 |
| NFR-LOCAL-001 | bind address、Host／Origin／CSRFのWeb integration test |
| NFR-DATA-001 | fixture／log／screenshot data inspection |
| NFR-TEST-001 | test inventoryと実行結果のcompletion audit |
| NFR-UX-001 | Web test、manual play review |
| NFR-WEB-001 | Web XSS／CSP／control-character test |
| NFR-CON-001 | concurrency、request ID、generation、optimistic lock test |

## 12. 実行担当

### メインエージェント

- 実装と必要なテストコードの作成
- 実装直後の差分、コンパイル、最小確認
- 井上の指摘修正
- 中谷へ渡す対象testの特定

### 井上

- CSS・HTMLだけの編集を除く非軽微な実装の事前方針レビュー
- 実装後の差分とテスト観点レビュー
- テスト実行とファイル編集は行わない

### 中谷

- 井上の実装後レビューとメインの修正後に対象限定testを実行
- ファイル編集、実装修正、Git操作は行わない
- 失敗時は最初の関連失敗と主要例外だけを報告
- メイン修正後の再実行は原則1回

### ユーザー

- Docker、DB、E2E、外部接続を伴うtestの実行可否を判断
- 手動操作と画面確認
- stage、commit、push、PR、merge

## 13. Maven command方針

実装後、実際の`pom.xml`とmodule名を確認してから正確なcommandを提示する。想定形式は次のとおりだが、scaffold前の現在は実行しない。

```powershell
.\mvnw.cmd -pl app "-Dtest=対象TestClass" test
.\mvnw.cmd -pl git-runner "-Dtest=対象TestClass" test
.\mvnw.cmd test
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

- Unit、Web、5stage fixture、Runner security、persistence integrationの必要項目が成功している。
- 代表E2Eと手動確認が完了している。
- 実行しなかったtestと未確認範囲が明記されている。
- security受け入れ条件をmockだけでなく実containerで確認している。
- failureを無視、skip、期待値の弱体化で解消していない。
