# Developer Dungeon アーキテクチャ

## 文書情報

- 状態: 既存ベースラインとPhase 5改善単位1〜6は実装済み。改善単位7A・7B・7Cは実装前方針確定、未実装
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
| `cleanup` | TTL、ローカル所有台帳、container identityに基づくorphan回収 |
| `config` | fixed image、limit、name prefix、Docker executable path |

Runnerは管理DBへ接続せず、ゲーム上のclear条件を判断しない。

## 7. Runner API

APIは`127.0.0.1`限定とし、起動時に共有したtokenでappを認証する。playerへ直接公開しない。IPv6 wildcard、`0.0.0.0`、LAN addressへbindしない。

### 7.0 起動時認証

- repository内の専用PowerShell 7.6.3 LTS x64 launcherが、`.NET RandomNumberGenerator`から起動ごとに32 byteを生成し、paddingなしbase64urlの43 ASCII文字へcanonical encodeした256-bit tokenを作る。標準Base64の`+`、`/`、`=`や任意Unicodeをtokenへ許可しない。
- launcherは`System.Diagnostics.ProcessStartInfo`で固定されたapp／Runner jarと固定引数だけを別JVM processとして起動し、同名tokenを各子process専用の環境変数へ設定する。launcher自身のprocess環境、command line、設定ファイル、一時ファイルへtokenを書き出さない。
- appはRunner URLとtokenをmemoryだけに保持し、すべてのRunner requestで専用headerとして送信する。Browserへtokenを返さない。
- Runnerは専用headerを必須とし、UTF-8 byte列を一定時間比較する。token不在、不一致、重複headerはGitやDockerを起動せず拒否する。
- Runnerは`127.0.0.1:18081`、appは`127.0.0.1:8080`へbindし、port使用中は別portへ自動退避せず起動に失敗する。
- launcherはtoken付きhealth requestでRunner readinessを最大45秒確認してからappを起動する。この時間には最大20秒のstartup recoveryを含み、launcherは回収処理より先にRunnerを停止しない。token値、request header、process environmentを標準出力や例外へ記録しない。
- appにも同tokenを要求するlauncher専用readiness operationを設ける。launcherはapp readinessの正常応答後だけlocal runtimeを起動成功と扱い、port競合、Spring context初期化失敗、早期process終了、timeoutでは`finally` cleanupへ移る。
- appとRunnerは通常Web routeと分離したloopback限定の`internal shutdown` operationを持ち、同じtokenを必須とする。認証不要shutdown endpointは作らない。
- launcherはpreflight、Runner起動、readiness待機、app起動、待機の全体を`try/finally`で囲む。通常終了、Ctrl+C、readiness timeout、app起動失敗、launcher例外のすべてで、app、Runnerの逆順に認証済みshutdownを要求する。shutdown HTTPを8秒待ち、応答後のprocess終了を5秒待って、開始から最大13秒以内に終了しない該当process treeだけを終了する。
- launcherは継承した`JAVA_HOME`だけを信頼せず、`JAVA_HOME`、PATH、ユーザー／マシン設定、固定Temurin配置の候補を順に検証し、Eclipse Temurin 25.0.3+9 x64と完全一致するJDKだけを子processへ渡す。
- launcherはDB起動より前にrepository固有のOS排他lockを取得し、同じrepositoryですでに起動中ならDB、Runner、appへ触れずに拒否する。起動前から稼働していた管理DBは当該launcherの所有とせず、後続の起動失敗または終了時に停止しない。
- Runnerはshutdown開始をatomicな状態として記録し、開始後の新規workspace requestをDocker起動前に拒否する。所有containerを停止・削除してから終了する。
- 強制終了でcleanupできなかったcontainerは、次回Runner起動時にローカル所有台帳とcontainer identityの完全一致を再検証して回収する。固定project／owner labelだけを根拠に削除しない。

このtokenはBrowserや偶発的なlocal requestからRunnerを分離するためのものであり、同一Windowsユーザー権限で動く悪意あるprocessを防ぐ境界とはみなさない。開発端末上の同一ユーザーprocessは信頼境界内とする。

RunnerがDocker CLI processを起動するときは親環境をそのまま継承せず、実行に必要な環境変数だけをallowlistで構築する。Runner token、app設定、credentialをDocker CLIへ渡さず、challenge containerの環境にも設定しない。

### 7.1 operation

| operation | request | response |
|---|---|---|
| `createWorkspace` | `attemptId`、`requestId`、`stageKey`、`generation` | `workspaceId`、initial snapshot |
| `executeCommand` | `attemptId`、`requestId`、`workspaceId`、`generation`、typed `GitCommand` | exit code、truncated stdout/stderr、duration、new snapshot |
| `readFile` | `attemptId`、`requestId`、`workspaceId`、`generation`、stage固定file key | content、version token |
| `writeFile` | `attemptId`、`requestId`、`workspaceId`、`generation`、stage固定file key、content、version token | write result、new version token |
| `getSnapshot` | `attemptId`、`requestId`、`workspaceId`、`generation` | repository snapshot |
| `replaceWorkspace` | `attemptId`、`requestId`、旧`workspaceId`、次`generation` | 旧workspace削除成功後だけ、新`workspaceId`とinitial snapshot |
| `destroyWorkspace` | `attemptId`、`requestId`、`workspaceId`、`generation`、reason | cleanup result |

`readFile`と`writeFile`は`STAGE-GIT-04`だけで有効にする。requestからhost path、container ID、image、mount、network、Docker optionを受け取らない。

`health`と`shutdown`はlauncher専用のinternal operationとし、上表のworkspace operationとrouteを分離する。いずれも同じRunner token、loopback bind、Host検証を必須とし、player session、CSRF token、workspace IDでは呼び出せない。

### 7.2 GitCommand

`GitCommand`は少なくとも次を持つ。

- `stageKey`
- `commandKind`
- validation済みoptionの列
- appで完全object IDへ正規化済みのrefの列
- validation済みpath keyの列

Browserから受け取ったraw textはappでparseし、contractには含めない。Runnerは観察commandでobject IDを原則12桁表示に固定し、appはそのattemptで表示済みの12桁IDまたは完全IDだけをfixture内許可objectの完全IDへ正規化する。Runnerは完全IDが現在workspace内に存在しstage policyの許可objectと一致することを再検証してからargvを構築する。未表示・短すぎる・曖昧prefix、未知object、revision式は拒否する。

STAGE-GIT-05では、標準入力構文`git reflog`、`git branch feature/payment-retry <object>`、`git switch feature/payment-retry`を、それぞれstage専用の`REFLOG_HEAD`、`CREATE_PAYMENT_RETRY_BRANCH`、`SWITCH_PAYMENT_RETRY`へ変換する。`REFLOG_HEAD`と`SWITCH_PAYMENT_RETRY`は引数を持たず、`CREATE_PAYMENT_RETRY_BRANCH`はappが表示済みIDから正規化した完全`C1`だけを持つ。branch名、reflog selector、revision式、optionをBrowser入力からcontractへ渡さない。

Runnerは`REFLOG_HEAD`を固定argv `git reflog show --format=%h%x09%gs --abbrev=12 --max-count=8 HEAD`、branch作成を固定名`feature/payment-retry`と完全`C1`、switchを固定名`feature/payment-retry`へ変換する。`show`は既存の安全な固定optionを使用する。appは許可済みの`log`または`reflog`がexit code 0かつ非truncatedで返した、`^[0-9a-f]{12}\t`または既存logの固定形式に一致する先頭12桁IDだけをattempt内の表示済み集合へ記録し、hint level 4で明示した`C1`も同じ集合へ追加する。error、truncated output、`show`本文、他commandのstdoutからIDを登録しない。appはこの表示済み集合を使って12／40桁入力を信頼済み完全IDへ正規化する。

Runnerは表示済み集合を保持しない。Runnerの責務は、appから受け取ったobject IDの40桁形式、workspace内でのcommit objectの存在とtype、Runner側の固定fixture allowlistとの一致、`CREATE_PAYMENT_RETRY_BRANCH`では固定`C1`との一致を再検証することである。表示provenanceのidempotencyとreset時破棄は既存どおりappのattempt stateが担当する。

### 7.3 attemptの直列化とidempotency

- appはattemptごとのlockまたはsingle-thread queueを持ち、command、snapshot、reset、destroyを直列化する。
- attemptは`STARTING`、`ACTIVE`、`EXECUTING`、`CLEARING`、`RESETTING`、`CLEANUP_PENDING`、`CLEARED`、`FAILED`、`EXPIRED`、`ABANDONED`の状態機械に従う。
- Browserの変更requestごとに一意な`requestId`を発行し、同じIDの再送へ同じ結果を返す。
- workspaceを再生成するたびに単調増加する`generation`を付け、古いworkspaceへのrequestを拒否する。
- Runnerもattempt／workspaceごとに直列化し、存続中は`requestId`と結果を保持して二重実行を防ぐ。
- Runner再起動などで実行結果を確定できない場合はcommandを自動再実行せず、workspaceを破棄して同じ論理attemptのsystem recoveryとして扱う。
- workspace交換では論理attempt ID、`highest_hint_level`、`player_reset_count`、`system_recovery_count`、command sequenceを保持し、workspace IDとgenerationだけを交換する。
- プレイヤーがリセットbuttonを押した場合だけ`player_reset_count`を増やし、timeout、Runner再起動、応答不明では`system_recovery_count`を増やす。
- workspace交換では旧containerの削除成功前にgenerationを進めず、新containerも作らない。cleanup失敗時は`CLEANUP_PENDING`へ遷移し、旧workspaceのexecute／snapshotを拒否して同じcleanup requestだけを再試行可能にする。

### 7.4 container所有台帳

- launcherは`.developer-dungeon/runtime/runner-owned-containers.json`の固定絶対pathをRunnerの子process環境だけへ渡す。requestやBrowser入力からpathを受け取らない。
- Runnerは台帳読込とstartup cleanupより前に、同directoryの`runner-owned-containers.lock`をWindows `FileStream`の`FileShare.None`相当で取得し、process終了まで保持する。取得失敗時はDocker操作とreadiness開始前に起動失敗する。
- 台帳は秘密値を含まず、作成状態、作成時刻、確認回数、nullableなcontainer ID、attempt ID、workspace ID、generation、完全image ID、build fingerprintだけを持つ。
- RunnerはDocker create前にattempt／workspace／generation／image／fingerprintを作成intentとしてtemporary file、flush、同一filesystem上のatomic replaceで保存する。intent保存失敗時はcontainerを作成しない。
- Docker create後、workspace公開前にcontainer IDをentryへ原子的に追記する。追記失敗時はworkspaceを公開せず、intentを残して対象containerの削除を試みる。
- container削除成功後だけ対応entryをcleanup request ID付き`DELETED` tombstoneへ同じ方式で置換する。削除失敗時はentryを残す。
- 起動時cleanupは台帳entryとcontainer inspectのproject、owner、attempt、workspace、image、fingerprintが完全一致する場合だけ削除する。container ID未確定のintentはworkspace labelで候補を限定し、完全一致する候補が1件の場合だけ削除する。
- ID未確定intentの候補が0件でもentryを削除せず、各scanのDocker期限5秒、2秒間隔、最大3回、startup recovery全体20秒以内で再scanする。解決しない0件、複数件、台帳破損、台帳外、不一致では台帳を保持してRunnerをdegradedにし、readinessとDockerを伴うworkspace operationを拒否する。
- 定期cleanupはin-memory active containerを除外し、TTL超過または`CLEANUP_PENDING`のentryだけを対象にする。
- 台帳はcontainerの安全な所有確認とcrash recoveryだけに使い、player progress、command history、採点を永続化しない。
- container削除成功時はentryをcleanup request ID付き`DELETED` tombstoneへ原子的に置換する。認証済みinternal requestでattempt ID、workspace ID、generationが一致する削除再確認にはDockerを呼ばず成功を返し、appが次generationのworkspace作成に成功した後だけ旧tombstoneを削除する。
- internal shutdownはshutdown状態へ遷移してから同期的に所有containerをcleanupする。ローカルMVPのactive container上限を1件、cleanup全体を6秒以内、`docker rm -f`を5秒以内とし、成功時だけ204を返す。失敗時は5xxを返して台帳entryを保持し、launcherは正常終了として表示しない。
- launcherはRunner readinessを45秒待つ。shutdown HTTP timeoutを8秒、応答後のprocess終了待機を5秒とし、shutdown開始から最大13秒を超えた場合だけ該当process treeを強制停止する。

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
11. clear成立時は`CLEARING`へ遷移し、workspaceの破棄成功後に`CLEARED`とスターを確定する。
12. Browserへplayer向け結果だけを返し、クリア後は非採点・非永続の自己確認と固定振り返りを表示する。

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

現在のMVPでいうfixture manifestは独立した実体fileではなく、challenge image内の固定fixtureと、Runner側にcompileされたstage別の期待object ID／tree ID／ref集合を指す。Runnerは検証済みimage IDとbuild-input fingerprintを前提に固定値をworkspace生成時に照合する。appは認証済みRunnerのinitial snapshotからstage targetを取得し、同じ期待値fileを重複保持しない。

## 10. RepositorySnapshotと採点

### 10.1 snapshot

`RepositorySnapshot`は必要最小限の正規化情報を持つ。

- current branchまたはdetached HEAD
- HEAD object ID
- HEADのparent countと順序付き直接parent object ID
- stageで固定するbranch tip（STAGE-GIT-02では`feature/profile`と`feature/notification`）
- 許可されたrefsとobject ID
- stageで必要なcommit ancestor relation
- stageで必要なtree ID
- porcelain相当のindex／working tree状態
- stash refs
- merge、rebase、cherry-pick、revert途中状態
- 指定ファイルのhashとconflict marker有無
- stage指定fileのexpected working tree diff／index hash
- STAGE-GIT-05専用`StageFiveState`の`mainTip`、`recoveryTargetId`、`recoveryTargetParent`、`recoveryTargetTreeId`、nullableな`paymentRetryTip`、`localBranches`

player向け`git log`出力をparseして採点しない。Runnerが固定された信頼済みGit commandでsnapshotを取得する。

### 10.2 clear policy

appの`stage` packageに、5つのstage別clear policyを置く。任意script、expression language、外部pluginは使用しない。

clear policyは`RepositorySnapshot`を受け取り、次を返す純粋なJava処理とする。

- clear / not clear
- 満たした条件
- 満たしていない条件のplayer向け抽象表示
- internal reason code

expected object IDとtree IDはRunner側の固定fixture定義で検証し、認証済みinitial snapshotからappのattempt内StageTargetsへ取り込む。appの固定stage定義やBrowser入力から期待IDを変更できない。

### 10.3 STAGE-GIT-05のfixtureと採点境界

Runner側の固定Stage 5 fixture定義は`C0`、失われた`C1`、両tree、初期ref集合、HEAD reflog内の期待`C1` entryを完全IDで持つ。新しいmanifest fileは導入しない。workspace生成時に、現在branchが`main`、local branchが`main`だけ、`main=C0`、working treeとindexがclean、`C1^1=C0`、`C1`がどのrefからも到達不能だがcommit objectとして存在し、固定したHEAD reflog表示から観察可能であることをRunnerが検証する。fixture作成に伴うbranch作成、switch、削除を含む全reflog更新のcommitter時刻とtimezoneを固定し、resetで同じ順序と表示を再現する。

workspace生成時はさらに、内部固定commandで`C0`と`C1`の`rev-parse --short=12`を取得し、結果がそれぞれちょうど12桁、完全IDの先頭12桁と一致、相互に不一致であることを検証する。13桁以上への拡張、prefix衝突、期待reflog行の`^[0-9a-f]{12}\t`不一致ではworkspaceを公開しない。

initial snapshotの`StageFiveState.recoveryTargetId`が、refから到達不能な`C1`の信頼済み完全IDをappへ供給する唯一の経路になる。appのStageTargetsは`mainTip=C0`と`recoveryTargetId=C1`を保持し、hint level 4、表示済み12／40桁正規化、clear判定に同じC1を使用する。Runnerも固定fixture定義の同じC1をallowlistとbranch作成targetに使用する。reflog stdout、`ancestorObjectIds`、Browser入力からC1の期待値を導出しない。`paymentRetryTip`は初期状態ではnull、branch作成後はC1とし、どちらの状態でもsnapshotを取得できる。

clear policyはreflog stdoutやcommand historyを採点へ使わない。信頼済みsnapshotから、現在branchが`feature/payment-retry`、HEADと同branch tipが元の`C1`、`C1^1=C0`、HEAD treeが期待`C1` tree、`main=C0`、local branch集合が`main`と`feature/payment-retry`だけ、working treeとindexがclean、Git操作途中状態がないことをすべて確認する。branch作成後も`main`にいる状態、`main`を動かした状態、同内容の別commit、誤objectを指すbranchはclearしない。

## 11. stage定義

stageの文章、hint、許可command kind、fixture ID、clear policy IDをappの固定resourceまたはJava定義で管理する。

表示上の案内量と読み取り専用状態要約は、stageの技術定義から分離したdeveloper管理の固定表示方針で扱う。初期MVPでは少なくとも次の2軸を独立させる。

- `guidanceMode`: `FULL_SYNTAX` / `CONCEPT_ONLY`
- `incidentBoardMode`: `OFF` / `BASIC` / `REDACTED_BRANCHES`

表示方針はcommand allowlist、fixture、clear policy、Runner contractへ影響させず、player入力、DB、外部設定から変更できない。Controllerへ渡す案内用view modelと状態要約用view modelを分け、Thymeleafでも別sectionとして条件表示する。汎用feature flag、plugin、動的stage作成基盤は導入しない。

MVPでは次を行わない。

- DBから任意stage scriptを読み込む
- 管理画面からstageを追加する
- playerがfixtureをuploadする
- stage定義内でJavaScript、SpEL、shellを実行する
- 将来mode用の`challenge_type`を持たせる

## 12. Web層

### 12.1 画面

- タイトル兼編選択画面
- Git編ステージ選択画面
- プレイ画面

1日縦切り版ではプレイ画面だけを直接表示してよい。

改善単位7C以降は、`GET /`をタイトル兼編選択画面、`GET /git/stages`をGit編ステージ選択画面とする。`GET /`は固定のGit編catalogだけをserver-side renderし、DB、attempt、Runner、workspaceを参照しない。`GET /git/stages`は固定のSTAGE-GIT-01〜05とclear状態だけをDB read-onlyで表示し、最高スターをview modelへ載せない。既存の固定URLである`GET /stages/{固定stage key}`とstage別POST URLは変更せず、formから任意のstage keyを受け取らない。一覧閲覧ではRunner、workspace、attemptを作らず、未対応stage keyはrouteを定義せず404とする。

入口画面は既存のSpring MVCとThymeleafで実装する。`title.html`は固定Git編card、`stages.html`は既存進捗queryを利用した5つの固定Stage行を描画し、画面ごとに専用のstatic CSSを持つ。参照画像は設計資料に限定し、本番画面では背景assetとsemantic HTMLを分離する。画像内の文字、button、Stage行をclick mapや透明overlayで代用しない。外部font、SPA framework、新しい本番依存関係は追加しない。

入口画面のrouteと責務は次のとおりとする。

| route | view | server依存 | 操作 |
|---|---|---|---|
| `GET /` | タイトル兼編選択 | 固定presentation catalogのみ | Git編を選び`/git/stages`へ移動 |
| `GET /git/stages` | Git編ステージ選択 | clear進捗のDB read-only参照 | 固定Stage URLへ移動、`/`へ戻る |
| `GET /stages/STAGE-GIT-01`〜`05` | プレイ画面 | 既存Stage lifecycle | attempt開始または再開 |

将来編の追加を見越しても、MVPではedition table、汎用edition controller、`challenge_type`、plugin、共通Runner interfaceを導入しない。新しい編を承認した時点で、固定cardとrouteを追加するか、実装済みの複数編から共通化を判断する。既存sidebarの「ステージ一覧」は`/git/stages`へ向け、タイトル画面へ戻る導線はGit編ステージ選択画面にだけ置く。既存Stage URLへのbookmarkと直接アクセスは維持する。

改善単位7Aでは読み取り専用の`GET /commands`を追加する。このrouteはpresentation用の固定catalogだけをserver-side renderし、DB、attempt、Runner、workspaceを参照しない。catalogは番号、command、用途を学習順に持つが、Runner allowlist、parser、security policyの正本にはせず、それらから動的生成もしない。Stage固有object ID、branch名、file名、正解操作順は登録しない。

安定版MVPはrepository snapshotによる自動クリアを維持し、プレイヤーの復旧報告を待つ`report` routeは追加しない。クリア後の自己確認は、固定の問いと解説を表示するだけの非採点・非永続UIとし、POST、DB、Runner、attempt状態を追加しない。

内部パイロット後の改善では全5ステージを`CONCEPT_ONLY`へ統一する。Stage 1・2・4の状態要約は既存の`BASIC`、Stage 3は`OFF`、Stage 5は`REDACTED_BRANCHES`を初期値とする。状態要約は案内量と独立させ、reflog entry、object ID、正解構文を先に漏らさない。これはview modelとtemplateの表示変更であり、command allowlist、parser、Runner contract、fixture、clear policyを変更しない。

プレイ画面は固定resourceから会話をserver-side renderし、同梱した同一originの静的JavaScriptが会話の進行とskipをclient内で制御する。改善単位7Aではactive画面を、4項目のsidebar、障害説明と目標を統合したheader、現在repository状態、workspaceへ整理する。独立した障害ticket、重複導入文、active/clear学習カード、概念chip、main領域下部のhint cardは描画しない。hintはsidebar button直下へ展開し、workspaceの通常表示はcommand入力、実行button、実行結果とする。resetは補助操作として残し、Stage 4限定editorは`stageKey=STAGE-GIT-04`、`mergeInProgress=true`、`cleared=false`をすべて満たす場合だけworkspace内へ追加する。

Stage 4限定editorの条件は表示制御だけに依存させない。Controllerは信頼済みsnapshotがmerge中の場合だけeditor内容を取得し、Serviceはread／writeの双方で同じmerge中条件を確認する。競合前、clear後、他Stageからの直接requestはRunnerのreadFile／writeFileを呼ばず、既存の入力拒否表示へ戻す。

会話状態はDB、attempt、Runnerへ保存しない。inline script、inline event handler、任意HTML、`eval`を使わず、JavaScript無効時は全会話、統合headerの技術条件、通常formを利用できる。clear済みresponseではcommand formとeditorを描画せず、成功見出し、最終状態、自己確認、固定解説、振り返りを最初のviewportへ配置する。最終状態要約はclearを確定したresponse内の信頼済みsnapshotからだけ作成し、再読込後の再現をMVP要件にしない。snapshot保存、`report` route、workspace保持は追加しない。

#### 12.1.1 改善単位7Bの部分更新契約

command、hint、reset、Stage 4 editor保存の既存POSTはfull HTMLを返す方式とCSRF protectionを維持する。JavaScript有効時だけsubmitを`fetch`で送信し、同一originの成功responseを`DOMParser`で解析して次の固定領域を更新する。

| region | 内容 |
|---|---|
| `stage-header` | 障害説明、目標、ACTIVE／CLEAR状態 |
| `stage-sidebar-state` | command数、hint level、star、sidebar内hint本文 |
| `stage-repository` | 現在branch、HEAD、clean、Stage固有のredacted状態 |
| `stage-workspace` | command form、実行結果、reset、条件付き限定editor、clear結果 |
| `stage-clear-dialogue` | clear後の人物反応。active中は空containerを維持する |

現在documentとresponse documentはrootの固定`data-stage-key`が一致し、各regionが両方にちょうど1件ずつ存在する場合だけ更新する。全region、response status、`Content-Type: text/html`、同一originを先に検証し、欠落、重複、stage key不一致、parse失敗では1領域も置換しない。検証後は1回の描画単位内でserver生成nodeを`importNode`して置換し、user由来文字列から`innerHTML`を組み立てない。response内のscriptは実行せず、event処理は現在documentへ登録した委譲listenerだけを使用する。

各formは送信中の二重submitを無効化し、response不明時に自動retryしない。commandの`requestId`、editorの`requestId`と`versionToken`、全formのCSRF tokenは置換後のserver responseを正本とする。通信失敗または置換拒否時は「結果を確定できないため再送せず再読込する」導線を表示する。command、hint、reset、editor保存のfallback actionにはそれぞれworkspace、sidebar hint、workspace、editorのfragmentを付け、JavaScript無効時も通常POST後に対象付近へ戻れるようにする。

通常更新はwindowとmonitor内のscroll位置をkey付きで保存・復元し、browser zoomを操作しない。更新通知は`aria-live`を使い、keyboard操作時のfocusはcommand結果、hint、editor結果へ`preventScroll`付きで戻す。clear成立時だけscrollを成功表示へ移し、clear見出しへfocusする。hint buttonは`aria-expanded`と`aria-controls`を持つ。

### 12.2 実装方式

- Spring MVC
- Thymeleaf
- 通常form POST
- 同梱した静的JavaScriptによるprogressive enhancement。SPA framework、WebSocket、専用JSON APIは使用しない
- server-side validation
- PRG patternを必要箇所で使用
- sessionにはplayer識別ではなく、現在attemptの最小情報だけを保持

ログイン、SPA、WebSocketは使用しない。appとRunnerはloopbackへbindし、BrowserからのPOSTにはCSRF protectionを適用する。

Git出力、commit message、diff、player入力、限定editor内容、error、固定会話、clear sceneはすべてuntrusted dataとして扱う。Thymeleafではplain textとしてescapeし、raw HTML描画を使用しない。ANSI escapeと表示不要なcontrol characterを除去または可視化し、少なくとも`default-src 'self'`、`script-src 'self'`、`object-src 'none'`、`base-uri 'none'`、`frame-ancestors 'none'`を含むCSPを適用する。会話用JavaScriptのために外部origin、`'unsafe-inline'`、`'unsafe-eval'`を追加しない。

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
| `status` | STARTING / ACTIVE / EXECUTING / CLEARING / RESETTING / CLEANUP_PENDING / CLEARED / FAILED / EXPIRED / ABANDONED |
| `version` | optimistic lock用の単調増加値 |
| `current_generation` | 現在workspaceの世代。resetごとに増加 |
| `workspace_id` | Runnerが発行した現在workspaceのUUID。終端時はnull |
| `create_request_id` | STARTING／reset後のworkspace作成に使う安定request ID |
| `cleanup_request_id` | clear／reset／recoveryの削除に使う安定request ID |
| `pending_stars` | CLEARING中に確定待ちの1〜3スター |
| `started_at` | 開始時刻 |
| `completed_at` | 終了時刻、nullable |
| `highest_hint_level` | 0〜4 |
| `player_reset_count` | プレイヤーが明示したreset回数。スター判定に使用 |
| `system_recovery_count` | timeout、Runner再起動、応答不明などによるworkspace再生成回数。スター判定に使用しない |
| `stars` | clear後の1〜3、nullable |
| `last_sequence_no` | commandとreset eventを含むattempt内の最終sequence |

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

`(attempt_id, sequence_no)`と`request_id`を一意にする。同一stageで非終端attemptを複数作らないpartial unique制約を持つ。変更requestでは、Git実行前に`PENDING`行を作成し、同じrequest IDの再送を再実行せず既存結果へ対応付ける。拒否入力はraw textを保存せず、固定reason codeだけを保存する。

外部Runner操作とDB更新は同一transactionにしない。短いDB transactionで中間状態と安定request IDを記録し、transaction外でRunner操作を行い、短いDB transactionで結果を確定する。clearは`CLEARING`へstarsとcleanup request IDを記録してから削除し、成功後だけ`CLEARED`へ確定する。resetは`RESETTING`へcleanup/create request IDを記録し、旧workspaceの削除成功後だけgenerationとreasonに応じたcounterを増やして`STARTING`へ戻す。削除失敗時は`CLEANUP_PENDING`として新workspaceを作らない。明示的な「新しい挑戦」だけが新しい`stage_attempt`を作る。

`stage`、`player`、`player_progress` tableは作らない。ステージは固定resource、進捗と最高スターはCLEARED attemptから導出する。

### 13.3 startup recovery

- Git Runner起動時は、前processのidempotency記録を失っているため、固定labelを持つ既存challenge containerを新規commandへ再利用せず、安全に停止・削除する。
- app起動時は`STARTING`、`ACTIVE`、`EXECUTING`、`CLEARING`、`RESETTING`、`CLEANUP_PENDING`を1 transactionずつ照合する。
- `PENDING` commandは自動再実行せず`RUNNER_ERROR`へ終端化し、対応するattemptをsystem recoveryへ遷移させる。
- system recoveryでは記録済みcleanup request IDで旧workspaceの削除を再確認してから、`system_recovery_count`と`current_generation`を増やし、新しいworkspaceを作成して`ACTIVE`へ戻す。CLEARINGは同じcleanup request IDで再送してからCLEAREDを確定する。矛盾または複数非終端attemptでは新workspaceを作らずfail closedにする。
- `CLEARED`、`FAILED`、`EXPIRED`、`ABANDONED`は再開しない。
- recovery処理自体もrequest ID、optimistic lock、generationで多重実行を防ぐ。
- app停止、Runner停止、response確定直前停止をそれぞれintegration testで再現する。

## 14. error model

| error category | appの扱い |
|---|---|
| INPUT_REJECTED | Git未実行。理由を安全な表現で表示 |
| GIT_ERROR | exit codeとtruncated outputを表示し、Gitが残した状態のまま同じworkspace／generation／attemptを継続 |
| TIMEOUT | containerを破棄し、同じ論理attemptのsystem recoveryとしてcountとgenerationを増やす。再生成失敗時だけFAILEDにする |
| OUTPUT_LIMIT | 出力を打ち切り、command結果に明示。必要ならattempt継続 |
| RUNNER_UNAVAILABLE | player操作を止め、resetまたは再起動案内 |
| CLEANUP_FAILED | attemptを継続せず、local logとUIに通知 |
| PERSISTENCE_ERROR | clear確定前なら再判定可能にし、重複記録を防ぐ |

stack trace、host path、credentialをBrowserへ返さない。

## 15. local起動

### 15.1 実装基準バージョン

2026-07-11時点の実装基準を次に固定する。

| 対象 | 固定値 | 適用 |
|---|---|---|
| JDK | Eclipse Temurin 25.0.3+9 x64 | app／Runnerのcompile・実行 |
| Java language level | 25 | Maven compiler release |
| Spring Boot | 4.1.0 | app／Runner |
| Apache Maven | 3.9.16 | Wrapperが取得して実行するbuild tool本体 |
| Maven Wrapper | 3.3.4、`only-script` | plugin 3.3.4で生成して追跡する`mvnw`／`mvnw.cmd`／properties |
| PowerShell | 7.6.3 LTS x64 | launcher、challenge image build script |
| Docker Desktop | 4.79.0、WSL 2 backend、Linux container | 初期対応環境 |
| WSL | 2.1.5以上 | Docker Desktop公式最低要件 |
| challenge base | `alpine:3.23.3@sha256:59855d3dceb3ae53991193bd03301e082b2a7faa56a514b03527ae0ec2ce3a95`、`linux/amd64` | challenge image build |
| challenge Git | Alpine package `git=2.52.0-r0` | player操作とsnapshot取得 |
| challenge image | `developer-dungeon/git-challenge:0.1.0`をlocal buildし、runtimeでは生成されたimmutable image IDだけを使用 | 1日縦切り版 |
| PostgreSQL | 18.4 | 安定版MVPから。1日版では起動しない |
| PostgreSQL image | `postgres:18.4-alpine3.23` | 安定版MVP着手時にplatform別digestを固定 |
| Testcontainers | 1.21.4 | persistence integration test |

Spring Bootの依存versionは原則として4.1.0のdependency managementへ従い、個別上書きしない。Gitの教材挙動は最新Git 2.55.0ではなく、challenge image内で再現可能なAlpine package 2.52.0-r0を正とする。

Maven Wrapperの`distributionUrl`はApache Maven 3.9.16 binary zipのHTTPS URLへ固定し、`distributionSha256Sum=5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce`を必須とする。`only-script`を使用するため`maven-wrapper.jar`は追跡・取得しない。正式なbuild commandは追跡済み`.\mvnw.cmd`だけとし、distributionのchecksum不一致ではMavenを実行しない。

Wrapperは`org.apache.maven.plugins:maven-wrapper-plugin:3.3.4:wrapper -Dtype=only-script -Dmaven=3.9.16 -DdistributionSha256Sum=...`で生成する。生成直後に`mvnw`、`mvnw.cmd`、`.mvn/wrapper/maven-wrapper.properties`のraw byte SHA-256を、相対pathのordinal昇順で`.mvn/wrapper/wrapper-files.sha256`へ固定する。contract testは3ファイルを再hashしてmanifestと比較し、1 byteでも異なれば失敗する。manifest更新はWrapperを明示更新する差分だけで行い、生成commandと3ファイルとmanifestを同じレビュー対象にする。

実装時にrepository rootの`.gitattributes`へ、`/mvnw text eol=lf`、`/mvnw.cmd text eol=crlf`、`/.mvn/wrapper/maven-wrapper.properties text eol=lf`、`/.mvn/wrapper/wrapper-files.sha256 text eol=lf`を追加する。このcheckout後のbyte列をWrapper hashの正本とし、`core.autocrlf`の値に依存させない。

`scripts/lib/LocalRuntime.psm1`に副作用のない共通preflight関数を置き、launcherとchallenge image build scriptの双方から呼ぶ。共通preflightはPowerShell、Windows architecture、WSL、Docker Desktop、daemon到達、Linux container mode、`linux/amd64`を検査し、取得不能、parse不能、不一致ではbuildとartifact更新と子process起動を行わない。launcherはさらにJDKとchallenge image identityを検査する。

- PowerShellのedition、version `7.6.3`、process architecture `x64`
- Windows 11 x86_64
- `JAVA_HOME`配下の固定`java.exe`について、vendor `Eclipse Adoptium`、runtime version `25.0.3+9`、architecture `amd64`。PATH上の別`java`へfallbackしない
- `wsl.exe --version`でWSL 2.1.5以上
- `docker version --format '{{.Server.Platform.Name}}'`でDocker Desktop 4.79.0、`docker version`／`docker info`でdaemon到達、Linux OS、`linux/amd64`

challenge imageの最終image IDは、次の実装済み手順をcontractとする。local buildだけではregistryのRepoDigestが付かないため、存在しないRepoDigestを前提にしない。

1. build inputを`challenge-image/Dockerfile`、`challenge-image/.dockerignore`、`challenge-image/rootfs/`配下の全regular file、`challenge-image/fixtures/`配下の全regular file、`scripts/build-challenge-image.ps1`、`scripts/lib/LocalRuntime.psm1`に限定する。required file／directory欠落、symlink、reparse point、またはASCIIの`[a-z0-9._/-]+`に収まらないrepository相対pathを拒否する。`challenge-image/`配下にこの集合以外のfile／directoryがあればbuild前に拒否する。
2. 各fileのraw byte SHA-256を小文字hexで求め、separatorを`/`へ統一した相対pathのordinal昇順に、`<64hex>  <relative-path>\n`形式でUTF-8 BOMなし・LF終端のcanonical manifestをmemory上に作る。そのmanifest byte列のSHA-256をbuild-input fingerprintとする。改行を含むpathや暗黙の改行変換を許可しない。
3. `.dockerignore`は最初に`**`を除外し、`Dockerfile`、`.dockerignore`、`rootfs/`、`rootfs/**`、`fixtures/`、`fixtures/**`だけを後続ruleで許可する。build scriptはこのallowlistと手順1の対象外file拒否を併用し、fingerprint対象外のbyteをeffective Docker contextへ含めない。
4. `scripts/build-challenge-image.ps1`だけが、共通preflight成功後、pin済みbase digestと厳密なpackage versionからimageをbuildし、fingerprintをOCI label `io.developer-dungeon.challenge.build-input-sha256=<64hex>`へ設定する。任意build argや別contextを受け取らない。
5. build scriptがimage内の`/usr/bin/git --version`、`linux/amd64`、完全image ID、project label、fingerprint labelを確認する。非root実行はDockerfileの固定userとRunnerの`--user 10001:10001`で二重に固定し、fixtureはworkspace生成時にRunnerがconfig、hook、attributes、module、symlink、object IDを検証する。APK artifactの追加checksum固定とcanonical manifestの詳細log出力は、Phase 1再評価後に必要性を判断する。
6. contract確認後、`docker image inspect`でplatformとcontent-addressableな完全image ID（`sha256:`＋64桁小文字hex）を取得する。
7. build scriptがrepository内のgit管理外固定path`.developer-dungeon/runtime/challenge-image.id`へ、完全image IDと改行だけをtemporary file経由で原子的に置換する。実装時に`.developer-dungeon/runtime/`を`.gitignore`へ追加する。手入力とlauncher／Runnerによる書込みを禁止する。
8. launcherが現在のworking treeからfingerprintを再計算し、artifactを厳格parseする。`docker image inspect`でID、`linux/amd64`、期待Git version、固定OCI labelのfingerprint一致を再検証してから、image IDと期待fingerprintを子process専用環境変数でRunnerへ渡す。
9. Runnerも完全image IDとfingerprint形式を再検証し、Docker inspectでlabel一致を再確認する。tagへfallbackせず、そのIDでcontainerを作成し、作成後のcontainer inspectで実image IDとlabelの一致を確認してからGitを許可する。
10. artifact欠落、空、余分な行、tag、短縮ID、未知・削除済みID、現在の入力とfingerprintが異なるstale image、platform／label／Git version不一致では、Git command前にfail closedにする。READMEなどbuild input外の変更ではstale扱いにしない。

Alpine repositoryから同じpackage artifactを将来も取得できることまでは保証せず、MVPではruntime imageの同一性を完全image IDで保証する。build再現性の残余リスクとして、repository側の差替え・取得不能を記録する。管理済みmirrorやAPK artifact checksum固定は、必要性を再評価してから導入する。

依存またはbase imageを更新する場合は別差分とし、release noteと脆弱性情報を確認し、imageを再buildしてimage IDとfixture object IDを再生成し、parser、snapshot、全Git stage contract testを実行する。古いimage IDは同じ変更内で削除せず、rollback確認後に除去する。

### 1日縦切り版

- `scripts/start-local.ps1`だけを正式な起動入口とし、appとgit-runnerを別JVM processで起動する。
- Windows 11 x86_64、Docker Desktop 4.79.0、WSL 2 backend、Linux container modeを利用する。
- launcherは`docker version`、`docker info`、OS／architecture、Linux container mode、固定challenge image IDを事前検査し、不一致なら子processを起動しない。
- PostgreSQLは起動しない。

### 安定版MVP

- PostgreSQLをDocker Composeで起動する。
- 安定版MVPでも正式なlocal runtime入口は`scripts/start-local.ps1`へ一本化する。
- IDEまたはMavenからの直接起動はunit／integration test、または明示的なdiagnostic profileだけに限定する。通常profileはtokenまたはimage IDが未設定ならruntime APIを公開せずfail closedにする。
- Git challenge containerのlifecycleはComposeへ固定せず、git-runnerがattemptごとに管理する。
- Web appにDocker socketをmountしない。
- Compose project名`developer-dungeon`、service名`postgres`、volume名`developer-dungeon-postgres-data`を固定する。DB portはloopbackだけへbindし、launcherがCompose labelを検証してから起動・停止する。volumeが存在するのにruntime credential fileが欠落・破損している場合は再生成せずfail closedにする。launcherはDB healthcheck、専用migrator JVM、Runner、appの順に起動し、逆順に停止する。

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

## 19. 実装前の確定結果

- Java、Spring Boot、Maven、Git、PostgreSQL、base imageのversionは15.1に固定した。
- challenge imageはbuild前に固定できる入力をpinし、build後の完全image ID記録を実行開始前のfail-closed gateとした。
- Runner tokenの生成・受渡方法、loopback port、起動順序、停止時回収を7.0に固定した。
- Windows上の正式な起動入口を`scripts/start-local.ps1`に固定した。
- 初期support範囲をWindows 11 x86_64＋Docker Desktop WSL 2 backend＋Linux containerに限定した。

実装は、この方針に対する井上の実装前レビューを通過し、ユーザーから実装開始の明示指示を得るまで開始しない。
