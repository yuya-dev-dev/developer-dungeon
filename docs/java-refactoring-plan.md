# Javaコード大規模リファクタリング計画

## 文書情報

- 状態: クラスタ1〜5完了。責務分割、可読性改善、井上レビュー、通常／DB／Docker testまで完了
- 調査開始commit: `009f53d`（PR #25マージ後の`main`）
- 完了基準commit: `f7ef5a4`（PR #30マージ後の`main`）
- 目的: Javaを学び始めた開発者が、処理の入口、責務、状態の所有者を追いやすいコードへ段階的に整理する
- 上位制約: [`architecture.md`](architecture.md)、[`threat-model.md`](threat-model.md)、[`test-strategy.md`](test-strategy.md)
- 読み方: [`code-reading-guide.md`](code-reading-guide.md)

## 1. 目的と成功条件

このリファクタリングは機能追加ではない。現在の動作と安全境界を維持したまま、命名、責務、依存方向、処理の見通しを改善する。

成功条件は次のとおりとする。

- BrowserからGit Runner、Docker、DBまでの処理経路をファイル名とclass名から追える。
- 1つのclassが教材定義、入力解析、採点、状態機械、外部I/Oなど複数の主要責務を同時に持たない。
- stateを持つcoordinatorと、stateを持たないpolicy／reader／validatorの区別が明確である。
- 外部仕様、security、idempotency、cleanup、採点結果に意図しない変更がない。
- 初心者向けの可読性を優先し、抽象化の数を増やすこと自体を成果にしない。
- 全Javaファイルを調査対象にするが、可読性が改善しないファイルを変更数のためだけに編集しない。

行数やmethod数は問題発見の手掛かりに使うが、機械的な上限を完成条件にしない。短くするために処理を別classへ移しただけの分割は採用しない。

## 2. 変更しないもの

次はbehavior-preserving refactorの外部contractであり、明示的な別承認なしに変更しない。

### 2.1 Webとprocess境界

- HTTP method、path、status、redirect、CSRF、view名、form field。
- appとRunnerのloopback bind、token header、token比較、internal health／shutdown。
- Spring component scanの起点と起動class。
- 正式launcher、JDK 25検証、process起動・停止順、repository lock。

### 2.2 Runner contractとGit境界

- `runner-contract`のrecord、enum、JSON field、operationの意味。
- command allowlist、完全object IDの再検証、表示済みobject IDのprovenance。
- hostでplayer入力を実行しないこと、固定argv、Docker環境変数allowlist。
- image ID／fingerprint／container identity、mount、network、resource limit。
- output上限、timeout、sanitization、error category。
- Java問題集からRunnerを参照しない境界。

### 2.3 attempt、workspace、DB

- DB schema、migration、role権限、status値、optimistic version。
- attempt ID、request ID、sequence、generation、再送時のidempotency。
- player reset countとsystem recovery countの分離。
- clear snapshot、star、hint、reset、recoveryの意味。
- cleanup成功前にclear／resetを確定しないfail-closed動作。
- workspace TTL、orphan回収、shutdown中のcreate拒否、degraded状態。

### 2.4 教材content

- Git課題と研修のfixture、clear条件、許可command、ヒント、表示文言。
- Java問題集9問の要求仕様、進捗3状態、模範codeの動作結果。
- `app/src/main/resources/java-problems/**/reference/*.java`をruntime production classとして共通化しない。

## 3. 現在のJava資産

2026-08-23時点の調査結果は次のとおりである。

| 分類 | file数 | 行数 | 扱い |
|---|---:|---:|---|
| app本体 | 42 | 2,381 | クラスタ2・3で責務分割済み |
| Git Runner本体 | 15 | 1,719 | クラスタ4で4つのstateless責務を分離 |
| runner-contract | 13 | 188 | 外部contract。原則変更禁止 |
| DB migrator | 1 | 20 | schema起動入口。原則変更禁止 |
| app test | 31 | 2,814 | appのcharacterization／回帰証拠 |
| Runner test | 17 | 2,359 | Runnerのcharacterization／security証拠 |
| Java教材reference source | 18 | 963 | 教材content。クラスタ2で個別に可読性を判断 |

合計は137 Java fileである。file数はsourceの構成数であり、Surefireが実行するtest case数とは区別する。module依存は次の向きだけとする。

```text
app ---------> runner-contract <--------- git-runner
 |
 +-- test scope only --> db-migrator

db-migrator --X--> app / git-runner
Java教材reference --X--> runtime component
```

### 3.1 app本体のclass family

| family | 現在の主なclass | 現在の責務 | 後続クラスタ |
|---|---|---|---|
| 起動・全体設定 | `DeveloperDungeonApplication`、`AppConfiguration`、`SecurityConfiguration`、`LoopbackRequestFilter`、`AppInternalController` | Spring起動、bean、Web security、loopback internal API | 3。公開入口を維持し必要時だけ移動 |
| Portal | `PortalController` | タイトル兼編選択画面 | 2。現状が単純なら変更しない |
| Java問題集Web | `JavaLearningController` | 一覧、詳細、進捗POST | 2 |
| Java問題集application | `JavaLearningService` | catalogと進捗の組合せ | 2 |
| Java問題集domain | `JavaProblem`、`JavaDifficulty`、`JavaProgressStatus` | 問題、難易度、進捗の値 | 2。値の意味は維持 |
| Java問題集content | `JavaProblemCatalog`、`JavaProblemContentLoader`、`JavaProblemCatalogValidator` | catalog調整／classpath読込／stateless検証 | 2で責務分割済み |
| Java問題集persistence | `JavaProgressRepository`、`JdbcJavaProgressRepository` | 進捗取得・保存 | 2 |
| Git Web | `StageController` | 全stage／training route、model組立 | 3 |
| Git application | `StageService` | attempt lifecycle、Runner呼出し、採点、recovery、表示状態 | 3。state所有者を維持 |
| Git stage policy | `StageRules`、`StageOneGrader`、`GitCommandParser`、各presentation／editor policy | 教材定義、parse、normalize、採点、hint、表示済みID | 3 |
| Git domain／view value | `StageDefinition`、`StageView`、`StageGrade`、`StageProgress`、`StageOutcome`ほか | 固定値と画面用の不変data | 3 |
| Runner client | `RunnerClient`、`RunnerClientProperties` | typed HTTP通信、token付与 | 3。wire contract維持 |
| Git persistence | `StagePersistence`、`JdbcStagePersistence`、`MemoryStagePersistence` | attempt状態遷移と進捗 | 3。DB contract維持 |
| 出力境界 | `OutputSanitizer`、`StageInputException`、`StageFeedbackKind` | player表示へ出す値の制限と分類 | 3 |

### 3.2 Git Runner本体のclass family

| family | 現在の主なclass | 現在の責務 | 後続クラスタ |
|---|---|---|---|
| 起動・設定 | `GitRunnerApplication`、`RunnerConfiguration`、`RunnerProperties` | Spring起動、固定設定、bean | 4 |
| internal API | `RunnerController`、`RunnerTokenFilter` | typed endpoint、token認証 | 4。route維持 |
| command validation | `RunnerCommandValidator` | command kindとargumentの再検証 | 4。stateless候補 |
| Docker gateway | `DockerGateway` | 固定Docker CLI process実行 | 4。秘密値を継承しない境界を維持 |
| ownership ledger | `ContainerOwnershipLedger`、`FileContainerOwnershipLedger`、`MemoryContainerOwnershipLedger` | 所有containerの永続／memory台帳 | 4 |
| workspace coordinator | `RunnerWorkspaceService` | workspace registry、request結果、Docker lifecycle、TTL、cleanup、shutdown | 4で単一状態所有者を維持 |
| Git argv | `RunnerGitArguments` | typed commandから固定Git argvへの変換 | 4で分離済み |
| snapshot | `RunnerSnapshotReader` | 固定Git観測からtyped snapshotを構築 | 4で分離済み |
| stage policy | `RunnerStagePolicy` | 許可command、branch、object target、初期fixture検証 | 4で分離済み |
| editor policy | `RunnerEditorPolicy` | file key、内容、改行、byte上限、version token検証 | 4で分離済み |

### 3.3 contract、migrator、test、教材source

- `runner-contract`の13 typeはappとRunnerのwire contractであり、可読性だけを理由にrename、統合、package移動を行わない。
- `DatabaseMigrator`はFlywayの独立起動入口である。変更理由がなければ維持する。
- 48 test fileは変更前後の証拠である。test都合でproduction可視性を`public`へ広げない。
- 18 reference sourceは9問ごとに別compile／Main実行される教材である。共通base classやframeworkへ統合しない。

## 4. 現在の主要な可読性課題

| class | 行数 | 混在している責務 | 方針 |
|---|---:|---|---|
| `RunnerWorkspaceService` | 約600 | lifecycle、Docker、fixture固有観測、idempotency、TTL、cleanup | クラスタ4でstateless責務を抽出済み。追加分割はクラスタ5で再評価 |
| `StageRules` | 約575 | 8教材定義、parse、normalize、hint、対象capture、採点、表示済みID | 教材catalog、command policy、grading policyを別rollback単位で抽出 |
| `StageService` | 約339 | attempt memory、DB遷移、Runner call、recovery、clear、reset、view生成 | attempt lifecycle coordinatorを1つに固定し、stateを持たない処理だけを先に分離 |
| `FileContainerOwnershipLedger` | 約187 | file format、atomic更新、ownership検証 | I/Oとdata変換の境界を確認してから判断 |
| Java問題集content境界 | `JavaProblemCatalog` 56、`JavaProblemContentLoader` 44、`JavaProblemCatalogValidator` 136 | catalog調整、classpath I/O、stateless検証 | クラスタ2で分離済み。公開APIとdata契約を維持する |
| `JdbcStagePersistence` | 約140 | 多数の状態遷移SQL | SQL状態機械を崩さず、名前と補助methodだけを慎重に整理 |
| `RepositorySnapshot` | 約135 | 大きなwire recordと互換constructor | contractのため維持。変更対象にしない |

## 5. lifecycleとidempotencyの単一所有者

### 5.1 app側

後続実装でも、1つのattempt lifecycle coordinatorが次を所有する。

- attemptごとの排他入口。
- `Attempt`のmutable state。
- command sequence、workspace generation、optimistic version。
- command／write requestの完了結果と再送防止。
- 表示済みobject IDとhintで開示したIDのprovenance。
- DB transitionとRunner callの前後関係。
- player reset、system recovery、clear cleanup。

現在のglobal `synchronized`をper-attempt lockへ変えること、同期範囲を狭めること、非同期化することは今回のリファクタリングへ含めない。

commandの順序は次を維持する。

1. 同じ排他境界でattemptとrequest IDを確認する。
2. parse／normalizeに失敗した場合もsequence付きでrejectionを永続化する。
3. `beginCommand`でexpected versionとrequest重複を確定する。
4. Runnerを1回だけ呼ぶ。
5. response snapshotを採点し、command結果を永続化する。
6. 成功かつ非truncatedの観察結果だけから表示済みIDを記録する。
7. clear候補ではRunner cleanup成功後にだけ`completeClear`する。
8. cleanup失敗は`CLEANUP_PENDING`としてfail closedにする。
9. 応答不明時は同じcoordinatorがsystem recoveryを直列に行う。

### 5.2 Runner側

後続実装でも、1つのworkspace lifecycle coordinatorが次を所有する。

- workspace registryとgeneration。
- operation別request結果とdeleted tombstone。
- allowed objectとstage target。
- ownership ledger、cleanup pending、cleanup attempt。
- TTL、startup orphan cleanup、shutdown、degraded状態。
- create／execute／read／write／snapshot／destroyの共通排他境界。

初期に抽出できるのは、独自map、lock、retry、lifecycle状態を持たないsnapshot reader、fixture validator、editor content policy、Git argv builderである。cleanupやregistryを別beanへ移して所有者を複数にしない。

## 6. 現行packageと論理責務

リファクタリング後も、Git編の物理packageは主として`jp.yuya.dev.developerdungeon.app`、Runnerは`jp.yuya.dev.developerdungeon.runner`の単一packageである。初期案にあった`app.git.*`や`runner.api`などへの機械的なpackage移動は採用していない。Java問題集だけは、独立した教材境界として`app.javalearning.web`、`application`、`domain`、`content`、`persistence`へ分割している。

| 物理package | 論理責務family | 主なclass／境界 |
|---|---|---|
| `app` | Web | `StageController`、`AppInternalController` |
| `app` | Application coordinator | `StageService` |
| `app` | Stage policy | `StageRules`、`StageCatalog`、`StageCommandPolicy`、`StageStatePolicy` |
| `app` | Runner adapter／persistence／security | `RunnerClient`、`StagePersistence`、`LoopbackRequestFilter` |
| `runner` | Internal API／validation | `RunnerController`、`RunnerTokenFilter`、`RunnerCommandValidator`、`RunnerStagePolicy` |
| `runner` | Git／snapshot／editor policy | `RunnerGitArguments`、`RunnerSnapshotReader`、`RunnerEditorPolicy` |
| `runner` | Stateful lifecycle／cleanup ownership | `RunnerWorkspaceService`、`DockerGateway`、`ContainerOwnershipLedger` |

論理familyは依存方向と読み順を示す分類であり、実在しないpackage名ではない。循環依存を作らず、attempt stateは`StageService`、workspace stateは`RunnerWorkspaceService`だけが所有する。2つ目の実利用がないinterfaceや、見た目だけを理由にしたpackage階層は追加しない。詳細は[`architecture.md`](architecture.md)5章・6章を正本とする。

## 7. 実装クラスタとrollback単位

クラスタは進捗上の大分類であり、1つの巨大差分を意味しない。各rollback単位で対象file allowlist、変更禁止file、対象testを確定する。

### クラスタ2: Java問題集と低リスクなapp境界

1. Java問題集の現状characterizationを追加または確認する。
2. `JavaProblemCatalog`からstateless validationを抽出する。
3. content読込を独立した責務へ整理する。
4. Java問題集package内の命名と単純な重複を整理する。
5. Portalは変更理由がある場合だけ扱う。
6. reference sourceはruntime差分へ混ぜず、クラスタ2内の独立した教材content用rollback単位として最後に扱う。要求仕様との一致、compile、`Main`実行、表示上の教育意図を問題ごとに確認し、改善理由がないsourceは変更しない。

クラスタ2では、既存characterizationを維持したまま、`JavaProblemCatalog`からclasspath I/Oとstateless validationをpackage-private classへ抽出した。Portal、進捗DB、問題JSON、reference sourceには変更理由がなかったため変更していない。reference sourceは既存testで9問すべてのcompileと`Main`実行を再確認し、可読性だけを理由とする変更は行わなかった。

### クラスタ3: Git app

1. 現行の単一packageを維持し、変更対象classのallowlistを固定する。
2. `StageRules`から固定教材catalogを抽出する。
3. command parse／normalize policyを抽出する。
4. grading／hint／displayed object policyを抽出する。
5. `StageService`はattempt coordinatorを残し、view組立やstateless補助から抽出する。
6. persistenceとRunner clientはwire／DB契約を変えず、最後に可読性を判断する。

クラスタ3では、`StageRules`の既存窓口と`StageTargets`を維持したまま、固定教材を`StageCatalog`、入力parse／normalize／表示済みobject照合を`StageCommandPolicy`、fixture検証／hint／snapshot採点を`StageStatePolicy`へ分離した。characterization追加、catalog、command policy、state policyをそれぞれ独立commitとし、各段階で対象限定testを成功させた。`StageService`はattempt、DB遷移、Runner呼出し、recovery、cleanupを同じ同期境界で所有するため変更せず、persistence、Runner client、route、template、fixture、教材内容も維持した。

### クラスタ4: Git Runner

1. 現行の単一packageを維持し、変更対象classのallowlistを固定する。
2. stateless Git argv builderを抽出する。
3. snapshot readerを抽出する。
4. fixture／stage command validatorを抽出する。
5. editor content／file validationを抽出する。
6. lifecycle coordinator、registry、ledger、cleanupは最後まで単一所有者を維持する。
7. coordinator自体の追加分割は、先行抽出後の行数と理解度を再評価して別承認する。

クラスタ4では、`RunnerWorkspaceService`を唯一のstateful coordinatorとして維持し、次の4単位だけを順番に抽出する。各単位にはpackage-private classの直接characterization testを追加し、単独でrollbackできるcommitにする。

1. `RunnerGitArguments`: `GitCommand`から固定Docker argvを構築する。共通化は`docker exec`と固定環境変数までに限定し、通常snapshot、stash、branch確認、fixtureのlocal config観測は用途別suffixを維持する。任意argvや任意suffixを受けるAPIは作らず、返却値は毎回新しいimmutable listとする。
2. `RunnerSnapshotReader`: container IDとstage keyから固定手順で`RepositorySnapshot`とfixture観測値を読む。任意path、任意ref、任意argvを受けるpackage APIは作らず、既存の5秒timeout、exit code 0／1／error、truncation、state file、stash異常の意味を維持する。
3. `RunnerStagePolicy`: Dockerへ依存しない純粋policyとして、stage kind、branch、object target、初期fixtureを検証する。service所有の`allowedObjects`やworkspace stateを持たず、`RepositorySnapshot`と型付きimmutable observationだけを受け取る。
4. `RunnerEditorPolicy`: file key、repository edit state、改行正規化、UTF-8／control character／2,048 byte上限、SHA-256 version tokenを純粋に扱う。Docker read／write、request replay、response保存、cleanupはcoordinatorに残す。

player指定object IDを扱う実行順は、次の順序から変更しない。

1. `RunnerCommandValidator`による型と形式の検査。
2. workspace／generationとrequest replayの確認。
3. service所有`allowedObjects`との完全一致確認。
4. `RunnerSnapshotReader`の固定`git cat-file -t`によるcommit型確認。
5. Docker非依存の`RunnerStagePolicy`によるstage kind／branch／target検査。
6. 固定argvによるplayer command実行。

クラスタ4では、上記4単位を独立commitで実装した。`RunnerWorkspaceService`はmaps、workspace、request結果、ledger、TTL、cleanup、shutdownの唯一の所有者として残し、約1,000行から約600行へ縮小した。`runner-contract`、HTTP route、Spring wiring、challenge image、fixture、教材内容、error message、timeout、resource limitは変更していない。元実装でstage allowlistより前に行っていたrevert対象検証だけを同じ順序で維持し、allowlist外、non-commit、誤revert対象、request replayのDocker call provenanceをservice境界testで固定した。

未知、未表示、現在workspaceの`allowedObjects`にないobject IDではDockerを呼ばない。`allowedObjects`に含まれるがstageの誤targetであるIDは、現行どおり固定`git cat-file -t`を1回実行した後に`RunnerStagePolicy`が拒否し、player commandは実行しない。replay時には追加Docker呼出しを行わない。editorの純粋入力検証は現行どおりreplay lookup前、snapshot依存のstate検証は現行どおりworkspace cleanup対象のcatch境界内に置く。version比較、replay保存、Docker書込み、書込み後検証の順序も変えない。

直接unit testでは、全`CommandKind`のargv要素と順序、snapshotのexit code／truncation／state file／stash異常、editorの2,048／2,049 UTF-8 byte、複数byte文字、CRLF／単独CR、control character、SHA-256 token、例外型・message・causeを固定する。`RunnerStagePolicyTest`では、全production stage／trainingの許可・拒否`CommandKind`、branch名、object target、不足または不整合のあるtyped observation、初期fixtureの正常系と代表的な近似不正、未知stageのfail-closedを表形式で固定する。policy testはDocker、snapshot reader、workspace mapへ依存させない。テスト専用public APIやSpring wiringは追加しない。

### クラスタ5: 全体統合

1. 全137 Java fileを再点検する。
2. 残る重複、命名、不要compatibility constructorを根拠付きで判断する。
3. testのfixtureとhelperを、意図が見えにくい箇所だけ整理する。
4. 読解ガイドとarchitectureを実装後の構造へ更新する。
5. unit、DB、Docker integration、主要画面を最終確認する。

クラスタ5では、追跡対象137 Java fileを再点検し、`TODO`、`FIXME`、`HACK`、`@Deprecated`が残っていないことを確認した。公開constructor／overloadは実call siteまたはcontract上の用途があるため削除しなかった。追加の責務分割は行わず、`StageService.Attempt`、`MemoryStagePersistence`、`MemoryContainerOwnershipLedger`の状態と処理順を読みやすく整え、`RunnerWorkspaceServiceIdempotencyTest`の既定準備だけをhelperへまとめた。wire contract、DB、route、Spring wiring、教材、fixture、error message、同期方式、lifecycleは変更していない。

memory persistenceにはrequest登録がversion競合より先に行われる契約、memory ledgerにはcopy返却と削除の契約を直接testで追加した。井上の実装前レビューと、P2修正後の実装後再レビューはいずれも`PASS`である。

## 8. テストgate

### 8.1 クラスタ1で取得した基準

| 日時 | command | JDK／Maven | 結果 | 範囲 |
|---|---|---|---|---|
| 2026-08-23 | `.\scripts\invoke-maven.ps1 test` | Temurin 25／Maven Wrapper 3.9.16、sandbox外 | 失敗 | Runner unit 36件中4件失敗。appはfail-fastにより未実行 |
| 2026-08-23 | `.\scripts\invoke-maven.ps1 -pl app -am test` | Temurin 25／Maven Wrapper 3.9.16、sandbox外 | 失敗 | app 91件中4件失敗。Java問題集compile／Main実行8件は成功 |
| 2026-08-23 | `.\scripts\invoke-maven.ps1 test` | Temurin 25／Maven Wrapper 3.9.16、sandbox外 | 成功 | 基準線修復後。Runner 36件、app 91件が成功 |
| 2026-08-23 | `.\scripts\invoke-maven.ps1 test` | Temurin 25／Maven Wrapper 3.9.16、sandbox外 | 成功 | クラスタ2変更後。Runner 36件、app 95件が成功 |
| 2026-08-23 | StageRules対象限定test | Temurin 25／Maven Wrapper 3.9.16、sandbox外 | 成功 | クラスタ3。catalog 21件、command policy 22件、state policy 47件が成功 |
| 2026-08-23 | root通常testをメイン・中谷で分担 | Temurin 25／Maven Wrapper 3.9.16、sandbox外 | 成功 | メインは`*Test,!StageControllerTest`でRunner 36件・app 94件、中谷は`StageControllerTest` 6件。合計Runner 36件・app 100件 |
| 2026-08-23 | Git Runner変更前Docker IT（class単位） | Temurin 25／Maven Wrapper 3.9.16、Docker Desktop 29.5.3 | 成功 | Alternative 7件、Security 2件、Stage 2 1件、Stage 3 1件、Stage 4 2件、Stage 5 3件、Training 3件。合計19件。終了後のRunner管理containerは0件 |
| 2026-08-24 | Git Runner変更後対象unit test | Temurin 25／Maven Wrapper 3.9.16、sandbox外 | 成功 | ledger 7件、command validator 3件、stage policy 5件、token filter 2件、workspace／idempotency 24件。合計41件 |
| 2026-08-24 | Git Runner変更後Docker IT（class単位） | Temurin 25／Maven Wrapper 3.9.16、Docker Desktop 29.5.3 | 成功 | Alternative 7件、Security 2件、Stage 2 1件、Stage 3 1件、Stage 4 2件、Stage 5 3件、Training 3件。合計19件。終了後のRunner管理containerは0件 |
| 2026-08-24 | クラスタ5全体統合後root通常test | Temurin 25／Maven Wrapper 3.9.16、sandbox外 | 成功 | Runner 60件、app 104件。合計164件 |
| 2026-08-24 | クラスタ5全体統合後DB IT | Temurin 25／Maven Wrapper 3.9.16、Docker Desktop 29.5.3 | 成功 | `JdbcJavaProgressRepositoryIT` 1件、`JdbcStagePersistenceIT` 2件。合計3件 |
| 2026-08-24 | クラスタ5全体統合後Docker IT | Temurin 25／Maven Wrapper 3.9.16、Docker Desktop 29.5.3 | 成功 | Alternative、Security、Stage 2〜5、Trainingの合計19件。終了後のRunner管理containerは0件 |
| 2026-08-24 | クラスタ5主要画面確認 | Temurin 25、Docker Desktop 29.5.3 | 成功 | タイトル、Git一覧、Stage 1、command一覧、Java問題一覧、Java問題詳細を実Browserで確認。HTTP 500とconsole errorなし |

クラスタ1の基準取得時に失敗していたtestは次のとおりである。

- `RunnerWorkspaceServiceIdempotencyTest`: 4件。productionが実行する固定`git cat-file -t`をmock Docker応答が扱わない。
- `StageFiveTemplateTest`: 1件。現在templateと旧表示期待の不一致。
- `StageGuidanceTest`: 1件。現在のguidance表示と旧期待の不一致。
- `StagePresentationTemplateTest`: 2件。現在templateと旧concept chip期待の不一致。

この失敗はクラスタ1の文書変更によるものではなかった。現仕様とproductionの固定commandを根拠にtest fixtureと古い表示期待だけを独立commit `d7b4707`で修正し、正式なroot `test`をgreenへ戻してからクラスタ2へ着手した。

通常のMaven `test`と現在のFast CIの`verify`は`*IT`を実行しない。DB IT 3件とDocker IT 19件は上表のとおり別commandで実行済みであり、DB ITはPersistence CIでも明示実行する。

### 8.2 各クラスタの着手gate

| 対象 | 変更前に成功必須 | 追加許可 |
|---|---|---|
| Java問題集 | Java問題集unit／template／reference compile・Main実行 | `JdbcJavaProgressRepositoryIT`はDB／Docker許可後 |
| Git app stage／lifecycle | Stage 1〜5、training、input rejection、request idempotency unit | `JdbcStagePersistenceIT`はDB／Docker許可後 |
| persistence | 対応unitと`JdbcStagePersistenceIT` | Docker Desktopが必要 |
| Git Runner | validator、token、ledger、idempotency unit | 関連stage Docker ITと`RunnerSecurityDockerIT`にDocker Desktopが必要 |

各rollback単位で、変更前に成功したtestと同じtestを変更後にも実行する。クラスタ1で判明したunit／template test 8件は独立commitで原因を解消し、正式なroot `test`が成功したためクラスタ2の着手gateを満たした。この条件は今後のクラスタでもリスク受容によって免除しない。

Docker／DBのようにユーザー許可と外部環境が必要なbaselineは、関連subsystemを変更するクラスタの着手gateとする。実行しない場合は未検証のまま変更せず、そのsubsystemのrollback単位を延期する。security／persistence baselineもリスク受容を理由に迂回しない。

## 9. レビュー・Git・完了条件

各非軽微なrollback単位は、メインの方針、井上の実装前レビュー、実装、メインの最小確認、井上の実装後レビュー、対象限定test、最終差分確認の順で進める。

- 1つの差分へpackage移動とlogic変更を混ぜない。
- app巨大classとRunner巨大classを同じ差分で変更しない。
- 新しい本番依存、DB migration、route変更、contract変更を混ぜない。
- 既存testの期待を変更する場合、現在仕様を正とする根拠を差分へ残す。
- 各小単位は単独でrevertでき、revert後もcompileする。

クラスタ1は、この計画、読解ガイド、基準test結果、既知の開始ブロッカーへ井上レビューを反映したことをもって完了する。コードリファクタリングは、既知の赤いtestを別小差分でgreenにした後、クラスタ2から開始する。

クラスタ2は、基準線をgreenへ戻した独立commit、Java問題contentのI/Oと検証の責務分割、直接的な安全制約test、井上の実装前後レビュー、変更後のroot `test`成功をもって完了する。次のクラスタ3ではGit appを対象とし、Java問題集の責務分割を追加で広げない。

クラスタ3は、`StageRules`のcharacterization、3責務の独立抽出、井上の実装前後レビュー`PASS`、対象限定test、変更後の分担root test成功をもって完了した。次のクラスタ4ではGit Runnerを対象とし、appの責務分割を追加で広げない。

クラスタ4は、Git argv、snapshot、stage policy、editor policyの4つをpackage-privateなstateless classへ独立抽出し、`RunnerWorkspaceService`の単一状態所有、security boundary、wire contract、fixtureを維持した。井上の実装前後レビューは`PASS`で、変更後対象unit test 41件とDocker IT 19件が成功し、Runner管理containerが0件であることを確認して完了した。次のクラスタ5では全体統合だけを扱う。

クラスタ5は、全Java fileの再点検、残存constructor／overloadの根拠確認、状態を持つmemory実装とtest fixtureの限定的な可読性改善、恒久的なJava実装規則の文書化を行った。井上の実装前後レビューは`PASS`で、対象限定test、root通常test 164件、DB IT 3件、Docker IT 19件、主要6画面が成功し、全5クラスタを完了した。
