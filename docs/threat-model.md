# Developer Dungeon 脅威モデル

## 文書情報

- 状態: 承認済み（Phase 1〜STAGE-GIT-05実装・対象限定テスト完了、PR #10までmain反映済み）
- 対象: Git編の1日縦切り版および安定版MVP
- 上位文書: [`requirements.md`](requirements.md)、[`git-mvp-stages.md`](git-mvp-stages.md)
- 関連文書: [`architecture.md`](architecture.md)、[`test-strategy.md`](test-strategy.md)

## 1. この文書が決めること

この文書は、プレイヤー入力から実Gitを動かす機能の保護対象、信頼境界、主要脅威、必須対策、セキュリティ受け入れ条件を定める。

セキュリティ要件を満たせない場合は、ステージ機能または実装速度を削る。ホスト実行、任意シェル、Dockerソケットの課題環境への公開を代替案にしない。

## 2. 対象範囲と前提

- ローカルホスト専用・シングルプレイヤーである。
- ブラウザからSpring Bootアプリケーションへアクセスする。
- Spring Bootアプリケーションは別プロセスのGit Runnerへ構造化要求を送る。
- Git Runnerは固定challenge imageからattemptごとの使い捨てコンテナを生成する。
- 安定版MVPでは管理用PostgreSQLを使用する。
- プレイヤーは悪意のある入力を試せるものとして扱う。
- fixtureとimageは開発者が管理するが、破損または設定ミスを想定する。
- Docker daemonまたはRunner controllerが完全に侵害された場合、コンテナ境界だけではホストを保護しきれない。この残余リスクを理由にMVPを外部公開しない。

## 3. 保護対象

| ID | 保護対象 |
|---|---|
| ASSET-001 | ホストOSとユーザーのファイル |
| ASSET-002 | Docker daemon、Docker Desktop VM、他のcontainer |
| ASSET-003 | Spring Bootアプリケーションとその設定 |
| ASSET-004 | 管理用PostgreSQLと進捗・履歴データ |
| ASSET-005 | APIキー、環境変数、credential、Docker設定 |
| ASSET-006 | 他attemptのworkspaceとfixture |
| ASSET-007 | ローカルPCのCPU、メモリ、disk、process |
| ASSET-008 | 採点結果、コマンド履歴、監査ログの完全性 |

## 4. 信頼境界

```text
Browser
  | raw command / editor input
  v
Spring Boot app
  | validated GitCommand DTO / workspace token
  v
Git Runner controller
  | fixed docker CLI argv / fixed image and limits
  v
Disposable challenge container
  | /usr/bin/git argv
  v
Ephemeral workspace

Spring Boot app ---> Management PostgreSQL
```

境界ごとの原則：

- Browserの入力は信頼しない。
- Spring Bootアプリケーションの検証結果もRunnerで再検証する。
- Runnerへimage名、mount、network、capability、任意実行ファイルを指定させない。
- challenge containerの出力を信頼済みデータとして扱わない。
- 採点用snapshotはプレイヤー向け出力と別の信頼済み経路で取得する。

## 5. 必須セキュリティ制御

| ID | 制御 |
|---|---|
| SEC-001 | ユーザー入力をホストOSまたはSpring Bootプロセス上のコマンドとして実行しない |
| SEC-002 | `cmd.exe`、PowerShell、`sh`、`bash`などのシェルをGit実行経路に使用しない |
| SEC-003 | raw commandをステージ別文法で解析し、許可済みのcommand、option、ref、pathから`GitCommand`を構築する。短縮object IDはfixture内許可objectへ一意な場合だけ完全IDへ正規化する |
| SEC-004 | Spring BootとGit Runnerの双方で`GitCommand`を検証する |
| SEC-005 | 1 attemptにつき1つの使い捨てchallenge containerとworkspaceを使用する |
| SEC-006 | challenge containerへDocker socket、host bind mount、device、host namespaceを渡さない |
| SEC-007 | challenge containerのnetworkを`none`にする |
| SEC-008 | challenge containerを非root、read-only root filesystem、capability全drop、`no-new-privileges`で実行する |
| SEC-009 | CPU、memory、PID、workspace、出力、wall-clock timeoutを必須設定にする |
| SEC-010 | timeout、reset、完了、失敗、TTL超過時にcontainerとworkspaceを破棄する |
| SEC-011 | Gitのsystem/global config、credential prompt、pager、editor、hook、外部protocolを無効化または固定する |
| SEC-012 | ユーザー指定pathを使わず、限定エディタでは正規化後の実pathとsymlinkを毎回検証する |
| SEC-013 | ログから秘密値、環境変数、host絶対pathを除き、出力を切り詰める |
| SEC-014 | Spring BootとRunnerをloopbackだけにbindし、Host、Origin、CSRF、Runner認証tokenを検証する |
| SEC-015 | 管理DBをchallenge containerのnetworkへ接続せず、Runnerへ管理DB credentialを渡さない |
| SEC-016 | challenge imageを固定し、実装時にdigestをpinして、意図しない更新を防ぐ |
| SEC-017 | fixtureにhook、symlink、submodule、外部URL、秘密値を含めないことをbuild時に検証する |
| SEC-018 | repository-local configをkeyと期待valueのallowlistへ限定し、`.gitattributes`、`.git/info/attributes`、external diff、filter、custom merge driver、fsmonitor等を拒否する |
| SEC-019 | Git出力、入力、fixture、editor内容、errorをplain textとしてescapeし、ANSI・制御文字を安全に表示し、CSPを適用する |
| SEC-020 | attempt単位でexecute、snapshot、reset、destroyを直列化し、request IDとworkspace generationで二重実行・取り違えを防ぐ |
| SEC-021 | Docker default seccomp profileを有効にし、`seccomp=unconfined`を禁止する。writable tmpfsを必要最小限かつ`nosuid,nodev,noexec`で構成する |

## 6. 初期リソース制限

以下を1日縦切り版と安定版MVPの初期値とする。実測で変更してよいが、無制限にはしない。

| 項目 | 初期値 |
|---|---:|
| 1コマンドの通常timeout | 5秒 |
| timeout後の強制終了猶予 | 1秒以内 |
| attemptの無操作TTL | 15分 |
| CPU | 0.5 CPU |
| memory | 256 MiB |
| PID | 64 |
| ephemeral workspace | 64 MiB |
| stdout + stderr | 64 KiB / command |
| 同時attempt | ローカルMVPでは1 |

timeout時はGit子processだけでなくchallenge containerを停止し、そのattemptを継続不能として再生成する。

## 7. Git実行環境の固定

Runnerは継承環境を原則破棄し、必要な値だけを設定する。

- Git executableをchallenge image内の固定絶対pathにする。
- `HOME`と`XDG_CONFIG_HOME`を空の専用領域へ向ける。
- system/global Git configを読み込ませない。
- `GIT_TERMINAL_PROMPT=0`とし、credential promptを禁止する。
- pagerを無効化し、interactive editorを起動しない。
- `core.hooksPath`をread-onlyの空directoryへ固定する。
- `safe.directory=*`を使用せず、workspaceを実行UIDの所有物にする。
- `protocol.allow=never`を基本とし、MVPでは外部protocolを許可しない。
- `GIT_ALLOW_PROTOCOL`、`GIT_PROTOCOL_FROM_USER`を安全側へ固定する。
- `GIT_DIR`、`GIT_WORK_TREE`、`GIT_EXEC_PATH`など、継承されたGit環境変数を除去する。
- player入力として`-c`、`--config-env`、`--exec-path`、`--git-dir`、`--work-tree`を許可しない。
- repository-local `.git/config`はkeyと期待valueの組でallowlist化し、workspace生成時にも再検証する。基本は`core.repositoryformatversion=0`、`core.filemode=true`、`core.bare=false`、`core.logallrefupdates=true`だけとする。
- `alias.*`、`diff.*.command`、`filter.*`、`merge.*.driver`、`core.fsmonitor`、`credential.*`、signing program、SSH commandなどを禁止する。
- MVP fixtureでは`.gitattributes`、`.git/info/attributes`、`.gitmodules`を禁止し、Runner所有設定`core.attributesFile=/dev/null`でsystem attributesを無効化する。
- player向け`diff`や`show`では、Runnerが安全な内部optionを追加してexternal diffとtextconvを無効化する。

## 8. 脅威と対策

| 深刻度 | 脅威 | 想定される影響 | 最低限の対策 |
|---|---|---|---|
| Critical | 任意コマンド実行 | hostまたはcontainer内で任意processを起動 | SEC-001〜004、固定Git executable、shell禁止 |
| Critical | command引数による制限回避 | Git global optionやhelperを通じた任意実行 | global option拒否、command別grammar、二重検証 |
| Critical | shell展開 | pipe、redirect、substitutionによる任意操作 | shellを起動せずargv配列を構築、改行・制御文字拒否 |
| Critical | Docker socket悪用 | host相当の権限取得、任意container生成 | appとchallengeへsocketを渡さず、controller APIを固定 |
| Critical | host fileアクセス | 個人ファイルやsecretの漏えい・破壊 | bind mountなし、user pathなし、ephemeral volumeのみ |
| Critical | container escape | Docker VMまたはhost侵害 | 非root、cap drop、seccomp、no-new-privileges、更新済み固定image、外部公開禁止 |
| High | Git hook実行 | checkout、commit、merge等を契機に任意process実行 | empty read-only hooksPath、fixture検査 |
| High | Git config・alias・helper悪用 | external command、credential、pathの変更 | config command禁止、system/global無効、`-c`禁止 |
| High | external protocol・remote helper | 外部通信、data流出、process起動 | network none、protocol allow deny、URL入力禁止 |
| High | path traversal | workspace外の読み書き | server生成workspace ID、canonical path、許可path固定 |
| High | symlink | editor APIを使ったworkspace外アクセス | fixtureで禁止、NOFOLLOW相当、書込直前再検証 |
| High | resource exhaustion | hostのCPU、memory、disk枯渇 | SEC-009、同時attempt 1、出力打切り |
| High | fork bomb・無限process | PID枯渇、cleanup不能 | PID limit、timeout、container kill |
| High | management DB到達 | 進捗改ざん・data破壊 | network分離、credential非共有、SEC-015 |
| High | localhostへのcross-site request | 悪意あるWeb pageからGit操作 | loopback、Host/Origin検証、CSRF token |
| High | Git出力・editor内容によるXSS | Browser上でscript実行、local API操作 | plain-text escape、raw HTML禁止、control処理、CSP、SEC-019 |
| High | 並行要求・二重POST | Git操作の二重実行、履歴・workspace破損 | attempt lock、状態機械、request ID、generation、SEC-020 |
| Medium | logへの機密情報混入 | local logやscreenから漏えい | 架空data、redaction、truncation、環境非記録 |
| Medium | 他attemptの参照 | 状態・回答・履歴の混線 | random workspace token、1 attempt 1 container、終了時破棄 |

## 9. ステージ別の追加制約

全ステージで、Runnerは観察commandのobject IDを原則12桁で表示し、appはそのattemptで実際に表示済みの12桁IDまたは完全IDだけを受理する。appが完全IDへ正規化し、Runnerがworkspace内objectとstage allowlistを再検証する。fixture build時に12桁prefixの一意性を確認し、未表示・短すぎる・曖昧prefix、未知object、revision式は拒否する。

### STAGE-GIT-01

- `revert`対象をfixtureの`C2`へ限定する。
- editorを起動しない`--no-edit`だけを許可する。

### STAGE-GIT-02

- `reset --hard`対象をfixtureの`C0`へ限定する。
- 任意ref、pathspec、remote branchを許可しない。

### STAGE-GIT-03

- stash message、pathspec、include-untrackedなどの追加optionをMVPでは許可しない。

### STAGE-GIT-04

- 編集可能pathを1ファイルへ固定する。
- `.git`配下、絶対path、`..`、symlink、hard linkの扱いを拒否する。
- ファイルサイズと文字encodingを固定する。

### STAGE-GIT-05

- `reflog`は引数なしのstage専用commandとし、RunnerがHEAD、format、12桁abbrev、最大8件を固定する。`--all`、任意ref、`HEAD@{n}`、`delete`、`expire`、`gc`を許可しない。
- appは`show`とbranch作成のobject IDを、Runnerのinitial snapshotが返した`C0`／`C1`かつattempt内で表示済みのものへ限定する。未表示、短すぎる、曖昧prefix、未知40桁ID、revision式をGit実行前に拒否する。
- Runnerは表示履歴を保持せず、受け取った完全IDの40桁形式、commit objectの存在とtype、固定fixture allowlistを再検証する。branch作成はstage専用commandとし、branch名を`feature/payment-retry`、targetをRunner側固定fixture定義の完全`C1`へ固定する。Browser由来branch名をRunnerへ渡さず、`C0`や別objectをtargetにできないようにする。
- switch先も`feature/payment-retry`へ固定し、detached HEAD用checkoutや任意branch switchを許可しない。
- fixture buildとworkspace生成時に`C1`のobject type、parent、tree、refからの到達不能、HEAD reflog内の期待entryを検証する。`C0`／`C1`の`rev-parse --short=12`がちょうど12桁で完全IDのprefixと一致し、相互に異なることを必須とする。13桁化、prefix衝突、reflog固定行形式不一致ではworkspaceを公開しない。reflogを改変・expire・gcするcommandを許可しないため、attempt中のobject保持をGitの期限任せにしない。
- refから到達不能なC1の完全IDはplayer向けreflog stdoutから導出せず、検証済みchallenge imageとRunner側固定fixture定義からStage 5専用initial snapshotへ格納する。appとRunnerはこの信頼済みC1をhint、正規化、allowlist、clear判定へ使用する。
- reflog、show、logの出力はuntrusted plain textとしてescapeし、固定件数に加えて既存の64 KiB出力上限とtimeoutを適用する。出力を採点へ使用しない。

## 10. Runner controllerの権限

Runner controllerはDocker daemonを操作できるため、高権限componentとして扱う。

- Spring Bootアプリケーションから任意のDocker optionを受け取らない。
- 使用image、container name prefix、resource limit、network、mount、entrypointをserver側定数へ固定する。
- APIはattempt作成、構造化Git command実行、snapshot取得、破棄だけにする。
- attemptごとのlockまたはqueueでexecute、snapshot、reset、destroyを直列化する。
- request IDとworkspace generationを検証し、同じrequest IDの再送ではGit操作を再実行せず、記録済み結果を返す。
- 実行結果が不明なままRunnerが再起動した場合は自動再実行せず、workspaceを破棄して同じ論理attemptのresetとして扱う。
- loopbackへbindし、起動時生成tokenでSpring Bootアプリケーションを認証する。
- playerへcontroller endpointとtokenを公開しない。
- launcherが生成したRunner tokenはapp／Runnerの子process環境とmemoryだけに保持し、引数、file、logへ残さない。Runnerが起動するDocker CLIはallowlist環境を使用し、token、app設定、credentialを継承しない。challenge containerにも渡さない。
- readinessとshutdownはloopback＋同tokenのlauncher専用internal operationとし、認証不要shutdownを作らない。
- Docker CLIを使う場合もshellを介さず、固定argvを構築する。

Windows + Docker Desktopを初期対象とする場合、Docker Desktop VMは追加の境界になるが、controller侵害時のDocker権限リスクは残る。外部公開へ進む場合は専用runner VMまたは専用hostを必須として、脅威モデルを再作成する。

## 11. 管理DBとログ

### 管理DB

- Spring Bootアプリケーションだけが管理DB credentialを持つ。
- Git Runnerとchallenge containerへcredential、JDBC URL、DB networkを渡さない。
- SQLはparameter bindingを使用し、player入力を管理SQLへ連結しない。
- progressとhistoryの更新をtransactionで行う。

### ログ

記録してよいもの：

- stage key、attempt ID、command kind、result kind
- exit code、duration、timeout、出力打切りの有無
- redaction・truncation済みのplayer入力と出力

記録しないもの：

- 環境変数全体
- Docker認証情報、Runner token、DB credential
- host絶対path
- 実在する個人情報

## 12. cleanup

- 安定版MVPは信頼済みsnapshotによる自動クリア後に直ちにcontainerを破棄し、プレイヤーの復旧報告待ちを理由にclear可能なworkspaceを保持しない。
- `report`待機を将来導入する場合は、idle TTL、attemptの最大生存時間、定期sweeper、期限切れ状態、cleanup目的、再送、古いgenerationの拒否を先に脅威モデルと状態機械へ追加し、未確定のまま実装しない。
- 正常クリア、リセット、明示終了、timeout、Runner例外でcontainerを破棄する。
- RunnerはDocker create前にcontrollerが発行したattempt／workspace ID、generation、完全image ID、fingerprintを作成intentとして秘密値を含まないローカル所有台帳へ原子的に記録し、create後にcontainer IDを追記する。削除成功後だけentryを消す。
- Runnerは台帳操作とstartup cleanupより前にOS排他lockを取得して終了まで保持し、二重Runnerが同じ台帳とcontainerを操作することを防ぐ。lock取得失敗時はDocker操作前に起動失敗する。
- 起動時cleanupは所有台帳のentryと実containerのproject／owner／attempt／workspace／image／fingerprintが完全一致する場合だけ回収する。台帳破損、台帳外、不一致では削除せずfail closedにする。
- container ID未確定intentの候補が0件でも自動削除せず、5秒期限のscanを2秒間隔で最大3回、全体20秒以内に再実行する。不明なら台帳を保持してreadinessと新規Docker操作を拒否する。
- 定期cleanupは起動時cleanupと分離し、in-memoryのactive containerを除外した上で、TTL超過またはcleanup待ちの台帳entryだけを回収する。
- リセットまたはsystem recoveryで旧containerの削除に失敗した場合は旧workspaceを隔離し、削除成功まで新containerを作成しない。
- graceful shutdown開始後は新規requestをDocker起動前に拒否する。強制終了で残ったcontainerは次回起動時に所有台帳から回収する。
- cleanup失敗は隠さず、attemptを失敗扱いにしてローカルログへ残す。
- 定期再試行は最大3回に制限し、以降は台帳entryを残して次回起動時またはユーザーの手動判断へ委ねる。無限cleanup loopを作らない。
- container削除成功後はcleanup request ID付き`DELETED` tombstoneを保持し、応答喪失後の同一request再送をDocker再操作なしで成功扱いにする。次generation作成成功後だけ旧tombstoneを削除する。

## 13. セキュリティ受け入れ条件

1. shell metacharacter、改行、未知option、Git global optionを含む入力がprocess実行前に拒否される。
2. challenge containerにDocker socket、host bind mount、device、host network、host PID namespaceがない。
3. challenge containerから外部networkと管理DBへ接続できない。
4. 非root、read-only root、cap drop、no-new-privileges、resource limitが実際のcontainer設定に反映される。
5. Git hook、global config、credential prompt、external protocolが利用できない。
6. timeoutでcontainerが停止・削除され、workspaceが再利用されない。
7. 限定エディタから`.git`、別path、symlinkへアクセスできない。
8. logにcredential、環境変数、host絶対pathが出力されない。
9. 1日縦切り版でも同じ必須境界を満たす。
10. 悪意あるlocal configまたは`.gitattributes`を含むfixtureがworkspace実行前に拒否される。
11. Git出力、editor内容、errorにHTML・script・ANSI・制御文字を含めてもDOMとして解釈されない。
12. command二重送信、command中reset、timeout中destroy、response喪失後再送でGit操作と履歴が一度だけ確定する。
13. Docker daemonのSecurityOptionsでbuiltin/default seccompが有効で、challenge containerが`unconfined`または意図しないprofile overrideを持たず、writable mountが`nosuid,nodev,noexec`とsize上限を持つ。
14. 256-bit tokenがcanonicalなpaddingなしbase64urlで生成され、引数、親process環境、file、log、Docker CLI、challenge containerへ漏れず、未認証のhealth／shutdown／Runner requestがDocker起動前に拒否される（TEST-LAUNCH-004、005、008、TEST-SEC-013、020）。
15. PowerShell、Windows architecture、JDK、WSL、Docker Desktop、Linux container mode、Maven distributionのpreflight不一致でbuild、artifact更新、子process起動を行わない（TEST-LAUNCH-001〜003、011）。
16. challenge image ID、`linux/amd64`、Git version、build-input fingerprint labelをlauncherとRunnerが検証し、tag、stale image、別platformへfallbackしない（TEST-LAUNCH-007、009、010、TEST-SEC-018、019、022、023）。
17. Runner／app readiness timeout、port競合、context初期化失敗、Ctrl+C、launcher例外、通常終了では開始済み子processと所有containerを回収し、graceful shutdown開始後は新規requestを拒否する。強制終了では残存containerを所有台帳へ残し、次回Runner起動時に回収する（TEST-LAUNCH-006、TEST-SEC-021）。

これらのいずれかを確認できない状態では、縦切り版またはMVPを完成扱いにしない。

## 14. 将来のSQL編

SQL編へ着手する場合は別の脅威モデルを作成する。最低条件は、管理DBと別instance、別network、別credential、最小権限role、read-only transaction、statement timeout、lock timeout、結果行数制限、危険文と複数文の制限である。現在のGit RunnerをSQLへ汎用化しない。

## 15. 残余リスク

- Docker daemonまたはRunner controller自体の脆弱性
- Docker Desktopおよびhost OSの脆弱性
- Git本体の未知の脆弱性
- 固定imageや依存packageのsupply-chain risk
- localhost限定でも、同一PC上の別processからRunnerを狙われる可能性
- app上で`ACTIVE`な論理attemptを長時間放置した場合のplayer session単位の自動期限切れは未実装であり、現行MVPではshutdown、startup recovery、Runner所有台帳によるcleanupへ依存する

MVPはこれらを完全に排除するものではない。外部公開、複数ユーザー、本番運用へ進む前に、専用VM、認証、監視、更新運用を含む別設計が必要である。
