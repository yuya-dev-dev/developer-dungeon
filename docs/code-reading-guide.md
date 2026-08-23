# Developer Dungeon Javaコード読解ガイド

## 文書情報

- 状態: リファクタリング前の現在構造を説明する初版
- 対象: `009f53d`時点のJava本体、test、Java教材reference source
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

### 2.3 `StageRules`

8件の`StageDefinition`、入力parse／normalize、hint、fixture target capture、snapshot採点を持つ。長い理由は、教材contentとpolicyが同じclassへ集まっているためである。

最初は次の3つを区別して読む。

1. `StageDefinition`: プレイヤーへ見せる教材と目標。
2. parse／normalize: Browserの文字列を許可済み`GitCommand`へ変換する境界。
3. grade: command履歴ではなく最終`RepositorySnapshot`を判定する処理。

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
     -> JavaProblemCatalog -> problem.json / reference source
     -> JavaProgressRepository -> PostgreSQL
  -> Java問題集template
```

### 3.1 `JavaLearningController`

`/java`、問題一覧、問題詳細、進捗更新を扱う。利用者codeを受け取らず、実行も採点もしない。

### 3.2 `JavaLearningService`

固定catalogと自己申告進捗を組み合わせ、一覧・詳細用の値を返す。学習問題の内容を変更しない。

### 3.3 `JavaProblemCatalog`

起動時に9問を全件読む。安全なpath、file名、package、公開type、`Main.main`、3テーマ×3難易度、初級scaffoldを検証する。現在は読込と検証が同じconstructorにあるため、クラスタ2の主要候補である。

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
