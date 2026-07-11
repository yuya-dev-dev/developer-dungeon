# Developer Dungeon アーキテクチャ

## 文書情報

- 状態: Review-ready（井上P1解消済み、ユーザー確認待ち）
- 対象: Git編の1日縦切り版および安定版MVP
- 上位文書: [`requirements.md`](requirements.md)、[`git-mvp-stages.md`](git-mvp-stages.md)、[`threat-model.md`](threat-model.md)
- 関連文書: [`vertical-slice.md`](vertical-slice.md)、[`test-strategy.md`](test-strategy.md)

## 1. この文書が決めること

この文書は、Git編を実現するruntime component、Maven module、package責務、Git Runner境界、状態採点、永続化、Dockerとの境界を定める。

この文書は将来のJava、SQL、Docker学習編の共通基盤を設計しない。

## 2. 設計原則

1. Spring BootアプリケーションとGit実行を別processにする。
2. player入力をraw shell commandとして境界の外へ渡さない。
3. 1 attemptを1 disposable containerと1 ephemeral workspaceへ対応させる。
4. 採点をcommand履歴ではなくrepository snapshotに対する純粋なJavaロジックにする。
5. 1日縦切り版でも安定版と同じ隔離境界を通す。
6. stage定義を任意scriptやpluginにしない。
7. Git専用の境界だけを作り、汎用Runnerへ抽象化しない。
8. ローカル・シングルプレイヤーに不要な認証、分散処理、event基盤を導入しない。

## 3. runtime構成

```text
Browser
  |
  v
Spring Boot app ----------------> Management PostgreSQL
  |
  | GitCommand DTO / attempt API
  v
Git Runner controller ----------> Docker Engine / Docker Desktop
                                      |
                                      v
                              Disposable challenge container
                                      |
                                      v
                              Ephemeral /workspace
```

### 3.1 1日縦切り版

- Browser
- Spring Boot app
- Git Runner controller
- challenge container
- in-memory attempt/history

PostgreSQL、Flyway、Docker Composeによる管理DBは使用しない。

### 3.2 安定版MVP

- 1日版のcomponent一式
- management PostgreSQL
- Flyway migration
- Docker Composeによるローカル管理DB起動
- progress/history persistence
- startup時と定期処理のorphan cleanup

## 4. Maven module

実装開始時は次の3 Maven moduleと、独立したDocker build contextを基本とする。

```text
developer-dungeon/
  pom.xml
  runner-contract/
  app/
  git-runner/
  challenge-image/
```

| 種別 | 名前 | 責務 |
|---|---|---|
| Maven module | `runner-contract` | Git専用のrequest/response DTOとerror code。SpringやDockerへ依存しない |
| Maven module | `app` | Web、application use case、domain、stage定義、採点、persistence、Runner client |
| Maven module | `git-runner` | command再検証、Docker lifecycle、Git process、snapshot取得、cleanup |
| Docker build context | `challenge-image` | 固定Git executable、空hooks directory、fixture、非root実行環境 |

`runner-contract`には汎用`Runner<T>`、SQL、Docker教材用DTOを置かない。

## 5. Spring Boot appのpackage責務

```text
com.developerdungeon.git
  web
  application
  domain
  stage
  runner
  persistence
  config
```

| package | 責務 |
|---|---|
| `web` | Controller、form validation、Thymeleaf view model、error表示 |
| `application` | stage開始、command実行、hint、判定、reset、progress更新 |
| `domain` | Attempt、GitCommand、RepositorySnapshot、ClearResult、StarRating |
| `stage` | 5つの固定StageDefinition、command policy、clear policy、hint |
| `runner` | Runner client、contract変換、timeout/error mapping |
| `persistence` | stage_attempt、command_historyのrepository |
| `config` | loopback、Runner接続、profile、DB設定 |

ControllerからRunner clientやpersistenceへ直接処理を流さず、application use caseを経由する。

## 6. Git Runnerのpackage責務

```text
com.developerdungeon.gitrunner
  api
  validation
  sandbox
  git
  snapshot
  cleanup
  config
```

| package | 責務 |
|---|---|
| `api` | loopback endpoint、Runner token検証、contract DTO |
| `validation` | stage別command/argumentの再検証 |
| `sandbox` | fixed Docker argv、container作成・停止・削除 |
| `git` | challenge container内の固定Git executable実行、出力制限 |
| `snapshot` | 採点用のrefs、trees、status、途中状態の取得 |
| `cleanup` | TTLとlabelに基づくorphan回収 |
| `config` | fixed image、limit、name prefix、Docker executable path |

Runnerは管理DBへ接続せず、ゲーム上のclear条件を判断しない。

## 7. Runner API

APIはloopback限定とし、起動時に共有したtokenでappを認証する。playerへ直接公開しない。

### 7.1 operation

| operation | request | response |
|---|---|---|
| `createWorkspace` | `attemptId`、`requestId`、`stageKey`、`generation` | `workspaceId`、initial snapshot |
| `executeCommand` | `attemptId`、`requestId`、`workspaceId`、`generation`、typed `GitCommand` | exit code、truncated stdout/stderr、duration、new snapshot |
| `readFile` | `attemptId`、`requestId`、`workspaceId`、`generation`、stage固定file key | content、version token |
| `writeFile` | `attemptId`、`requestId`、`workspaceId`、`generation`、stage固定file key、content、version token | write result、new version token |
| `getSnapshot` | `attemptId`、`requestId`、`workspaceId`、`generation` | repository snapshot |
| `replaceWorkspace` | `attemptId`、`requestId`、旧`workspaceId`、次`generation` | 新`workspaceId`、initial snapshot |
| `destroyWorkspace` | `attemptId`、`requestId`、`workspaceId`、`generation`、reason | cleanup result |

`readFile`と`writeFile`は`STAGE-GIT-04`だけで有効にする。requestからhost path、container ID、image、mount、network、Docker optionを受け取らない。

### 7.2 GitCommand

`GitCommand`は少なくとも次を持つ。

- `stageKey`
- `commandKind`
- validation済みoptionの列
- appで完全object IDへ正規化済みのrefの列
- validation済みpath keyの列

Browserから受け取ったraw textはappでparseし、contractには含めない。Runnerは観察commandでobject IDを原則12桁表示に固定し、appはそのattemptで表示済みの12桁IDまたは完全IDだけをfixture内許可objectの完全IDへ正規化する。Runnerは完全IDが現在workspace内に存在しstage policyの許可objectと一致することを再検証してからargvを構築する。未表示・短すぎる・曖昧prefix、未知object、revision式は拒否する。

### 7.3 attemptの直列化とidempotency

- appはattemptごとのlockまたはsingle-thread queueを持ち、command、snapshot、reset、destroyを直列化する。
- attemptは`ACTIVE`、`EXECUTING`、`RESETTING`、`CLEARED`、`FAILED`、`EXPIRED`、`ABANDONED`の状態機械に従う。
- Browserの変更requestごとに一意な`requestId`を発行し、同じIDの再送へ同じ結果を返す。
- workspaceを再生成するたびに単調増加する`generation`を付け、古いworkspaceへのrequestを拒否する。
- Runnerもattempt／workspaceごとに直列化し、存続中は`requestId`と結果を保持して二重実行を防ぐ。
- Runner再起動などで実行結果を確定できない場合はcommandを自動再実行せず、workspaceを破棄して同じ論理attemptのsystem recoveryとして扱う。
- workspace交換では論理attempt ID、`highest_hint_level`、`player_reset_count`、`system_recovery_count`、command sequenceを保持し、workspace IDとgenerationだけを交換する。
- プレイヤーがリセットbuttonを押した場合だけ`player_reset_count`を増やし、timeout、Runner再起動、応答不明では`system_recovery_count`を増やす。

## 8. command実行フロー

1. Browserがraw commandと1回限りのform nonceをappへPOSTする。
2. `web`がlength、改行、control characterを検証する。
3. `stage`のcommand parserが表示済み12桁object IDを許可済み完全IDへ正規化し、typed `GitCommand`へ変換する。
4. `application`がattempt lockを取得し、state、request ID、workspace generationを確認する。
5. Runner clientがtyped contractを送る。
6. Runnerがstage policyで再検証する。
7. Runnerがshellを使わず、固定Docker CLI argvから`docker exec`相当を実行する。
8. challenge container内で固定絶対pathのGitをargv配列で実行する。
9. Runnerが時間と出力を制限し、採点用snapshotを別に取得する。
10. appがrequest IDに対応するcommand historyを確定し、stage clear policyを評価する。
11. Browserへplayer向け結果だけを返す。

入力拒否は手順6以前で終了し、Git processを起動しない。

## 9. challenge container

### 9.1 image

- versionを固定したLinux baseとGitを使用し、実装時にdigestをpinする。
- `/usr/bin/git`などの固定pathを使用する。
- 非root UID/GIDを作成する。
- `/opt/fixtures`をread-onlyにする。
- `/opt/empty-hooks`をread-onlyの空directoryにする。
- shellをplayer入力の実行経路に使わない。
- fixture検査をimage build時に行う。
- repository-local configをkeyと期待valueの組で検査し、`.gitattributes`、`.git/info/attributes`、`.gitmodules`を拒否する。

### 9.2 container設定

- root filesystem: read-only
- `/workspace`: `rw,nosuid,nodev,noexec,size=64m,mode=0700`のtmpfs
- `/tmp`: `rw,nosuid,nodev,noexec,size=16m,mode=0700`のtmpfs。固定HOMEをこの配下に置く
- network: none
- user: non-root固定UID
- capabilities: all drop
- `no-new-privileges`: enabled
- seccomp: Docker default profileを使用し、`seccomp=unconfined`を禁止
- privileged、device、host PID/IPC/network: none
- host bind mount、Docker socket: none
- CPU、memory、PID、output、timeout: [`threat-model.md`](threat-model.md)の初期値

### 9.3 fixture初期化

Runnerがstage keyを固定fixture IDへmappingし、player入力をpathへ使用せず、read-only fixtureを新しい`/workspace`へ複製する。author、committer、timestampを固定したfixture manifestからexpected objectを検証する。workspace生成時にもlocal configのkeyと期待value、hook、symlink、`.gitattributes`、`.git/info/attributes`、`.gitmodules`、外部URLを再検査し、不一致ならGitを実行せず失敗させる。許可するlocal configは`core.repositoryformatversion=0`、`core.filemode=true`、`core.bare=false`、`core.logallrefupdates=true`を基本とし、追加は個別承認する。Runner所有のcommand-scope設定で`core.attributesFile=/dev/null`を含め、hooks、protocol、fsmonitor、external diff、textconv、credential、署名処理、system attributesを安全側へ固定する。

## 10. RepositorySnapshotと採点

### 10.1 snapshot

`RepositorySnapshot`は必要最小限の正規化情報を持つ。

- current branchまたはdetached HEAD
- HEAD object ID
- HEADのparent countと順序付き直接parent object ID
- 許可されたrefsとobject ID
- stageで必要なcommit ancestor relation
- stageで必要なtree ID
- porcelain相当のindex／working tree状態
- stash refs
- merge、rebase、cherry-pick、revert途中状態
- 指定ファイルのhashとconflict marker有無
- stage指定fileのexpected working tree diff／index hash

player向け`git log`出力をparseして採点しない。Runnerが固定された信頼済みGit commandでsnapshotを取得する。

### 10.2 clear policy

appの`stage` packageに、5つのstage別clear policyを置く。任意script、expression language、外部pluginは使用しない。

clear policyは`RepositorySnapshot`を受け取り、次を返す純粋なJava処理とする。

- clear / not clear
- 満たした条件
- 満たしていない条件のplayer向け抽象表示
- internal reason code

expected object IDとtree IDはfixture manifestからstage定義へ読み込む。Browser入力から変更できない。

## 11. stage定義

stageの文章、hint、許可command kind、fixture ID、clear policy IDをappの固定resourceまたはJava定義で管理する。

MVPでは次を行わない。

- DBから任意stage scriptを読み込む
- 管理画面からstageを追加する
- playerがfixtureをuploadする
- stage定義内でJavaScript、SpEL、shellを実行する
- 将来mode用の`challenge_type`を持たせる

## 12. Web層

### 12.1 画面

- ステージ一覧
- プレイ画面

1日縦切り版ではプレイ画面だけを直接表示してよい。

### 12.2 実装方式

- Spring MVC
- Thymeleaf
- 通常form POST
- server-side validation
- PRG patternを必要箇所で使用
- sessionにはplayer識別ではなく、現在attemptの最小情報だけを保持

ログイン、SPA、WebSocketは使用しない。appとRunnerはloopbackへbindし、BrowserからのPOSTにはCSRF protectionを適用する。

Git出力、commit message、diff、player入力、限定editor内容、errorはすべてuntrusted dataとして扱う。Thymeleafではplain textとしてescapeし、raw HTML描画を使用しない。ANSI escapeと表示不要なcontrol characterを除去または可視化し、少なくとも`default-src 'self'`、`script-src 'self'`、`object-src 'none'`、`base-uri 'none'`、`frame-ancestors 'none'`を含むCSPを適用する。

## 13. 永続化

### 13.1 1日縦切り版

- stage定義: 固定resource
- attempt: memory
- command history: memory
- progress: 保存しない

### 13.2 安定版MVP

Spring JDBCとFlywayを使い、JPAはMVPでは導入しない。

#### `stage_attempt`

| column | 用途 |
|---|---|
| `id` | UUID primary key |
| `stage_key` | `STAGE-GIT-01`など |
| `status` | ACTIVE / EXECUTING / RESETTING / CLEARED / FAILED / EXPIRED / ABANDONED |
| `version` | optimistic lock用の単調増加値 |
| `current_generation` | 現在workspaceの世代。resetごとに増加 |
| `started_at` | 開始時刻 |
| `completed_at` | 終了時刻、nullable |
| `highest_hint_level` | 0〜4 |
| `player_reset_count` | プレイヤーが明示したreset回数。スター判定に使用 |
| `system_recovery_count` | timeout、Runner再起動、応答不明などによるworkspace再生成回数。スター判定に使用しない |
| `stars` | clear後の1〜3、nullable |

#### `command_history`

| column | 用途 |
|---|---|
| `id` | generated primary key |
| `attempt_id` | stage_attempt foreign key |
| `request_id` | UUID。再送防止の一意key |
| `sequence_no` | attempt内の順序 |
| `workspace_generation` | 実行対象workspace世代 |
| `entered_text` | redaction・length制限済み入力 |
| `command_kind` | 構造化した種類、拒否時はnullable |
| `result_kind` | PENDING / REJECTED / GIT_ERROR / SUCCEEDED / RESET / TIMEOUT / RUNNER_ERROR |
| `exit_code` | 実行時のみ、nullable |
| `duration_ms` | 実行時間 |
| `executed_at` | 実行時刻 |

`(attempt_id, sequence_no)`と`request_id`を一意にする。変更requestでは、Git実行前に`PENDING`行を作成し、同じrequest IDの再送を再実行せず既存結果へ対応付ける。

resetは`stage_attempt`の終端statusにしない。同じ論理attemptを`RESETTING`へ遷移させ、reasonに応じて`player_reset_count`または`system_recovery_count`と`current_generation`を増やし、reset eventを`command_history`へ記録して`ACTIVE`へ戻す。`highest_hint_level`とsequenceを保持する。明示的な「新しい挑戦」だけが新しい`stage_attempt`を作る。

`stage`、`player`、`player_progress` tableは作らない。ステージは固定resource、進捗と最高スターはCLEARED attemptから導出する。

### 13.3 startup recovery

- Git Runner起動時は、前processのidempotency記録を失っているため、固定labelを持つ既存challenge containerを新規commandへ再利用せず、安全に停止・削除する。
- app起動時は`EXECUTING`、`RESETTING`、`PENDING`を1 transactionずつ照合する。
- `PENDING` commandは自動再実行せず`RUNNER_ERROR`へ終端化し、対応するattemptをsystem recoveryへ遷移させる。
- system recoveryでは`system_recovery_count`と`current_generation`を増やし、新しいworkspaceを作成して`ACTIVE`へ戻す。再生成できない場合だけ`FAILED`にする。
- `CLEARED`、`FAILED`、`EXPIRED`、`ABANDONED`は再開しない。
- recovery処理自体もrequest ID、optimistic lock、generationで多重実行を防ぐ。
- app停止、Runner停止、response確定直前停止をそれぞれintegration testで再現する。

## 14. error model

| error category | appの扱い |
|---|---|
| INPUT_REJECTED | Git未実行。理由を安全な表現で表示 |
| GIT_ERROR | exit codeとtruncated outputを表示し、attempt継続 |
| TIMEOUT | containerを破棄し、同じ論理attemptのsystem recoveryとしてcountとgenerationを増やす。再生成失敗時だけFAILEDにする |
| OUTPUT_LIMIT | 出力を打ち切り、command結果に明示。必要ならattempt継続 |
| RUNNER_UNAVAILABLE | player操作を止め、resetまたは再起動案内 |
| CLEANUP_FAILED | attemptを継続せず、local logとUIに通知 |
| PERSISTENCE_ERROR | clear確定前なら再判定可能にし、重複記録を防ぐ |

stack trace、host path、credentialをBrowserへ返さない。

## 15. local起動

### 1日縦切り版

- appとgit-runnerを別JVM processで起動する。
- Docker DesktopのLinux container modeを利用する。
- PostgreSQLは起動しない。

### 安定版MVP

- PostgreSQLをDocker Composeで起動する。
- appとgit-runnerはIDEまたはMavenから別processとして起動してよい。
- Git challenge containerのlifecycleはComposeへ固定せず、git-runnerがattemptごとに管理する。
- Web appにDocker socketをmountしない。

## 16. テスト境界

- domain、command parser、clear policyはDockerなしのunit testとする。
- WebはMockMvcまたは同等のMVC testとする。
- persistenceはPostgreSQL Testcontainersを使う。
- challenge image contractとGit fixtureはDockerを伴うintegration testとする。
- Runnerのresource limit、network、mount、cleanupは実container設定で確認する。
- Dockerを伴うテストはユーザーの明示許可を得て実行する。

詳細は[`test-strategy.md`](test-strategy.md)を正本とする。

## 17. 1日版から安定版への移行

1日版で作った次の境界は維持する。

- raw command parser
- typed GitCommand contract
- app / Runner process分離
- disposable challenge container
- RepositorySnapshot
- STAGE-GIT-01 clear policy

安定版で追加する。

- PostgreSQL、Flyway、Spring JDBC
- stage_attempt、command_history
- ステージ一覧
- STAGE-GIT-02〜05
- 限定エディタ
- orphan cleanupの定期処理
- Testcontainers統合テスト

LocalGitRunnerやhost process実行から移行する設計は採用しない。

## 18. 明示的に採用しない構成

- Spring Boot processから`ProcessBuilder`でplayer由来Gitを直接実行する
- Spring Boot containerへDocker socketをmountする
- challenge container内でDockerを起動する
- Kubernetes、microservice群、message broker
- mode共通の汎用Runner
- JPAによる不要なentity model
- stage script engine、plugin system
- 管理DBと課題環境のnetwork共有

## 19. 実装前に固定する事項

- Java、Spring Boot、Git、PostgreSQL、Docker imageのversion
- challenge imageのdigestとupdate手順
- Runner tokenの生成・受渡方法
- Windows上でのapp／Runner起動command
- Docker Desktop以外のsupport範囲

これらは1日縦切り版の実装計画を井上が事前レビューする前に確定する。
