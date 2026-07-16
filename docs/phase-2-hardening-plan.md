# Phase 2 Git Runner hardening 実装前計画

## 文書情報

- 状態: 井上実装前レビューPASS（2026年7月12日）、ユーザー実装承認待ち
- 対象Phase: Phase 2 Git Runner hardening
- 上位文書: [`requirements.md`](requirements.md)、[`threat-model.md`](threat-model.md)、[`architecture.md`](architecture.md)、[`test-strategy.md`](test-strategy.md)
- 関連文書: [`vertical-slice.md`](vertical-slice.md)、[`../roadmap.md`](../roadmap.md)

## 1. 目的と完了条件

Phase 1で成立したGit Runnerの安全境界について、設定値やmockだけでは未確認の部分を、実containerと失敗経路で検証可能にする。新しいstage、永続化、認証、外部公開、限定editorは追加しない。

このPhaseの完了は、次を満たすこととする。

1. [`threat-model.md`](threat-model.md)の未確認セキュリティ受け入れ条件を、実行したtest IDと結果で追跡できる。
2. reset、TTL、graceful shutdown、timeoutの各経路で対象challenge containerが削除され、強制終了時は所有台帳から次回起動時に回収される。
3. challenge containerのnetwork、mount、user、capability、seccomp、resource limit、image identityを実際の`docker container inspect`で確認できる。
4. launcherのpreflight、readiness失敗、子process cleanupを、Docker daemonを要さないcontract testで確認できる。
5. Dockerを使うtestは、ユーザーが実行を許可した時だけ対象限定で実行する。

## 2. 今回の対象範囲

### 含める

- SEC-006〜011、SEC-013〜014、SEC-016〜018、SEC-021のうち、実containerまたはlauncher失敗経路の証拠が未確認な項目
- セキュリティ受け入れ条件2〜6、9〜10、13〜17の対象限定test
- timeout、reset、TTL、Runner shutdown、create途中失敗、orphan cleanupのcleanup保証
- cleanup失敗時に旧workspaceを隔離し、新containerの作成を禁止するapp／Runnerの状態制御
- Runnerが発行したcontainerだけを記録するローカル所有台帳と、その台帳に基づく安全なorphan回収
- `TEST-SEC-001`〜`010`、`012`〜`016`、`018`〜`023`および`TEST-LAUNCH-001`〜`013`のうち、Phase 1で実行証跡がないもの
- 上記testで判明した、同じ境界内の最小修正

### 含めない

- 新しいGit stage、stage別command拡張、限定editor（`TEST-SEC-011`）
- PostgreSQL、Flyway、progress永続化、管理DB接続
- 画面演出、物語、UIの大幅変更、外部公開、ログイン
- Phase 2と無関係な依存関係追加、Docker Compose、Testcontainers導入

SEC-001〜004、SEC-019、SEC-020はPhase 1のDockerなしunit／Web testで回帰を維持する。今回の主目的はそれらを作り直すことではなく、実containerとlauncher失敗経路でしか確認できない境界を補うことである。

## 3. セキュリティ受け入れ条件との対応

| 受け入れ条件 | Phase 1の根拠 | Phase 2で確定する証拠 | 主なtest ID |
|---|---|---|---|
| 2. socket・bind mount・device・host namespaceなし | `docker run`引数のunit確認のみ | 実container inspectでMounts、HostConfig、Devices、NamespaceModeを確認する | TEST-SEC-005 |
| 3. 外部network・管理DBへ到達不可 | `--network none`の設定のみ | NetworkModeが`none`であり、追加networkがないことをinspectする。Phase 1は管理DBを持たないため、DB接続試行は行わない | TEST-SEC-004 |
| 4. 非root・read-only・cap drop・Nnp・resource limit | 設定値の実装のみ | Config/HostConfigの実値をinspectし、期待値と完全一致させる | TEST-SEC-001〜003、006、014、015 |
| 5. hook・config・credential・external protocol無効 | Runnerの固定argvとfixture検査のunit確認 | 悪性fixtureを実workspace化する前に拒否し、固定Git環境で代表commandが外部helperを起動しないことを確認する | TEST-SEC-009、010、016 |
| 6. timeout後に削除・再利用なし | mockを使う例外経路とTTL unit test | timeout fault後に実containerが削除され、cleanup失敗時はworkspaceが隔離されて新containerを作らず、後続cleanup成功後も古いworkspaceを再利用しないことを確認する | TEST-SEC-007、012、021 |
| 8. logへの秘密値・環境変数・host path非漏えい | OutputSanitizerとtoken filterのunit test | 既知markerを使い、fake Docker executableが観測するprocess環境、launcher出力、例外、container環境、test artifactにmarkerがないことを確認する | TEST-LAUNCH-005、TEST-SEC-020 |
| 9. 1日縦切り版の必須境界 | 手動確認と設定実装 | 本表の実container証拠をSTAGE-GIT-01の固定imageで採取する | TEST-SEC-001〜010、014〜015、018〜019 |
| 10. 悪性local config・attributesの拒否 | 実装済みのworkspace検査 | fixture検査の不正ケースを実行し、Git command前に失敗することを確認する | TEST-SEC-016 |
| 13. seccompと安全なtmpfs | 設定値の実装のみ | `docker info`でdaemonのbuiltin/default seccompを確認し、container inspectでunconfinedや上書きがないこととTmpfsの実値を確認する | TEST-SEC-014、015 |
| 14. token生成・非漏えい・未認証拒否 | token filterと手動起動 | Docker不要contract testでtokenとchild環境を確認し、Runner APIの拒否を回帰する | TEST-LAUNCH-004、005、008、TEST-SEC-013、020 |
| 15. Windows preflight fail closed | 正常環境での手動preflight | Docker不要contract testで各probe失敗時にbuild・artifact更新・child起動がないことを確認する | TEST-LAUNCH-001〜003、011 |
| 16. image identity fail closed | launcher/Runner実装と手動確認 | artifact、platform、label、Git version、stale image、tag不許可をcontract／Docker integrationで確認する | TEST-LAUNCH-007、009、010、012、013、TEST-SEC-018、019、022、023 |
| 17. launcher失敗時の回収 | `try/finally`の実装のみ | fake childを使うDocker不要contract testと、Runner shutdown後の実container不存在確認を分離して確認する | TEST-LAUNCH-006、TEST-SEC-021 |

## 4. Docker integration test方針

### 4.1 実行単位と隔離

- `RunnerSecurityDockerIT`のような`*IT`命名のtest classにまとめ、通常の`mvn test`には含めない。
- 実行はユーザーの明示許可後に、事前にchallenge imageをbuildしてから、対象classだけを指定する。
- testは生成したworkspace IDとcontainer IDだけをcleanup対象として記録する。全container削除、`docker system prune`、labelなしの`docker rm`は使わない。
- test開始前にDeveloper Dungeonの通常起動を止める。`project=developer-dungeon`かつ`owner=git-runner`の既存containerが残る場合は、勝手に削除せず失敗として報告する。
- testの`finally`でも、test自身が記録したIDだけを`docker rm -f`する。
- 強制終了testはtest所有のRunner processと専用portだけを使い、ユーザーが起動したapp／Runner processを停止しない。
- 二重起動testは1つ目のRunnerが台帳lockとactive workspaceを保持した状態で2つ目を起動し、2つ目がcontainer inspect、削除、作成を1回も行わず起動失敗することを確認する。

### 4.2 lifecycleの検証方法

| 経路 | 再現方法 | 合格条件 |
|---|---|---|
| reset | workspaceを作成してdestroyする。削除成功時だけ新しいgenerationを作成する | 旧container IDがinspect不可で、新しいworkspaceだけが存在する。削除失敗時は新containerを作らない |
| TTL | 可変ClockをRunnerへ注入し15分超過を即時再現する | 対象containerが削除され、古いworkspace IDへの操作を拒否する。削除失敗時もexpired隔離を維持する |
| graceful shutdown | 実Runner processでshutdownを開始し、終了途中に新規requestを送る | shutdown開始後のrequestをDocker起動前に拒否し、記録済みcontainerを削除して終了する |
| 強制終了 | test所有Runner processでworkspaceを作成後、graceful処理を通らない停止を行い、同じ所有台帳でRunnerを再起動する | 強制終了直後の残存を検出し、再起動時startup sweepが対象だけを回収する |
| create途中失敗 | container作成後のfixture検査またはidentity検査を失敗させる | 作成済みcontainerが残らない |
| command timeout | 実containerでworkspaceを作成した後、委譲型test doubleからDocker gateway timeoutを通知してRunnerの例外cleanup経路を通す。別testで実Docker CLI processの期限超過も確認する | 実containerが削除され、そのworkspace IDを再利用できない |
| orphan cleanup | 所有台帳に記録したrunning/stopped orphan、active workspace、台帳外decoyを用意し、startup sweepとperiodic sweepを別々に実行する | activeは保持し、台帳内orphanだけを削除し、label・image・fingerprint・ID不一致または台帳外のcontainerを削除しない |

実Git commandだけで短時間のtimeoutを決定的に発生させることは、許可commandを安全に限定する方針と両立しない。このためtimeoutは、実containerの作成・削除だけを本物のDocker gatewayへ委譲し、Git実行時だけ期限超過を返すtest doubleでRunnerのcleanup判断を通す。これとは別に、testが所有する一時containerへ固定の長時間operationを実行し、実`DockerGateway`が期限超過を検出してDocker CLI processを終了することを確認する。両testとも`finally`で記録済みcontainerだけを削除する。timeoutを作るためにproductionの許可command、任意shell、危険なGit設定を追加してはならない。

### 4.3 orphan cleanupの安全条件

- challenge containerにはproject、owner、attempt UUID、workspace UUID、image fingerprintのlabelを必須とする。
- Runnerはstartup sweepより前に`.developer-dungeon/runtime/runner-owned-containers.lock`をWindowsの排他的file lockとして取得し、process終了まで保持する。lock取得失敗時は台帳読込、container inspect、削除、作成、readiness開始の前に起動失敗する。
- RunnerはDocker create前に、作成時刻、確認回数、attempt ID、workspace ID、generation、完全image ID、fingerprintを含む作成intentを、秘密値を含まないローカル所有台帳へ原子的に記録する。作成後にcontainer IDを同じentryへ原子的に追記する。台帳pathはlauncherが`.developer-dungeon/runtime`配下の固定絶対pathとしてRunnerへ渡す。
- container削除成功後だけ台帳entryをcleanup request ID付き`DELETED` tombstoneへ原子的に置換する。認証済みinternal requestでattempt ID、workspace ID、generationが一致する削除再確認にはDockerを呼ばず成功を返す。container ID追記に失敗したcontainerはworkspaceとして公開せず、作成intentを残したまま直ちに削除を試みる。削除にも失敗した場合は次回startup sweepがintentのworkspace labelから限定回収し、広いscanへfallbackしない。
- startup sweepは台帳entryごとにcontainer inspectを行い、container ID、project、owner、attempt/workspace UUID、image ID、fingerprintの全一致時だけ削除する。container ID未確定のintentはworkspace labelで候補を限定し、完全一致する候補が1件の場合だけ削除する。
- ID未確定intentの候補が0件でもentryを削除しない。各scanのDocker期限を5秒、scan間隔を2秒、回数を最大3回、startup recovery全体を20秒以内とし、後発containerを回収する。3回とも0件、複数件、不一致、台帳破損、台帳外containerの場合は台帳を保持したままRunnerをdegradedとしてreadinessを失敗させ、Docker操作を受け付けない。intentの自動破棄や広いscanへのfallbackは行わない。
- periodic sweepはstartup sweepと分離し、in-memoryのactive container IDを必ず除外する。TTL超過またはcleanup待ち状態で、台帳とinspectが完全一致するentryだけを削除する。
- candidateのinspect、削除、削除失敗は個別に記録する。候補取得失敗時に広いfilterへfallbackしない。
- cleanup失敗は最大3回の周期再試行に制限し、それ以降は台帳entryを残して次回startup sweepまたはユーザーの手動判断へ委ねる。無限loopを作らない。
- 台帳lock取得失敗、台帳破損、identity不一致、曖昧候補、startup cleanup失敗ではRunnerをdegradedにし、health/readinessを成功させず、少なくともcreate、execute、snapshotをDocker起動前に拒否する。

### 4.4 cleanup失敗時の状態方針

- appは旧workspaceのdestroy成功を受け取るまで、resetまたはsystem recovery用の新workspaceを作成しない。
- Runnerはtimeout、TTL、destroy、create途中失敗でcleanupが失敗したworkspaceを`cleanup pending`として隔離し、execute、snapshot、通常destroyを拒否する。
- 同一request IDのcleanup再送は同じcontainer IDだけを対象にし、別containerを作らない。
- container削除成功時は台帳entryを直ちに消さず、cleanup request IDと`DELETED`結果を持つtombstoneへ原子的に置換する。認証済みinternal requestでattempt ID、workspace ID、generationが一致する削除再確認にはDockerを呼ばず成功を返す。appが次generationのworkspace作成に成功した時だけ、旧generationのtombstoneを削除する。
- cleanup成功後も古いworkspace IDとgenerationは再利用しない。appは明示的なreset/recovery操作で次generationを作る。
- graceful shutdown中は新規workspace operationをDocker起動前に拒否する。cleanup失敗entryは台帳に残し、process終了を隠して成功扱いにしない。
- ローカルMVPは同時attempt 1のため、graceful shutdown開始時のactive container上限を1件とする。起動時に回収できないorphan／曖昧entryがあればreadinessを成功させない。
- internal shutdownは最初にshutdown状態へ遷移し、1件のcontainer cleanupを6秒以内（`docker rm -f`は5秒以内、台帳更新を含む残り1秒）で完了させる。成功時だけ204を返して終了し、失敗時は5xxを返してdegraded状態を維持する。
- launcherのshutdown HTTP timeoutは8秒とし、応答後のprocess終了を5秒待つ。shutdown開始から最大13秒を超えた場合だけ該当process treeを強制停止する。非2xx、HTTP timeout、強制停止を正常終了として表示せず、次回起動回収が必要であることだけを秘密値なしで報告する。
- shutdown中はcreate、execute、snapshotを拒否し、認証済みinternal shutdownの再試行と、すでにcleanup対象として確定したcontainerのdestroyだけを許可する。

## 5. container設定をinspectする共通方針

testは`docker run`のargvではなく、作成後の`docker container inspect`のJSONを構造化して読む。文字列の部分一致だけで合格にしない。

| 観点 | inspectで確認する値 |
|---|---|
| image identity | `Image`が完全image ID、`Config.Labels`のproject/owner/fingerprintが期待値 |
| network | `HostConfig.NetworkMode=none`、追加networkなし |
| mount | `Mounts`にbind mountとDocker socketがなく、`/workspace`と`/tmp`だけが期待tmpfs設定 |
| privilege | `Config.User=10001:10001`、`ReadonlyRootfs=true`、`Privileged=false`、host PID/IPC/networkなし |
| capability/NNP/seccomp | daemonの`SecurityOptions`にbuiltin/default seccompがあり、`CapDrop`がALL、containerの`SecurityOpt`に`no-new-privileges`があり、`seccomp=unconfined`または意図しないprofile上書きがない |
| resource limit | NanoCpus、Memory、PidsLimit、tmpfs size/modeが脅威モデルの初期値と一致 |
| Docker socket | Mount Source/Destinationとhost configに`docker.sock`およびhost pathがない |

network到達性は、`NetworkMode=none`と追加networkなしを必須の自動証拠とする。外部接続先を持つprobe containerや実在DBをtestのために起動しない。

daemonのSecurityOptionsからbuiltin/default seccompを確認できない場合、container側に`unconfined`指定がなくてもTEST-SEC-014は不合格とする。

## 6. test IDごとの観測方法

| test ID | test層 | 具体的な観測方法 |
|---|---|---|
| TEST-SEC-008 | Docker不要contract | 既知のASCII markerを32 KiB超ずつstdout/stderrへ出すfake Docker executableを使い、合計64 KiBで打切りとtruncated flagを確認する |
| TEST-SEC-009 | unit＋実container | malicious config/hookを持つtest専用派生imageをlocal固定imageから作り、workspace公開前に拒否する。production fixtureとcommand allowlistは変更しない |
| TEST-SEC-010 | unit＋実container inspect | URL/ref/global optionの拒否、`protocol.allow=never`、空の`GIT_ALLOW_PROTOCOL`、`GIT_PROTOCOL_FROM_USER=0`、NetworkMode noneを組み合わせて確認する。外部接続先やproduction test routeを追加しない |
| TEST-SEC-020 | Docker不要contract＋inspect＋captured log | fake Docker executableが受け取った環境をtest一時fileへ記録し、PATH/SystemRoot以外と既知markerがないことを確認する。container `Config.Env`にもtoken、app設定、credential名がないことを確認する。marker、host path、制御文字を含むfake Docker failureを与え、captured Runner logと例外にraw値がないことを確認する |
| TEST-SEC-022 | Docker不要contract | temp runtime directoryとfake image probeを使い、contract成功時だけartifactを一時fileから原子的置換し、各失敗時に既存byte列が不変であることを確認する |
| TEST-SEC-009/016の悪性fixture | 実container | test専用派生imageへ不正config、hook、attributesを1種類ずつ入れ、RunnerがGit command前に拒否する。派生image IDもtestが記録・削除する |

既知markerは実tokenや実credentialを使用しない。test結果へ値を表示せず、存在有無だけをassertする。

## 7. launcher contract test方針（Docker不要）

PowerShellのtestに外部moduleは追加しない。`scripts/lib/LocalRuntime.psm1`とlauncherから、副作用のない関数を分離し、native PowerShell assertion harnessで実行する。

- preflightはOS、PowerShell、JDK、WSL、Docker Desktop、Linux mode、artifact/image metadataをprobe objectまたはscript block経由で取得可能にする。
- child起動、readiness待機、停止は小さなadapterに分離し、testではfake childと固定HTTP結果を渡す。
- productionの`start-local.ps1`は実probeと実process adapterだけを渡す。testのための環境変数、隠しroute、production fallbackを追加しない。

必須シナリオは、PowerShell/Windows/JDK/WSL/Docker/Linux mode不一致、wrapper manifest不一致、image artifact不正、stale image、Runner readiness timeout、app起動失敗、port競合、Ctrl+C相当、launcher例外、通常終了である。各失敗では「child起動前に停止」または「開始済みchildを逆順に1回だけ停止」のどちらかを明示して確認する。

fake child／fake probeを使うcontract testが証明するのは、adapterが失敗またはinterruptを通知した後のlauncher orchestration、cleanup順序、fail-closed判断である。実Windowsのconsole Ctrl+C、実port bind失敗、実Spring context停止を確認済みとは扱わない。port競合とSpring context失敗は対象限定process testを可能な範囲で自動化し、実Ctrl+Cはユーザーの最小手動確認として実行結果を別に記録する。

Runner readiness timeoutは45秒とする。startup recoveryは最大20秒、残り25秒をSpring起動とhealth確認へ確保する。contract testでは、3回目のscan直前にcontainerが現れる境界、recoveryが20秒でdegradedになる境界、launcherが45秒より前にRunnerを停止しないことを固定する。shutdown contract testでは、cleanupが5秒直前に成功する場合は強制停止しないこと、6秒を超える場合は5xxまたはHTTP timeout後の強制停止経路へ進むことを固定する。

## 8. 実装順と確認順

1. Docker inspect結果を構造化して読むtest helperと、対象container IDだけを管理するcleanup helperを作る。
2. appのreset/recovery状態、runnerの所有台帳・shutdown状態・cleanup pending、orphan cleanup候補検証、create途中失敗・TTL・reset・shutdownの最小修正とtestを追加する。
3. container isolationとimage identityのDocker integration testを追加する。
4. `LocalRuntime.psm1`からpure preflight/token/child lifecycle関数を分離し、Docker不要のlauncher contract testを追加する。
5. Dockerを使わない対象testをメインが最小確認し、井上が実装後差分をレビューする。
6. 井上の指摘修正後、中谷がPhase 2で追加・変更したDocker不要の対象限定testを実行する。ユーザー許可がある場合だけ、続けてDocker integration test classを対象限定で実行する。
7. 実行結果と未実行のDocker/manual確認をroadmapへ反映する。

## 9. 実行コマンドの扱い

実装完了後に、実在するtest class名と`pom.xml`を確認して正確なコマンドを確定する。現時点でDockerを使うtestを通常の`mvn test`へ混ぜない。Docker integration testは、ユーザーの許可とDocker Desktop起動を確認してから、対象classだけを明示指定して実行する。

## 10. レビュー結果と後続事項

- 井上の最終Verdict: PASS
- P1: なし
- P2: なし
- P3: 次generationを作らずappを終了した場合、`DELETED` tombstoneが残り続ける可能性がある。

Phase 2のローカルMVPは同時attempt 1であり、このP3は実装開始を妨げない。`DELETED` tombstoneは未確定intentと別の状態として読み込み、tombstoneだけを理由にRunnerをdegradedへ遷移させない。複数attemptと永続化を導入するPhase 3へ進む前に、十分な冪等性保持期間、件数上限、TTLによる安全な圧縮方針を確定する。
