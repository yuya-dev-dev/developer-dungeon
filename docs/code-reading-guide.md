# Developer Dungeon Javaコード読解ガイド

## 文書情報

- 状態: クラスタ3のGit app責務分割まで反映
- 対象: `009f53d`を調査基準とし、クラスタ2・3で整理したJava本体とtestを反映
- 目的: Javaを学び始めた開発者が、処理を読む順番とstateの所有者を把握できるようにする
- 計画: [`java-refactoring-plan.md`](java-refactoring-plan.md)

## 1. 最初に押さえる4 module

| module | 役割 | 最初に読むfile |
|---|---|---|
| `runner-contract` | appとGit Runnerが交換する型 | `GitCommand.java`、`RepositorySnapshot.java` |
| `app` | Browser、Git学習、Java問題集、管理DB | `DeveloperDungeonApplication.java` |
| `git-runner` | Git入力の再検証、Docker、使い捨てworkspace | `GitRunnerApplication.java` |
| `db-migrator` | Flyway migrationだけを適用する独立入口 | `DatabaseMigrator.java` |

依存方向は`app -> runner-contract <- git-runner`である。appから`RunnerWorkspaceService`を直接呼ばず、HTTPとcontractを境界にする。

## 2. Git課題を開いてからcommand結果が返るまで

現在の実fileを次の順で読む。

```text
Browser
  -> StageController
  -> StageService
       +-> StageRules
            +-> StageCatalog
            +-> StageCommandPolicy
            +-> StageStatePolicy
       +-> StagePersistence
       +-> RunnerClient
            -> RunnerController
            -> RunnerWorkspaceService
                 +-> RunnerCommandValidator
                 +-> DockerGateway
                      -> challenge container内のGit

RunnerのRepositorySnapshot
  -> StageService
       +-> StageRulesで採点
       +-> StagePersistenceへ結果を永続化
       +-> clear時はRunner cleanupを調整
  -> StageView
  -> Thymeleaf template
```

`StageRules`、`StagePersistence`、`RunnerClient`が互いを順番に呼ぶのではない。`StageService`が3つをそれぞれ呼び、処理順とstateを調整するcoordinatorである。

### 2.1 `StageController`

`GET`、command、hint、reset、Stage 4 editorのrouteを受け、`StageService`の結果をThymeleaf modelへ入れる。GitやDBの処理はここで行わない。

### 2.2 `StageService`

Git編の中心である。現在は次を1つのclassが持つ。

- stageごとのactive attempt。
- request再送結果。
- sequence、generation、optimistic version。
- Runner呼出し前後のDB transition。
- clear時のworkspace cleanup。
- reset、system recovery、startup recovery。
- hintと表示済みobject ID。

読むときは`open`、`execute`、`performOperation`、`reset`、`createWorkspace`、`recoverSaved`の順が分かりやすい。`synchronized`は単なる飾りではなく、同じattemptの状態と外部操作を直列化する安全境界である。

### 2.3 `StageRules`と3つの方針class

`StageRules`は`StageService`から見える互換窓口だけを残した。次の順に読むと、教材、入力境界、最終状態判定を混同せず追える。

1. `StageCatalog`: 8件の`StageDefinition`と一覧順序を持つ。
2. `StageCommandPolicy`: Browserの文字列を許可済み`GitCommand`へ変換し、表示済みobject IDと照合して正規化する。
3. `StageStatePolicy`: fixture targetを検証して取得し、hintを組み立て、command履歴ではなく最終`RepositorySnapshot`を採点する。
4. `StageRules`: 上記3責務へ委譲し、既存callerとtestが利用する`StageTargets`を保持する。

`StageService`はattempt lifecycle、DB遷移、Runner呼出し、cleanupを直列化するstate所有者であるため、クラスタ3では分割していない。

### 2.4 `StagePersistence`

interfaceが許可する状態遷移を先に読み、次に`JdbcStagePersistence`を見る。SQLを1文ずつ読む前に、`STARTING -> ACTIVE -> EXECUTING`、clear、reset、cleanup pendingという用途を把握する。

`MemoryStagePersistence`は主にunit test用であり、JDBC実装と意味がずれないことが重要である。

### 2.5 `RunnerClient`から`RunnerController`

`RunnerClient`はtoken付きHTTP requestへ変換し、`RunnerController`はtyped requestを`RunnerWorkspaceService`へ渡す。Browserのraw commandをRunnerへ渡さない。

### 2.6 `RunnerWorkspaceService`

Git Runnerの中心で、約1,000行ある。読む順番は次のとおりとする。

1. Controllerから呼ばれる`create`、`execute`、`readFile`、`writeFile`、`snapshotFor`、`destroy`。
2. `validateStageCommand`と`validateAllowedObject`。
3. `snapshotFor`から呼ばれるprivateな`snapshot`とGit出力の読取。
4. workspace／generation検証。
5. create失敗、TTL、orphan、shutdown cleanup。

このclassのmaps、ledger、cleanup状態は同じmonitorで守られている。後続の分割でもstateの所有者は1つに保つ。

### 2.7 `DockerGateway`

Docker CLI processを起動する最下層である。任意shellではなく固定argument列を使い、親processの環境をそのまま渡さない。このclassより上でplayer入力をtyped valueへ狭める。

## 3. Java問題集の読み順

```text
Browser
  -> JavaLearningController
  -> JavaLearningService
     -> JavaProblemCatalog
        -> JavaProblemContentLoader -> catalog / problem.json / reference source
        -> JavaProblemCatalogValidator -> path / size / content / 3x3 matrix
     -> JavaProgressRepository -> PostgreSQL
  -> Java問題集template
```

### 3.1 `JavaLearningController`

`/java`、問題一覧、問題詳細、進捗更新を扱う。利用者codeを受け取らず、実行も採点もしない。

### 3.2 `JavaLearningService`

固定catalogと自己申告進捗を組み合わせ、一覧・詳細用の値を返す。学習問題の内容を変更しない。

### 3.3 `JavaProblemCatalog`

起動時に9問を全件読み、読み込みと検証の順序を調整して、slugから問題を引く不変indexを構築する。classpath I/Oは`JavaProblemContentLoader`、安全なpath、file名、容量、package、公開type、`Main.main`、3テーマ×3難易度、初級scaffoldの規則はstatelessな`JavaProblemCatalogValidator`が担当する。公開constructorと取得API、問題data、例外messageは分割前から変更していない。

### 3.4 `JavaProblem`とreference source

`JavaProblem`はJSONから読む問題dataである。`app/src/main/resources/java-problems/**/reference/*.java`は模範codeとして画面に表示され、test時に問題ごとにcompile・実行される。application本体のclassではない。

## 4. 起動とsecurityの読み順

### app

1. `DeveloperDungeonApplication`
2. `AppConfiguration`
3. `SecurityConfiguration`
4. `LoopbackRequestFilter`
5. `AppInternalController`

### Runner

1. `GitRunnerApplication`
2. `RunnerConfiguration`
3. `RunnerProperties`
4. `RunnerTokenFilter`
5. `RunnerController`

token、loopback、shutdownは通常のplayer routeとは別の境界である。読みやすさのために統合してはいけない。

## 5. testを読む順番

| 知りたいこと | 最初に読むtest |
|---|---|
| Git入力の許可と拒否 | `StageInputRejectionTest`、`RunnerCommandValidatorTest` |
| Stage 1〜5の採点 | 各`Stage*ServiceTest` |
| alternative solution | `RunnerAlternativeSolutionsDockerIT` |
| request再送とcleanup | `RunnerWorkspaceServiceIdempotencyTest` |
| Runner隔離 | `RunnerSecurityDockerIT` |
| DB状態遷移 | `JdbcStagePersistenceIT` |
| Java問題9問と模範code | `JavaProblemCatalogTest` |
| Java問題contentの安全制約と例外境界 | `JavaProblemCatalogValidatorTest` |
| Java進捗DB | `JdbcJavaProgressRepositoryIT` |
| routeと画面model | `StageControllerTest`、`JavaLearningControllerTest` |

`*Test`は通常のMaven testで実行される。`*IT`は通常testに含まれず、Docker DesktopやPostgreSQL Testcontainersが必要である。

## 6. 現在構造と目標構造を混同しない

現在、Git appのclassは主に`jp.yuya.dev.developerdungeon.app`直下、Runnerは主に`jp.yuya.dev.developerdungeon.runner`直下にある。`javalearning`だけは既にlayer別packageを持つ。

後続クラスタでは次の目標へ段階的に移すが、まだ実装済みではない。

```text
app.git.web / application / domain / stage / runner / persistence
runner.api / config / validation / lifecycle / git / snapshot / sandbox / cleanup
```

package移動とlogic分割は別々に行う。このガイドは各クラスタ完了時に「現在構造」の該当節だけ更新する。

## 7. 初心者が迷ったときの見方

- Controllerでは「どのURLが、どのservice methodを呼ぶか」だけを見る。
- Serviceでは「入力、state変更、外部呼出し、返り値」の順で追う。
- recordは値のまとまりとして読み、生成場所を探す。
- interfaceは実装を読む前に「利用側が何を要求しているか」を確認する。
- exception処理では正常系へ戻らず、どのstateを永続化して何をcleanupするかを見る。
- security関連の重複は、appとRunnerで二重検証する意図がある可能性を先に疑う。
- 短いcodeへ直すことより、stateの所有者と処理順を明示することを優先する。
